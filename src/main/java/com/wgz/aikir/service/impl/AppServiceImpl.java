package com.wgz.aikir.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.wgz.aikir.constant.AppConstant;
import com.wgz.aikir.core.AiCodeGeneratorFacade;
import com.wgz.aikir.core.builder.VueProjectBuilder;
import com.wgz.aikir.core.builder.FullStackProjectBuilder;
import com.wgz.aikir.core.handler.StreamHandlerExecutor;
import com.wgz.aikir.exception.BusinessException;
import com.wgz.aikir.exception.ErrorCode;
import com.wgz.aikir.exception.ThrowUtils;
import com.wgz.aikir.model.dto.app.AppQueryRequest;
import com.wgz.aikir.model.entity.App;
import com.wgz.aikir.mapper.AppMapper;
import com.wgz.aikir.model.entity.User;
import com.wgz.aikir.model.enums.ChatHistoryMessageTypeEnum;
import com.wgz.aikir.model.enums.CodeGenTypeEnum;
import com.wgz.aikir.model.enums.DeployModeEnum;
import com.wgz.aikir.model.vo.AppVO;
import com.wgz.aikir.model.vo.AppDeploymentVO;
import com.wgz.aikir.model.vo.CodeFileTreeNode;
import com.wgz.aikir.model.vo.UserVO;
import com.wgz.aikir.monitor.MonitorContext;
import com.wgz.aikir.monitor.MonitorContextHolder;
import com.wgz.aikir.service.AppService;
import com.wgz.aikir.service.ChatHistoryService;
import com.wgz.aikir.service.CodeGenerationTaskService;
import com.wgz.aikir.service.DockerComposeDeployService;
import com.wgz.aikir.service.FrontendPreviewBuildService;
import com.wgz.aikir.multiagent.service.GenerationRunService;
import com.wgz.aikir.multiagent.service.PlanningAgentService;
import com.wgz.aikir.multiagent.config.MultiAgentProperties;
import com.wgz.aikir.multiagent.strategy.GenerationStrategyPolicy;
import com.wgz.aikir.service.ScreenShotService;
import com.wgz.aikir.service.UserService;
import com.wgz.aikir.service.UserNotificationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author <a href="https://gitee.com/jky_3477_0">WGZ</a>
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    private static final Set<String> CODE_TREE_IGNORED_NAMES = Set.of(
            "node_modules", ".git", "dist", "build", "target", ".idea", ".vscode", ".mvn", "coverage"
    );

    @Resource
    private UserService userService;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    @Lazy
    private ChatHistoryService chatHistoryService;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private FullStackProjectBuilder fullStackProjectBuilder;

    @Resource
    private DockerComposeDeployService dockerComposeDeployService;

    @Resource
    private UserNotificationService userNotificationService;

    @Resource
    private ScreenShotService screenShotService;

    @Resource
    private CodeGenerationTaskService codeGenerationTaskService;

    @Resource
    private FrontendPreviewBuildService frontendPreviewBuildService;

    @Resource
    private GenerationRunService generationRunService;

    @Resource
    private PlanningAgentService planningAgentService;

    @Resource
    private MultiAgentProperties multiAgentProperties;

    @Resource
    private GenerationStrategyPolicy generationStrategyPolicy;


    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "提示词不能为空");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验，仅本人可以和自己的应用对话
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        // 4. 获取应用的代码生成类型
        ThrowUtils.throwIf(codeGenerationTaskService.isRunning(appId), ErrorCode.OPERATION_ERROR,
                "该应用正在生成代码，请等待当前任务完成");
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用代码生成类型错误");
        }
        // 5. 在调用 AI 前，先保存用户消息到数据库中
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        // 6. 设置监控上下文（埋点）
        MonitorContextHolder.setContextHolder(new MonitorContext(loginUser.getId().toString(), appId.toString()));
        if (generationStrategyPolicy.isMultiAgentSelected(app)) {
            ThrowUtils.throwIf(!generationStrategyPolicy.shouldStartMultiAgent(app, multiAgentProperties),
                    ErrorCode.NO_AUTH_ERROR, "协作生成功能当前未启用，请重新选择快速生成");
            var run = planningAgentService.createPlanningRun(app, loginUser, message, true);
            String response = "已启动多 Agent 协作任务（运行 ID：" + run.getRunId()
                    + "）。系统会先完成 M1 规划，再自动并行生成前端和后端；请在右侧“协作过程”查看实时进度。";
            chatHistoryService.addChatMessage(appId, response, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
            return codeGenerationTaskService.start(appId, Flux.just(response)
                    .doFinally(signalType -> MonitorContextHolder.clearContext()));
        }
        // 7. 调用 AI 生成代码（流式）
        if (codeGenTypeEnum == CodeGenTypeEnum.FULLSTACK
                || codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT
                || codeGenTypeEnum == CodeGenTypeEnum.HTML) {
            frontendPreviewBuildService.markGenerationStarted(appId, codeGenTypeEnum);
        }
        var generationRun = generationRunService.startDirectRunIfEnabled(app, loginUser, codeGenTypeEnum, message);
        String runId = generationRun == null ? null : generationRun.getRunId();
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
        // 8. 收集 AI 响应的内容，并且在完成后保存记录到对话历史
        Flux<String> handledStream = streamHandlerExecutor.doExecute(codeStream, chatHistoryService, appId, loginUser, codeGenTypeEnum)
                .doOnComplete(() -> generationRunService.completeDirectRun(runId))
                .doOnError(error -> generationRunService.failDirectRun(runId, error))
                .doFinally(s -> {
                            // 8. 清理监控上下文
                            MonitorContextHolder.clearContext();
                          });
        return codeGenerationTaskService.start(appId, handledStream);
    }

    @Override
    public boolean isCodeGenerationRunning(Long appId, User loginUser) {
        validateAppOwner(appId, loginUser);
        return codeGenerationTaskService.isRunning(appId);
    }

    @Override
    public Flux<String> subscribeCodeGeneration(Long appId, User loginUser) {
        validateAppOwner(appId, loginUser);
        return codeGenerationTaskService.subscribe(appId);
    }

    private void validateAppOwner(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "无权访问该应用");
    }

    @Override
    public String deployApp(Long appId, User loginUser) {
        // 兼容旧接口，默认静态部署模式
        return deployApp(appId, DeployModeEnum.CODE_DOWNLOAD, loginUser);
    }

    @Override
    public String deployApp(Long appId, DeployModeEnum deployMode, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        ThrowUtils.throwIf(deployMode == null, ErrorCode.PARAMS_ERROR, "部署模式不能为空");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验，仅本人可以部署自己的应用
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        // 4. 已部署的应用不允许重复部署
        if (app.getDeployedTime() != null) {
            if (StrUtil.isNotBlank(app.getDockerDeployUrl())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR,
                        "应用已通过 Docker 一键部署，请勿重复部署，请前往我的部署页面");
            }
            if (StrUtil.isNotBlank(app.getDeployKey())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用已部署，请勿重复部署");
            }
        }
        // 5. 检查是否已有 deployKey
        String deployKey = app.getDeployKey();
        // 如果没有，则生成 6 位 deployKey（字母 + 数字）
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 6. 获取代码生成类型，获取原始代码生成路径（应用访问目录）
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 7. 检查路径是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码路径不存在，请先生成应用");
        }
        // 8. 按部署模式分发
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (deployMode == DeployModeEnum.DOCKER_COMPOSE) {
            String composeProjectName = "fullstack_" + appId;
            return deployWithDockerCompose(appId, deployKey, composeProjectName, sourceDirPath, codeGenTypeEnum, loginUser);
        }
        // 默认静态部署模式：构建并部署前端静态资源（前端预览）
        return deployWithCodeDownload(appId, deployKey, sourceDir, sourceDirPath, codeGenType);
    }

    /**
     * 静态部署模式：构建前端静态资源并部署到预览目录，同时提供完整源码下载
     */
    private String deployWithCodeDownload(Long appId, String deployKey, File sourceDir,
                                          String sourceDirPath, String codeGenType) {
        // 根据项目类型进行构建部署
        if (codeGenType.equals(CodeGenTypeEnum.VUE_PROJECT.getValue())) {
            // Vue 项目：构建并部署前端 dist
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "应用构建失败");
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists() || !distDir.isDirectory(), ErrorCode.SYSTEM_ERROR, "应用构建成功，但dist目录不存在");
            sourceDir = distDir;
        } else if (codeGenType.equals(CodeGenTypeEnum.FULLSTACK.getValue())) {
            // 全栈项目：构建前端并部署前端 dist（静态部署模式下，后端源码随 zip 下载，用户本地运行）
            boolean buildSuccess = fullStackProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "全栈项目前端构建失败");
            File frontendDistDir = new File(sourceDirPath, "frontend/dist");
            ThrowUtils.throwIf(!frontendDistDir.exists() || !frontendDistDir.isDirectory(),
                    ErrorCode.SYSTEM_ERROR, "全栈项目前端构建成功，但 dist 目录不存在");
            sourceDir = frontendDistDir;
            log.info("全栈项目前端部署完成（静态部署模式），完整源码可通过 /app/download 下载，项目路径: {}", sourceDirPath);
        }
        // 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用部署失败：" + e.getMessage());
        }
        // 更新数据库
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        // 返回可访问的 URL 地址
        String appDeployUrl = String.format("%s/%s", AppConstant.CODE_DEPLOY_HOST, deployKey);
        // 调用截图方法并更新数据库
        return appDeployUrl;
    }

    /**
     * Docker 一键部署模式：执行 docker-compose up -d，返回前端访问地址
     * 仅支持 FULLSTACK 类型（包含 docker-compose.yml）
     */
    private String deployWithDockerCompose(Long appId, String deployKey, String composeProjectName,
                                           String sourceDirPath, CodeGenTypeEnum codeGenTypeEnum, User loginUser) {
        // Docker 部署仅支持全栈项目
        ThrowUtils.throwIf(codeGenTypeEnum != CodeGenTypeEnum.FULLSTACK,
                ErrorCode.PARAMS_ERROR, "Docker 一键部署仅支持全栈项目（FULLSTACK）类型");
        // 执行 Docker Compose 部署
        DockerComposeDeployService.DockerDeployResult result = dockerComposeDeployService.deploy(sourceDirPath, composeProjectName);
        if (!result.isSuccess()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Docker 部署失败：" + result.getErrorMessage());
        }
        // 更新数据库：记录 docker 部署地址
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        updateApp.setDockerDeployUrl(result.getFrontendUrl());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        try {
            App app = this.getById(appId);
            userNotificationService.createDeploymentSuccessNotification(loginUser.getId(), appId,
                    app == null ? "应用" : app.getAppName(), result.getFrontendUrl());
        } catch (Exception exception) {
            log.warn("部署成功通知写入失败，部署结果不受影响，应用 ID: {}", appId, exception);
        }
        // 调用截图方法并更新数据库（截取前端页面）
        log.info("Docker 一键部署成功，前端: {}，后端: {}", result.getFrontendUrl(), result.getBackendUrl());
        return result.getFrontendUrl();
    }

    @Override
    public List<AppDeploymentVO> listMyDockerDeployments(User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        List<App> deployedApps = this.list(QueryWrapper.create()
                .eq("userId", loginUser.getId())
                .isNotNull("dockerDeployUrl")
                .orderBy("deployedTime", false));
        return deployedApps.stream().map(this::buildDockerDeploymentVO).toList();
    }

    @Override
    public AppDeploymentVO startDockerDeployment(Long appId, User loginUser) {
        App app = getDockerDeploymentApp(appId, loginUser);
        String sourceDirPath = getSourceDirPath(app);
        boolean success = dockerComposeDeployService.start(sourceDirPath, "fullstack_" + appId);
        ThrowUtils.throwIf(!success, ErrorCode.SYSTEM_ERROR, "启动容器失败，请确认 Docker Desktop 正在运行且容器未被删除");
        AppDeploymentVO deploymentVO = buildDockerDeploymentVO(app);
        if (deploymentVO.getFrontendUrl() != null) {
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setDockerDeployUrl(deploymentVO.getFrontendUrl());
            this.updateById(updateApp);
        }
        return deploymentVO;
    }

    @Override
    public AppDeploymentVO stopDockerDeployment(Long appId, User loginUser) {
        App app = getDockerDeploymentApp(appId, loginUser);
        boolean success = dockerComposeDeployService.stopKeepingContainers(getSourceDirPath(app), "fullstack_" + appId);
        ThrowUtils.throwIf(!success, ErrorCode.SYSTEM_ERROR, "停止容器失败，请稍后重试");
        return buildDockerDeploymentVO(app);
    }

    private App getDockerDeploymentApp(Long appId, User loginUser) {
        validateAppOwner(appId, loginUser);
        App app = this.getById(appId);
        ThrowUtils.throwIf(StrUtil.isBlank(app.getDockerDeployUrl()), ErrorCode.PARAMS_ERROR, "该应用尚未完成 Docker 部署");
        ThrowUtils.throwIf(CodeGenTypeEnum.FULLSTACK != CodeGenTypeEnum.getEnumByValue(app.getCodeGenType()),
                ErrorCode.PARAMS_ERROR, "只有全栈应用支持 Docker 容器管理");
        return app;
    }

    private AppDeploymentVO buildDockerDeploymentVO(App app) {
        String sourceDirPath = getSourceDirPath(app);
        DockerComposeDeployService.DockerRuntimeInfo runtimeInfo = dockerComposeDeployService
                .getRuntimeInfo(sourceDirPath, "fullstack_" + app.getId());
        String frontendUrl = StrUtil.blankToDefault(runtimeInfo.getFrontendUrl(), app.getDockerDeployUrl());
        return AppDeploymentVO.builder()
                .appId(app.getId())
                .appName(app.getAppName())
                .codeGenType(app.getCodeGenType())
                .composeProjectName("fullstack_" + app.getId())
                .frontendUrl(frontendUrl)
                .backendUrl(runtimeInfo.getBackendUrl())
                .status(runtimeInfo.getStatus())
                .statusMessage(runtimeInfo.getStatusMessage())
                .deployedTime(app.getDeployedTime())
                .build();
    }

    private String getSourceDirPath(App app) {
        return AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + app.getCodeGenType() + "_" + app.getId();
    }

    /**
     * 异步生成应用截图并入库
     * @param appId 应用 ID
     * @param appUrl 应用访问网址
     */
    @Override
    public void generateScreenShotAsync(Long appId, String appUrl){
        Thread.startVirtualThread(() -> {
            try {
            // 生成截图并获取可访问的截图地址
            String screenshotUrl = screenShotService.generateAndSaveScreenshot(appUrl);
            log.info("更新应用封面截图： {}", screenshotUrl);
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(screenshotUrl);
            boolean updateResult = this.updateById(updateApp);
            if (!updateResult) {
                log.warn("更新应用封面截图失败，appId: {}", appId);
            }
            } catch (Exception exception) {
                // Screenshot is best-effort and must never interrupt generation, preview or deployment.
                log.warn("异步生成应用封面截图失败，appId: {}, url: {}", appId, appUrl, exception);
            }
        });
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public boolean isDownLoadAppAllowed(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        App app = getById(appId);
        // 拼接项目预览目录
        String previewDir = app.getCodeGenType() + "_" + appId;
        return checkFileExists(previewDir);
    }

    @Override
    public List<CodeFileTreeNode> listCodeFileTree(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权查看该应用的代码目录");
        }

        Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR,
                app.getCodeGenType() + "_" + appId).toAbsolutePath().normalize();
        if (!Files.isDirectory(projectRoot)) {
            return List.of();
        }
        return readCodeTree(projectRoot, projectRoot);
    }

    private List<CodeFileTreeNode> readCodeTree(Path projectRoot, Path directory) {
        try (var paths = Files.list(directory)) {
            return paths
                    .filter(path -> !CODE_TREE_IGNORED_NAMES.contains(path.getFileName().toString()))
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .sorted(Comparator
                            .comparing((Path path) -> !Files.isDirectory(path))
                            .thenComparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .map(path -> toCodeTreeNode(projectRoot, path))
                    .toList();
        } catch (Exception exception) {
            log.warn("读取应用代码目录失败: {}", directory, exception);
            return List.of();
        }
    }

    private CodeFileTreeNode toCodeTreeNode(Path projectRoot, Path path) {
        CodeFileTreeNode node = new CodeFileTreeNode();
        boolean directory = Files.isDirectory(path);
        node.setTitle(path.getFileName().toString());
        node.setKey(projectRoot.relativize(path).toString().replace(File.separatorChar, '/'));
        node.setLeaf(!directory);
        if (directory) {
            node.setChildren(readCodeTree(projectRoot, path));
        }
        return node;
    }

    /**
     * 判断传入的文件名是否在项目目录下
     * @param fileName 目标文件名
     * @return
     */
    private boolean checkFileExists(String fileName){
        String projectRoot = AppConstant.CODE_OUTPUT_ROOT_DIR;
        // 构建完整路径：项目根目录 + 目标子文件夹
        Path targetPath = Paths.get(projectRoot, fileName);
        // 检查路径是否存在且是目录
        return Files.exists(targetPath) && Files.isDirectory(targetPath);
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    /**
     * 删除应用时，关联删除对话历史
     *
     * @param id
     * @return
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        long appId = Long.parseLong(id.toString());
        if (appId <= 0) {
            return false;
        }
        // 先删除关联的对话历史
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            log.error("删除应用关联的对话历史失败：{}", e.getMessage());
        }
        // 删除应用
        return super.removeById(id);
    }
}
