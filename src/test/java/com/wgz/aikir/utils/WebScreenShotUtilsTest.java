package com.wgz.aikir.utils;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Slf4j
class WebScreenShotUtilsTest {

    @Test
    void saveWebPageScreenshot() {
        String testPath = "https://www.baidu.com";
        String result = WebScreenShotUtils.saveWebPageScreenshot(testPath);
        Assertions.assertNotNull(result);
        log.info("截图保存路径：{}", result);
    }
}