package com.wgz.aikir.service.impl;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.wgz.aikir.constant.AppConstant;
import com.wgz.aikir.exception.BusinessException;
import com.wgz.aikir.exception.ErrorCode;
import com.wgz.aikir.exception.ThrowUtils;
import com.wgz.aikir.manage.CosManager;
import com.wgz.aikir.service.MultimediaUploadService;
import com.wgz.aikir.utils.WebScreenShotUtils;
import jakarta.annotation.Resource;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.tomcat.jni.FileInfo;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class MultimediaUploadServiceImpl implements MultimediaUploadService {

    @Resource
    private CosManager cosManager;

    /**
     * 上传图片，返回前端可访问的url路径（压缩后的）
     * 
     * @param multipartFile 上传的文件
     * @return 图片访问URL
     */
    @Override
    public String uploadImage(MultipartFile multipartFile) {
        // 1. 文件验证
        validateUploadFile(multipartFile);

        // 2. 生成文件信息
        FileInfo fileInfo = generateFileInfo(multipartFile);

        // 3. 创建临时工作空间
        Path tempWorkspace = createTempWorkspace();

        // 4.上传原始图片到临时目录
        String originalImagePath = uploadOriginalImage(multipartFile, fileInfo, tempWorkspace);
        try {
            // 5. 处理并上传图片 方法1
            return compressAndUploadImage(originalImagePath, fileInfo);
            // 5. 处理并上传图片 方法2
//            return processAndUploadImage(multipartFile, fileInfo, tempWorkspace);
        } catch (Exception e) {
            log.error("图片上传失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片上传处理失败: " + e.getMessage());
        } finally {
            // 5. 清理临时资源
            cleanupWorkspace(tempWorkspace);
        }
    }

    private String compressAndUploadImage(String originalImagePath, FileInfo fileInfo) {
        final String COMPRESSION_SUFFIX = "_compressed.jpg";
        String compressedImagePath = AppConstant.CODE_MULTI_MEDIA_DIR + File.separator + fileInfo.getUniqueName() + COMPRESSION_SUFFIX;
        // 压缩图片
        final float COMPRESS_QUALITY = 0.3f;
        try {
            ImgUtil.compress(
                    FileUtil.file(originalImagePath),
                    FileUtil.file(compressedImagePath),
                    COMPRESS_QUALITY
            );

            log.info("图片压缩成功: {}", compressedImagePath);
            // 验证压缩结果
            ThrowUtils.throwIf(!Files.exists(Path.of(compressedImagePath)),
                    ErrorCode.SYSTEM_ERROR, "图片压缩失败");
            // 上传到COS
            String cosUrl = cosManager.uploadFile(fileInfo.cosKey, FileUtil.file(compressedImagePath));
            ThrowUtils.throwIf(StrUtil.isBlank(cosUrl), ErrorCode.SYSTEM_ERROR, "图片上传到Cos失败");
            return cosUrl;
        } catch (Exception e) {
            log.error("图片处理失败: {}", e.getMessage(), e);
            return null;
        }
    }

    private String uploadOriginalImage(MultipartFile multipartFile, FileInfo fileInfo, Path tempWorkspace)  {
        String originalImageFilePath = tempWorkspace.resolve(fileInfo.getUniqueName()).toString();
        File originalImageFile = new File(originalImageFilePath);
        try {
            multipartFile.transferTo(originalImageFile);
            if (!originalImageFile.exists()){
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片上传失败");
            }
            log.info("图片上传成功: {}", originalImageFilePath);
            return originalImageFilePath;
        } catch (Exception e) {
            log.error("图片上传失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 文件验证
     */
    private void validateUploadFile(MultipartFile file) {
        ThrowUtils.throwIf(file == null || file.isEmpty(),
                ErrorCode.PARAMS_ERROR, "上传文件不能为空");

        ThrowUtils.throwIf(file.getSize() > MAX_FILE_SIZE,
                ErrorCode.PARAMS_ERROR, "文件大小超过限制");

        String contentType = file.getContentType();
        ThrowUtils.throwIf(!ALLOWED_IMAGE_TYPES.contains(contentType),
                ErrorCode.PARAMS_ERROR, "不支持的图片格式");
    }

    /**
     * 生成文件信息
     */
    private FileInfo generateFileInfo(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String extension = FilenameUtils.getExtension(originalName);
        String uniqueName = generateUniqueFileName(extension);
        String cosKey = generateCosKey(uniqueName);

        return FileInfo.builder()
                .originalName(originalName)
                .uniqueName(uniqueName)
                .extension(extension)
                .cosKey(cosKey)
                .build();
    }

    /**
     * 创建临时工作空间
     */
    private Path createTempWorkspace() {
        String dir = AppConstant.CODE_MULTI_MEDIA_DIR;
        FileUtil.mkdir(dir);
        return Paths.get(dir);
    }

    /**
     * 处理并上传图片
     */
    private String processAndUploadImage(MultipartFile file, FileInfo fileInfo, Path workspace)
            throws IOException {

        // 1. 直接压缩到目标位置
        Path compressedPath = workspace.resolve("compressed_" + fileInfo.getUniqueName());

        try (InputStream inputStream = file.getInputStream()) {
            compressImageStream(inputStream, compressedPath.toString());
            log.info("图片压缩完成: {}", compressedPath);
        }

        // 2. 验证压缩结果
        ThrowUtils.throwIf(!Files.exists(compressedPath),
                ErrorCode.SYSTEM_ERROR, "图片压缩失败");

        // 3. 上传到COS
        String cosUrl = cosManager.uploadFile(fileInfo.getCosKey(), compressedPath.toFile());
        ThrowUtils.throwIf(StrUtil.isBlank(cosUrl),
                ErrorCode.SYSTEM_ERROR, "上传到对象存储失败");

        log.info("图片上传成功: {} -> {}", fileInfo.getOriginalName(), cosUrl);
        return cosUrl;
    }

    private void compressImageStream(InputStream inputStream, String outputPath) {
        final float COMPRESS_QUALITY = 0.3f;

        // 创建临时文件来存储原始图片
        Path tempOriginalFile = null;
        try {
            // 在同一个工作目录创建临时原始文件
            Path outputDir = Paths.get(outputPath).getParent();
            tempOriginalFile = Files.createTempFile(outputDir, "temp_original_", ".tmp");

            // 将输入流写入临时文件
            try (FileOutputStream fos = new FileOutputStream(tempOriginalFile.toFile())) {
                inputStream.transferTo(fos);
            }

            // 使用 HuTool 压缩图片
            ImgUtil.compress(tempOriginalFile.toFile(), FileUtil.file(outputPath), COMPRESS_QUALITY);

            log.info("图片流压缩完成: {}", outputPath);

        } catch (Exception e) {
            log.error("压缩图片流失败: {}", outputPath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩图片流失败: " + e.getMessage());
        } finally {
            // 清理临时文件
            if (tempOriginalFile != null && Files.exists(tempOriginalFile)) {
                try {
                    Files.delete(tempOriginalFile);
                } catch (IOException e) {
                    log.warn("删除临时文件失败: {}", tempOriginalFile, e);
                }
            }
        }
    }

    /**
     * 生成唯一文件名
     */
    private String generateUniqueFileName(String extension) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String randomId = UUID.randomUUID().toString().substring(0, 8);
        return String.format("%s_%s%s", timestamp, randomId,
                StrUtil.isNotBlank(extension) ? "." + extension : "");
    }

    /**
     * 生成COS对象键
     */
    private String generateCosKey(String fileName) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("images/uploads/%s/%s", datePath, fileName);
    }

    /**
     * 清理工作空间
     */
    private void cleanupWorkspace(Path workspace) {
        if (workspace != null && Files.exists(workspace)) {
            try {
                FileUtil.del(workspace.toFile());
                log.debug("临时工作空间已清理: {}", workspace);
            } catch (Exception e) {
                log.warn("清理临时工作空间失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 文件信息封装类
     */
    @Data
    @Builder
    private static class FileInfo {
        private String originalName;
        private String uniqueName;
        private String extension;
        private String cosKey;
    }

    // 常量定义
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );
}