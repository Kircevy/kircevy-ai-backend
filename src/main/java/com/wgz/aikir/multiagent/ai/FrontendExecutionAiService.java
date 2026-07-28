package com.wgz.aikir.multiagent.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.TokenStream;

/** 只生成前端文件清单的 M2 智能体；不向模型暴露文件或命令工具。 */
public interface FrontendExecutionAiService {

    @SystemMessage(fromResource = "prompt/multiagent/frontend-file-manifest-system-prompt.txt")
    TokenStream generateFrontendFileManifest(@UserMessage String executionContext);

    @SystemMessage(fromResource = "prompt/multiagent/frontend-source-system-prompt.txt")
    TokenStream generateFrontendSourceFile(@UserMessage String executionContext);
}
