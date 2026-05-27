package com.wgz.aikir.agentscope.controller;

import com.wgz.aikir.agentscope.agent.CodeProjectAssistantAgent;
import com.wgz.aikir.agentscope.model.AgentScopeDemoRequest;
import com.wgz.aikir.common.BaseResponse;
import com.wgz.aikir.common.ResultUtils;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/agentscope/demo")
public class AgentScopeDemoController {

    @Resource
    private CodeProjectAssistantAgent codeProjectAssistantAgent;

    @PostMapping("/chat")
    public BaseResponse<Map<String, String>> chat(@Valid @RequestBody AgentScopeDemoRequest request) {
        String answer = codeProjectAssistantAgent.chat(request.getMessage());
        return ResultUtils.success(Map.of("answer", answer));
    }
}
