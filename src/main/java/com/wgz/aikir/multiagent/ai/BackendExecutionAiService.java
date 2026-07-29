package com.wgz.aikir.multiagent.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.TokenStream;

/** 只生成后端文件清单的 M2 智能体；不向模型暴露文件或命令工具。 */
public interface BackendExecutionAiService {

    @SystemMessage(fromResource = "prompt/multiagent/backend-file-manifest-system-prompt.txt")
    TokenStream generateBackendFileManifest(@UserMessage String executionContext);

    @SystemMessage(fromResource = "prompt/multiagent/backend-source-system-prompt.txt")
    TokenStream generateBackendSourceFile(@UserMessage String executionContext);
}
