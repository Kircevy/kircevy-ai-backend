package com.wgz.aikir.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.wgz.aikir.model.dto.app.AppQueryRequest;
import com.wgz.aikir.model.entity.App;
import com.wgz.aikir.model.entity.User;
import com.wgz.aikir.model.enums.DeployModeEnum;
import com.wgz.aikir.model.vo.AppVO;
import com.wgz.aikir.model.vo.CodeFileTreeNode;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 */
public interface AppService extends IService<App> {

    /**
     * 通过对话生成应用代码
     *
     * @param appId 应用 ID
     * @param message 提示词
     * @param loginUser 登录用户
     * @return
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    boolean isCodeGenerationRunning(Long appId, User loginUser);

    Flux<String> subscribeCodeGeneration(Long appId, User loginUser);

    /**
     * 应用部署（默认代码下载模式，兼容旧接口）
     *
     * @param appId 应用 ID
     * @param loginUser 登录用户
     * @return 可访问的部署地址或下载提示
     */
    String deployApp(Long appId, User loginUser);

    /**
     * 应用部署（按部署模式分发）
     *
     * @param appId      应用 ID
     * @param deployMode 部署模式（CODE_DOWNLOAD / DOCKER_COMPOSE）
     * @param loginUser  登录用户
     * @return 可访问的部署地址或下载提示
     */
    String deployApp(Long appId, DeployModeEnum deployMode, User loginUser);

    /**
     * 异步生成应用截图并入库
     * @param appId
     * @param appUrl
     */
    void generateScreenShotAsync(Long appId, String appUrl);

    /**
     * 获取应用封装类
     *
     * @param app
     * @return
     */
    AppVO getAppVO(App app);

    boolean isDownLoadAppAllowed(Long appId);

    /**
     * Read the generated project directory tree for its owner.
     */
    List<CodeFileTreeNode> listCodeFileTree(Long appId, User loginUser);

    /**
     * 获取应用封装类列表
     *
     * @param appList
     * @return
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 构造应用查询条件
     *
     * @param appQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

}
