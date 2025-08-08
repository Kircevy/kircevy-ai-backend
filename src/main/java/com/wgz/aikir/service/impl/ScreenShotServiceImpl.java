package com.wgz.aikir.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.wgz.aikir.constant.AppConstant;
import com.wgz.aikir.exception.ErrorCode;
import com.wgz.aikir.exception.ThrowUtils;
import com.wgz.aikir.manage.CosManager;
import com.wgz.aikir.service.ScreenShotService;
import com.wgz.aikir.utils.WebScreenShotUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@Slf4j
public class ScreenShotServiceImpl implements ScreenShotService {

    @Resource
    private CosManager cosManeger;

    /**
     * 生成并保存网页截图
     * @param webUrl
     * @return
     */
    @Override
    public String generateAndSaveScreenshot(String webUrl) {
        // 参数校验
        if (StrUtil.isBlank(webUrl)){
            log.info("网页url不能为空");
            return null;
        }
        log.info("开始生成网页截图: {}", webUrl);
        // 生成本地截图
        String localScreenshotPath = WebScreenShotUtils.saveWebPageScreenshot(webUrl);
        ThrowUtils.throwIf(StrUtil.isBlank(localScreenshotPath), ErrorCode.OPERATION_ERROR, "本地截图生成失败");
        // 上传截图到 Cos 对象存储
        try {
            String cosUrl = uploadScreenshotToCos(localScreenshotPath);
            ThrowUtils.throwIf(StrUtil.isBlank(cosUrl), ErrorCode.OPERATION_ERROR, "截图上传对象存储失败");
            log.info("网页截图生成并上传成功: {} -> {}", webUrl, cosUrl);
            return cosUrl;
        } finally {
            // 清理本地文件
            cleanupLocalFile(localScreenshotPath);
        }
    }

    private String uploadScreenshotToCos(String localScreenshotPath) {
        if (StrUtil.isBlank(localScreenshotPath)) {
            return null;
        }
        File screenShotFile = new File(localScreenshotPath);
        if (!screenShotFile.exists()) {
            log.error("截图文件不存在: {}", localScreenshotPath);
            return null;
        }
        // 生成 COS 对象键
        String fileName = UUID.randomUUID().toString().substring(0, 8) + "_compressed.jpg";
        String screenShotKey = generateScreenShotKey(fileName);
        return cosManeger.uploadFile(screenShotKey, screenShotFile);
    }

    /**
     * 生成截图对象存储键
     * @param fileName
     * @return
     */
    private String generateScreenShotKey(String fileName) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("/screenshot/%s/%s",datePath , fileName);
    }

    /**
     * 清理本地文件
     *
     * @param localFilePath 本地文件路径
     */
    private void cleanupLocalFile(String localFilePath) {
        File localFile = new File(localFilePath);
        if (localFile.exists()) {
            File parentDir = localFile.getParentFile();
            FileUtil.del(parentDir);
            log.info("本地截图文件已清理: {}", localFilePath);
        }
    }
}
