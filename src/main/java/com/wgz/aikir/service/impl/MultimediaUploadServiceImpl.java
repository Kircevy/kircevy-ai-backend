package com.wgz.aikir.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.wgz.aikir.exception.ErrorCode;
import com.wgz.aikir.exception.ThrowUtils;
import com.wgz.aikir.manage.CosManager;
import com.wgz.aikir.service.MultimediaUploadService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;


@Service
@Slf4j
public class MultimediaUploadServiceImpl implements MultimediaUploadService {

    @Resource
    private CosManager cosManager;


    /**
     * 上传图片，返回前端可访问的url路径（压缩后的）
     * @param multipartFile 上传的文件
     * @return 图片访问URL
     */
    @Override
    public String uploadImage(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            log.warn("上传图片失败，文件为空");
            return null;
        }

        // 创建临时目录
        String tempDir = createTempDirectory();
        if (tempDir == null) {
            log.error("创建临时目录失败");
            return null;
        }

        // 生成唯一的文件名
        String originalFilename = multipartFile.getOriginalFilename();
        String fileExtension = FilenameUtils.getExtension(originalFilename);
        String uniqueFileName = UUID.randomUUID().toString().substring(0, 8) +
                (fileExtension != null ? "." + fileExtension : "");

        // 完整的本地文件路径
        String localFilePath = tempDir + File.separator + uniqueFileName;
        String compressedFilePath = tempDir + File.separator + "compressed_" + uniqueFileName;

        try {
            // 1. 将上传的文件保存到本地
            File localFile = new File(localFilePath);
            multipartFile.transferTo(localFile);
            log.info("文件已保存到本地: {}", localFilePath);

//            // 2. 压缩图片
//            File compressedFile = imageCompressionService.compressImage(localFile, compressedFilePath);
//            if (compressedFile == null || !compressedFile.exists()) {
//                log.warn("图片压缩失败，使用原始文件");
//                compressedFile = localFile; // 使用原始文件作为备选
//            }

            // 3. 上传到COS
            String cosKey = generatePromptImageKey(localFile.getName());
            String cosUrl = cosManager.uploadFile(cosKey, localFile);

            if (StrUtil.isBlank(cosUrl)) {
                log.error("上传到对象存储失败");
                return null;
            }

            log.info("图片成功上传到COS: {}", cosUrl);
            return cosUrl;
        } catch (IOException e) {
            log.error("文件处理失败: ", e);
            return null;
        } finally {
            // 4. 清理本地临时文件
            cleanupTempFiles(tempDir);
        }
    }

    /**
     * 创建临时目录
     * @return 临时目录路径
     */
    private String createTempDirectory() {
        String rootDir = System.getProperty("user.dir") + File.separator + "tmp" + File.separator +
                         "multimedia" + File.separator + "promptImage";
        FileUtil.mkdir(rootDir);
        return rootDir;
    }

    /**
     * 生成COS对象键
     * @param fileName 文件名
     * @return COS键
     */
    private String generatePromptImageKey(String fileName) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("prompt/%s/%s", datePath, fileName);
    }

    /**
     * 清理临时文件
     * @param tempDirPath 临时目录路径
     */
    private void cleanupTempFiles(String tempDirPath) {
        if (tempDirPath != null) {
            File tempDir = new File(tempDirPath);
            if (tempDir.exists() && tempDir.isDirectory()) {
                FileUtil.del(tempDir);
                log.info("临时目录已清理: {}", tempDirPath);
            }
        }
    }
}