package com.wgz.aikir.core;

import cn.hutool.json.JSONUtil;
import com.wgz.aikir.ai.AiCodeGeneratorService;
import com.wgz.aikir.ai.AiCodeGeneratorServiceFactory;
import com.wgz.aikir.ai.model.HtmlCodeResult;
import com.wgz.aikir.ai.model.MultiFileCodeResult;
import com.wgz.aikir.ai.model.message.AiResponseMessage;
import com.wgz.aikir.ai.streaming.ThinkingDisplayAdapter;
import com.wgz.aikir.ai.streaming.TokenStreamFluxAdapter;
import com.wgz.aikir.ai.streaming.ToolCallDisplayAdapter;
import com.wgz.aikir.constant.AppConstant;
import com.wgz.aikir.core.builder.VueProjectBuilder;
import com.wgz.aikir.core.builder.FullStackProjectBuilder;
import com.wgz.aikir.core.parser.CodeParserExecutor;
import com.wgz.aikir.core.saver.CodeFileSaverExecutor;
import com.wgz.aikir.exception.BusinessException;
import com.wgz.aikir.exception.ErrorCode;
import com.wgz.aikir.model.enums.CodeGenTypeEnum;
import com.wgz.aikir.service.FrontendPreviewBuildService;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 代码生成门面类，组合代码生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    private final TokenStreamFluxAdapter tokenStreamFluxAdapter = new TokenStreamFluxAdapter();

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private FullStackProjectBuilder fullStackProjectBuilder;

    @Resource
    private FrontendPreviewBuildService frontendPreviewBuildService;

    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId 应用 ID
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成类型不能为空");
        }
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode( userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            case VUE_PROJECT, SPRINGBOOT, FULLSTACK -> {
                // 复杂类型通过工具调用写文件，阻塞等待 TokenStream 完成后返回项目目录
                TokenStream tokenStream = switch (codeGenTypeEnum) {
                    case VUE_PROJECT -> aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                    case SPRINGBOOT -> aiCodeGeneratorService.generateSpringBootCodeStream(appId, userMessage);
                    case FULLSTACK -> aiCodeGeneratorService.generateFullStackCodeStream(appId, userMessage);
                    default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的类型");
                };
                java.util.concurrent.CompletableFuture<Void> future = new java.util.concurrent.CompletableFuture<>();
                tokenStream.onCompleteResponse(r -> future.complete(null))
                           .onError(future::completeExceptionally)
                           .start();
                future.join();
                String projectDirName = codeGenTypeEnum.getValue() + "_" + appId;
                yield new File(AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + projectDirName);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId 应用 ID
     * @return 保存的目录
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成类型不能为空");
        }
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = tokenStreamFluxAdapter.toFlux(
                        aiCodeGeneratorService.generateHtmlCodeStream(userMessage));
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = tokenStreamFluxAdapter.toFlux(
                        aiCodeGeneratorService.generateMultiFileCodeStream(userMessage));
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            case VUE_PROJECT -> {
                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                yield processTokenStream(tokenStream, appId, codeGenTypeEnum);
            }
            case SPRINGBOOT -> {
                TokenStream tokenStream = aiCodeGeneratorService.generateSpringBootCodeStream(appId, userMessage);
                yield processTokenStream(tokenStream, appId, codeGenTypeEnum);
            }
            case FULLSTACK -> {
                TokenStream tokenStream = aiCodeGeneratorService.generateFullStackCodeStream(appId, userMessage);
                yield processTokenStream(tokenStream, appId, codeGenTypeEnum);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 通用流式代码处理方法
     *
     * @param codeStream  代码流
     * @param codeGenType 代码生成类型
     * @param appId 应用 ID
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId) {
        // 字符串拼接器，用于当流式返回所有的代码之后，再保存代码
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream.doOnNext(chunk -> {
            // 实时收集代码片段
            codeBuilder.append(chunk);
        }).doOnComplete(() -> {
            // 流式返回完成后，保存代码
            try {
                String completeCode = codeBuilder.toString();
                // 使用执行器解析代码
                Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                // 使用执行器保存代码
                File saveDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                log.info("保存成功，目录为：{}", saveDir.getAbsolutePath());
                if (codeGenType == CodeGenTypeEnum.HTML) {
                    frontendPreviewBuildService.markStaticPreviewReady(appId);
                }
            } catch (Exception e) {
                log.error("保存失败: {}", e.getMessage());
            }
        });
    }



   /**
     * 将TokenStream流转换为Flux<String>流
     * @param tokenStream token 流
     * @param codeGenTypeEnum 代码生成类型
     * @return 流式响应
     */
   private Flux<String> processTokenStream(TokenStream tokenStream, Long appId, CodeGenTypeEnum codeGenTypeEnum){
        return Flux.create(sink -> {
            ToolCallDisplayAdapter toolCallDisplayAdapter = new ToolCallDisplayAdapter(sink::next);
            ThinkingDisplayAdapter thinkingDisplayAdapter = new ThinkingDisplayAdapter(sink::next);
            AtomicBoolean hasExecutedTool = new AtomicBoolean(false);
            AtomicBoolean terminalHandled = new AtomicBoolean(false);
            Runnable completeGeneration = () -> {
                if (!terminalHandled.compareAndSet(false, true)) {
                    return;
                }
                completeProjectGeneration(sink, appId, codeGenTypeEnum);
            };
            tokenStream.onPartialResponse((String partialResponse) -> {
                AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                sink.next(JSONUtil.toJsonStr(aiResponseMessage));
            }).onPartialThinking(partialThinking -> thinkingDisplayAdapter.onPartialThinking(partialThinking.text())).beforeToolExecution(beforeToolExecution -> {
                toolCallDisplayAdapter.beforeToolExecution(beforeToolExecution.request());
            }).onToolExecuted(toolExecution -> {
                hasExecutedTool.set(true);
                toolCallDisplayAdapter.onToolExecuted(toolExecution);
            }).onCompleteResponse((ChatResponse response) -> {
                completeGeneration.run();
            }).onError((Throwable error) -> {
                if (hasExecutedTool.get() && isTerminalEmptyStream(error)) {
                    log.warn("工具型代码生成已执行文件操作，模型以空流结束，按正常收尾处理，appId：{}", appId);
                    completeGeneration.run();
                    return;
                }
                log.error("代码生成流失败，appId：{}", appId, error);
                sink.error(error);
            }).start();
        });
    }

    /**
     * 完成工具型工程生成后的构建校验。该方法只允许由同一 TokenStream 的终止路径调用一次。
     */
    private void completeProjectGeneration(reactor.core.publisher.FluxSink<String> sink, Long appId,
                                           CodeGenTypeEnum codeGenTypeEnum) {
        String projectDirName = codeGenTypeEnum.getValue() + "_" + appId;
        String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + projectDirName;
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            boolean buildSuccess = vueProjectBuilder.buildProject(projectPath);
            if (!buildSuccess) {
                log.error("Vue 项目构建失败，路径: {}", projectPath);
                sink.next(JSONUtil.toJsonStr(new AiResponseMessage("\n\n[构建失败] Vue 项目构建失败，请检查生成的代码")));
            }
        } else if (codeGenTypeEnum == CodeGenTypeEnum.SPRINGBOOT) {
            log.info("Spring Boot 项目生成完成，项目路径: {}", projectPath);
        } else if (codeGenTypeEnum == CodeGenTypeEnum.FULLSTACK) {
            boolean buildSuccess = fullStackProjectBuilder.buildProject(projectPath);
            if (!buildSuccess) {
                log.error("全栈项目构建失败，路径: {}", projectPath);
                sink.next(JSONUtil.toJsonStr(new AiResponseMessage("\n\n[构建失败] 全栈项目构建失败，请检查生成的代码")));
            }
        }
        sink.complete();
    }

    /**
     * 部分推理模型在工具循环完成后不输出最终文本，直接关闭最后一轮 SSE。
     */
    private boolean isTerminalEmptyStream(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("模型服务以空 SSE 流结束")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
