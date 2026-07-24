package com.wgz.aikir.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.wgz.aikir.mapper.UserNotificationMapper;
import com.wgz.aikir.model.entity.UserNotification;
import com.wgz.aikir.service.UserNotificationService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 站内通知服务实现。
 */
@Service
public class UserNotificationServiceImpl extends ServiceImpl<UserNotificationMapper, UserNotification>
        implements UserNotificationService {

    @Override
    public void createDeploymentSuccessNotification(Long userId, Long appId, String appName, String frontendUrl) {
        UserNotification notification = new UserNotification();
        notification.setUserId(userId);
        notification.setType("DEPLOY_SUCCESS");
        notification.setTitle("应用部署成功");
        notification.setContent("“" + appName + "” 已部署成功，可在我的部署中访问、启动或停止容器。访问地址：" + frontendUrl);
        notification.setTargetPath("/deployment");
        notification.setIsRead(0);
        this.save(notification);
    }

    @Override
    public List<UserNotification> listUserNotifications(Long userId) {
        return this.list(QueryWrapper.create()
                .eq("userId", userId)
                .orderBy("createTime", false)
                .limit(1, 20));
    }

    @Override
    public boolean markAsRead(Long notificationId, Long userId) {
        UserNotification notification = this.getById(notificationId);
        if (notification == null || !userId.equals(notification.getUserId())) {
            return false;
        }
        notification.setIsRead(1);
        return this.updateById(notification);
    }
}
