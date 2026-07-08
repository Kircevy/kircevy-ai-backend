package com.wgz.aikir.langgraph4j.node;

import com.wgz.aikir.constant.AppConstant;
import com.wgz.aikir.core.builder.VueProjectBuilder;
import com.wgz.aikir.core.builder.FullStackProjectBuilder;
import com.wgz.aikir.exception.BusinessException;
import com.wgz.aikir.exception.ErrorCode;
import com.wgz.aikir.langgraph4j.state.WorkflowContext;
import com.wgz.aikir.model.enums.CodeGenTypeEnum;
import com.wgz.aikir.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import reactor.core.publisher.Flux;

import javax.swing.*;

import java.io.File;
import java.time.Duration;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
@Slf4j
public class ProjectBuilderNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 项目构建");

            // 获取必要的参数
            String generatedCodeDir = context.getGeneratedCodeDir();
            CodeGenTypeEnum generationType = context.getGenerationType();
            String buildResultDir = generatedCodeDir;

            switch (generationType) {
                case VUE_PROJECT -> {
                    // Vue 项目类型：使用 VueProjectBuilder 进行构建（npm install + npm run build）
                    try {
                        VueProjectBuilder vueBuilder = SpringContextUtil.getBean(VueProjectBuilder.class);
                        boolean buildSuccess = vueBuilder.buildProject(generatedCodeDir);
                        if (buildSuccess) {
                            buildResultDir = generatedCodeDir + File.separator + "dist";
                            log.info("Vue 项目构建成功，dist 目录: {}", buildResultDir);
                        } else {
                            log.warn("Vue 项目构建失败，返回原路径: {}", generatedCodeDir);
                        }
                    } catch (Exception e) {
                        log.error("Vue 项目构建异常: {}", e.getMessage(), e);
                    }
                }
                case FULLSTACK -> {
                    // 全栈项目类型：使用 FullStackProjectBuilder 进行构建（校验结构 + npm install）
                    try {
                        FullStackProjectBuilder fullStackBuilder = SpringContextUtil.getBean(FullStackProjectBuilder.class);
                        boolean buildSuccess = fullStackBuilder.buildProject(generatedCodeDir);
                        if (buildSuccess) {
                            log.info("全栈项目构建成功，目录: {}", generatedCodeDir);
                        } else {
                            log.warn("全栈项目构建失败，返回原路径: {}", generatedCodeDir);
                        }
                    } catch (Exception e) {
                        log.error("全栈项目构建异常: {}", e.getMessage(), e);
                    }
                }
                case SPRINGBOOT -> {
                    // Spring Boot 项目类型：不需要自动构建，由 MavenTool 在生成过程中按需调用
                    log.info("Spring Boot 项目生成完成，路径: {}", generatedCodeDir);
                }
                default -> {
                    // HTML、MULTI_FILE 等类型不需要构建
                    log.info("{} 类型不需要项目构建，路径: {}", generationType.getValue(), generatedCodeDir);
                }
            }
            // 更新状态
            context.setCurrentStep("项目构建");
            context.setBuildResultDir(buildResultDir);
            log.info("项目构建节点完成，最终目录: {}", buildResultDir);
            return WorkflowContext.saveContext(context);
        });
    }
}

