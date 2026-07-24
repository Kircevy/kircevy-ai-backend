package com.wgz.aikir.multiagent.controller;

import com.wgz.aikir.annotation.AuthCheck;
import com.wgz.aikir.common.BaseResponse;
import com.wgz.aikir.common.ResultUtils;
import com.wgz.aikir.constant.UserConstant;
import com.wgz.aikir.multiagent.model.request.MultiAgentSettingsUpdateRequest;
import com.wgz.aikir.multiagent.model.vo.MultiAgentSettingsVO;
import com.wgz.aikir.multiagent.service.MultiAgentSettingsService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员专用的二阶段功能开关接口。 */
@RestController
@RequestMapping("/admin/multi-agent/settings")
public class MultiAgentAdminController {

    @Resource
    private MultiAgentSettingsService multiAgentSettingsService;

    @GetMapping
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<MultiAgentSettingsVO> getSettings() {
        return ResultUtils.success(multiAgentSettingsService.getSettings());
    }

    @PostMapping
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<MultiAgentSettingsVO> updateSettings(@RequestBody MultiAgentSettingsUpdateRequest request) {
        return ResultUtils.success(multiAgentSettingsService.updateSettings(request));
    }
}
