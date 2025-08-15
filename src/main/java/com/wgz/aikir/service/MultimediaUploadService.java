package com.wgz.aikir.service;

import com.wgz.aikir.model.entity.User;
import org.springframework.web.multipart.MultipartFile;

/**
 * 多媒体上传
 */
public interface MultimediaUploadService {

    /**
     * 图片上传到对象存储
     * @param multipartFile
     * @return
     */
    String uploadImage(MultipartFile multipartFile);
}
