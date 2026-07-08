package com.wgz.aikir.ai.tools;

import cn.hutool.json.JSONObject;
import com.wgz.aikir.constant.AppConstant;
import com.wgz.aikir.model.enums.CodeGenTypeEnum;
import com.wgz.aikir.service.AppService;
import com.wgz.aikir.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具基类
 * 定义所有工具的通用接口
 */
@Slf4j
public abstract class BaseTool {

    /**
     * 获取工具的英文名称（对应方法名）
     *
     * @return 工具英文名称
     */
    public abstract String getToolName();

    /**
     * 获取工具的中文显示名称
     *
     * @return 工具中文名称
     */
    public abstract String getDisplayName();

    /**
     * 生成工具请求时的返回值（显示给用户）
     *
     * @return 工具请求显示内容
     */
    public String generateToolRequestResponse() {
        return String.format("\n\n[选择工具] %s\n\n", getDisplayName());
    }

    /**
     * 生成工具执行结果格式（保存到数据库）
     *
     * @param arguments 工具执行参数
     * @return 格式化的工具执行结果
     */
    public abstract String generateToolExecutedResult(JSONObject arguments);

    /**
     * 根据 appId 动态获取项目目录名
     * 支持多种项目类型：vue_project, springboot, fullstack 等
     *
     * @param appId 应用 ID
     * @return 项目目录名称（如 "vue_project_123"）
     */
    protected String getProjectDirName(Long appId) {
        try {
            AppService appService = SpringContextUtil.getBean(AppService.class);
            var app = appService.getById(appId);
            if (app != null && app.getCodeGenType() != null) {
                String codeGenType = app.getCodeGenType();
                CodeGenTypeEnum typeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
                if (typeEnum != null) {
                    return typeEnum.getValue() + "_" + appId;
                }
            }
        } catch (Exception e) {
            log.warn("查询应用类型失败，使用默认 code_output 前缀: {}", e.getMessage());
        }
        // 默认回退：使用通用前缀，避免 FULLSTACK/SPRINGBOOT 写错目录
        return "code_output_" + appId;
    }

    /**
     * 获取项目根目录路径
     *
     * @param appId 应用 ID
     * @return 项目根目录路径字符串
     */
    protected String getProjectRootPath(Long appId) {
        return AppConstant.CODE_OUTPUT_ROOT_DIR + "/" + getProjectDirName(appId);
    }
}
