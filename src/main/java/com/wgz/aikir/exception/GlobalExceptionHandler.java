package com.wgz.aikir.exception;

import cn.hutool.json.JSONUtil;
import com.wgz.aikir.common.BaseResponse;
import com.wgz.aikir.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.util.Map;

@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        // 尝试处理 SSE 请求
        if (handleSseError(e.getCode(), e.getMessage())) {
            return null;
        }
        // 对于普通请求，返回标准 JSON 响应
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        // 尝试处理 SSE 请求
        if (handleSseError(ErrorCode.SYSTEM_ERROR.getCode(), "系统错误")) {
            return null;
        }
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }

    /**
     * 处理SSE请求的错误响应
     *
     * @param errorCode 错误码
     * @param errorMessage 错误信息
     * @return true表示是SSE请求并已处理，false表示不是SSE请求
     */
    private boolean handleSseError(int errorCode, String errorMessage) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return false;
        }
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();
        // 判断是否是SSE请求（通过Accept头或URL路径）
        String accept = request.getHeader("Accept");
        String uri = request.getRequestURI();
        boolean isSse = (accept != null && accept.contains("text/event-stream"))
                || uri.contains("/chat/gen/code");
        if (!isSse) {
            return false;
        }
        // SSE 响应已由 Flux 框架占用 output stream，不能再写入
        if (response.isCommitted()) {
            log.warn("SSE 响应已提交，无法写入错误信息");
            return true;
        }
        try {
            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");
            Map<String, Object> errorData = Map.of(
                    "error", true,
                    "code", errorCode,
                    "message", errorMessage
            );
            String errorJson = JSONUtil.toJsonStr(errorData);
            String ssePayload = "event: business-error\ndata: " + errorJson + "\n\nevent: done\ndata: {}\n\n";
            // 使用 getOutputStream 而非 getWriter，避免与 SSE 框架冲突
            jakarta.servlet.ServletOutputStream out = response.getOutputStream();
            out.write(ssePayload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.flush();
            return true;
        } catch (Exception e) {
            log.warn("写入 SSE 错误响应失败: {}", e.getMessage());
            return true;
        }
    }
}
