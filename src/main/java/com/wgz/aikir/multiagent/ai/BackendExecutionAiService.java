package com.wgz.aikir.multiagent.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.TokenStream;

/** 只生成后端文件清单的 M2 智能体；不向模型暴露文件或命令工具。 */
public interface BackendExecutionAiService {

    @SystemMessage(fromResource = "prompt/multiagent/backend-execution-system-prompt.txt")
    TokenStream generateBackendBundle(@UserMessage String executionContext);
}
