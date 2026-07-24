package com.wgz.aikir.service;

import com.mybatisflex.core.service.IService;
import com.wgz.aikir.model.entity.UserNotification;

import java.util.List;

/**
 * 站内通知服务。
 */
public interface UserNotificationService extends IService<UserNotification> {

    void createDeploymentSuccessNotification(Long userId, Long appId, String appName, String frontendUrl);

    List<UserNotification> listUserNotifications(Long userId);

    boolean markAsRead(Long notificationId, Long userId);
}
