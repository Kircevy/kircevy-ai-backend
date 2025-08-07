package com.wgz.aikir.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.wgz.aikir.ai.model.HtmlCodeResult;
import com.wgz.aikir.ai.model.MultiFileCodeResult;
import com.wgz.aikir.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 文件保存器
 */
@Deprecated
public class CodeFileSaver {

    /**
     * 文件保存的根目录
     */
    private static final String FILE_SAVE_ROOT_DIR = System.getProperty("use.dir") + "/tmp/code_output";

    /**
     * 保存 HTML 网页代码
     *
     * @param htmlCodeResult
     * @return
     */
    public static File savaHtmlCodeResult(HtmlCodeResult htmlCodeResult) {
        String dirPath = buildUnique(CodeGenTypeEnum.HTML.getValue());
        writeToFile(dirPath, "index.html", htmlCodeResult.getHtmlCode());
        return new File(dirPath);
    }

    /**
     * 保存多文件网页代码
     *
     * @param multiFileCodeResult
     * @return
     */
    public static File savaMultiCodeResult(MultiFileCodeResult multiFileCodeResult) {
        String dirPath = buildUnique(CodeGenTypeEnum.MULTI_FILE.getValue());
        writeToFile(dirPath, "index.html", multiFileCodeResult.getHtmlCode());
        writeToFile(dirPath, "style.css", multiFileCodeResult.getCssCode());
        writeToFile(dirPath, "script.js", multiFileCodeResult.getJsCode());
        return new File(dirPath);
    }

    /**
     * 构建文件的唯一路径：tmp/code_output/bizType_雪花 ID
     *
     * @param bizType 代码生成类型
     * @return
     */
    private static String buildUnique(String bizType){
        String uniquePathName = FILE_SAVE_ROOT_DIR + File.separator + StrUtil.format("{}_{}", bizType, IdUtil.getSnowflakeNextId());
        FileUtil.mkdir(uniquePathName);
        return uniquePathName;
    }


    /**
     * 保存单个文件
     *
     * @param dirPath
     * @param filename
     * @param content
     */
    private static void writeToFile(String dirPath, String filename, String content){
        String filePath = dirPath + File.separator + filename;
        FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
    }
}
