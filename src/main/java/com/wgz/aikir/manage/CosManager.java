package com.wgz.aikir.manage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.wgz.aikir.config.CosClientConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@Slf4j
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;


    /**
     * 上传对象
     * @param key 唯一key
     * @param file  文件
     * @return 上传结果
     */
    public PutObjectResult putObject(String key, File file){
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        return cosClient.putObject(putObjectRequest);
    }

    public String uploadFile(String key, File file) {
        PutObjectResult result = putObject(key, file);
        if (result != null){
            String url = String.format("%s%s", cosClientConfig.getHost(), key);
            log.info("上传到Cos成功，上传file名: {} -> url: {}", file.getName(), url);
            return url;
        }else {
            log.error("上传文件到Cos失败，上传file: {}", file);
            return null;
        }
    }
}
