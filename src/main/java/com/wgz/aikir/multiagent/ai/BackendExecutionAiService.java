package com.wgz.aikir.multiagent.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/** 只生成后端文件清单的 M2 智能体；不向模型暴露文件或命令工具。 */
public interface BackendExecutionAiService {

    @SystemMessage(fromResource = "prompt/multiagent/backend-execution-system-prompt.txt")
    String generateBackendBundle(@UserMessage String executionContext);
}
