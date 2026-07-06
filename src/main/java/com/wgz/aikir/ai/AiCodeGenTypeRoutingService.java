package com.wgz.aikir.ai;

import com.wgz.aikir.ai.model.HtmlCodeResult;
import com.wgz.aikir.ai.model.MultiFileCodeResult;
import com.wgz.aikir.model.enums.CodeGenTypeEnum;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

public interface AiCodeGenTypeRoutingService {

    /**
     * ai生成代码生成类型
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    @SystemMessage(fromResource = "prompt/codegen-routing-system-prompt.txt")
    CodeGenTypeEnum routeCodeGenType (@UserMessage String userMessage);

}