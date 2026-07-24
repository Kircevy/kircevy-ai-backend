package com.wgz.aikir.controller;

import com.wgz.aikir.common.BaseResponse;
import com.wgz.aikir.common.ResultUtils;
import com.wgz.aikir.model.entity.User;
import com.wgz.aikir.model.entity.UserNotification;
import com.wgz.aikir.service.UserNotificationService;
import com.wgz.aikir.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前用户的站内通知接口。
 */
@RestController
@RequestMapping("/notification")
public class UserNotificationController {

    @Resource
    private UserNotificationService userNotificationService;

    @Resource
    private UserService userService;

    @GetMapping("/my")
    public BaseResponse<List<UserNotification>> listMyNotifications(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userNotificationService.listUserNotifications(loginUser.getId()));
    }

    @PostMapping("/{notificationId}/read")
    public BaseResponse<Boolean> markAsRead(@PathVariable Long notificationId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userNotificationService.markAsRead(notificationId, loginUser.getId()));
    }
}
