package com.wgz.aikir.controller;

import com.wgz.aikir.common.BaseResponse;
import com.wgz.aikir.common.ResultUtils;
import com.wgz.aikir.exception.BusinessException;
import com.wgz.aikir.exception.ErrorCode;
import com.wgz.aikir.service.MultimediaUploadService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/multiMedia")
public class MultiMediaUploadController {

    @Resource
    private MultimediaUploadService multimediaUploadService;

    /**
     * 上传提示词图片
     * @param file
     * @return
     */
    @PostMapping("/promptImageUpload")
    public BaseResponse<String> promptImageUpload(@RequestPart("file") MultipartFile file) {
        // 参数校验
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件数据不能为空");
        }
        return ResultUtils.success(multimediaUploadService.uploadImage(file));
    }
}
