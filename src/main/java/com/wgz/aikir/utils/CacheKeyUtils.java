package com.wgz.aikir.utils;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;

public class CacheKeyUtils {

    /**
     * 生成redis缓存唯一键
     * @return key
     */
    public static String generateKey(Object obj) {
        if (obj == null) {
            DigestUtil.md5Hex("null");
        }
        // 先转为JSON，在转md5，既保证唯一性又节省内存空间
        String jsonStr = JSONUtil.toJsonStr(obj);
        return DigestUtil.md5Hex(jsonStr);
    }
}
