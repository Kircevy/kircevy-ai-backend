package com.wgz.aikir.multiagent.service;

import com.wgz.aikir.multiagent.model.request.MultiAgentSettingsUpdateRequest;
import com.wgz.aikir.multiagent.model.vo.MultiAgentSettingsVO;

/** 管理二阶段功能开关的运行时状态与 YAML 持久化。 */
public interface MultiAgentSettingsService {

    /** 获取当前已经生效的开关配置。 */
    MultiAgentSettingsVO getSettings();

    /** 更新开关配置，立即生效并同步写入外部 YAML 文件。 */
    MultiAgentSettingsVO updateSettings(MultiAgentSettingsUpdateRequest request);
}
