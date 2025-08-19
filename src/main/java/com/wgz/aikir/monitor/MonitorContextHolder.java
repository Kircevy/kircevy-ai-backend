package com.wgz.aikir.monitor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MonitorContextHolder {
    private static final ThreadLocal<MonitorContext> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 设置监控上下文
     * @param context
     */
    public static void setContextHolder(MonitorContext context) {
        CONTEXT_HOLDER.set(context);
    }

    /**
     * 获取监控上下文
     * @return
     */
    public static MonitorContext getContext() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 清除监控上下文
     */
    public static void clearContext() {
        CONTEXT_HOLDER.remove();
    }
}
