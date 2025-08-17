package com.wgz.aikir.rateLimiter.aspect;

import com.wgz.aikir.exception.BusinessException;
import com.wgz.aikir.exception.ErrorCode;
import com.wgz.aikir.model.entity.User;
import com.wgz.aikir.rateLimiter.annotation.RateLimit;
import com.wgz.aikir.rateLimiter.enums.RateLimitType;
import com.wgz.aikir.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

@Aspect
@Component
@Slf4j
public class RateLimitAspect {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private UserService userService;

    @Before("@annotation(rateLimit)")
    public void doBefore(JoinPoint point, RateLimit rateLimit) {
        // 拼接缓存件
        String key = generateRateLimitKey(point, rateLimit);
        // 获取限流器
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        // 初始化限流器
        rateLimiter.trySetRate(RateType.OVERALL, rateLimit.rate(), rateLimit.rateInterval(), RateIntervalUnit.SECONDS);
        // 获取限流令牌
        boolean result = rateLimiter.tryAcquire(1);
        if (!result) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST, rateLimit.message());
        }
    }

    /**
     * 生成限流 key
     * @param point
     * @param rateLimit
     * @return
     */
    private String generateRateLimitKey(JoinPoint point, RateLimit rateLimit) {
        // 自定义限流 key 拼接器
        StringBuilder rateLimitKeyBuilder = new StringBuilder();
        rateLimitKeyBuilder.append("rate_limit:");
        // 拼接key
        if (rateLimit.key() != null && !rateLimit.key().isEmpty()) {
            rateLimitKeyBuilder.append(rateLimit.key()).append(":");
        }
        // 根据限流类型区分限流key
        switch (rateLimit.limitType()) {
            case API -> {
                MethodSignature signature = (MethodSignature) point.getSignature();
                Method method = signature.getMethod();
                rateLimitKeyBuilder.append("API:").append(method.getDeclaringClass().getSimpleName())
                        .append(".").append(method.getName());
                break;
            }
            case USER -> {
                try {
                    ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                    if (attributes != null) {
                        HttpServletRequest request = attributes.getRequest();
                        User loginUser = userService.getLoginUser(request);
                        rateLimitKeyBuilder.append("USER:").append(loginUser.getId());
                    } else {
                        // 无法获取请求上下文，使用IP限流
                        rateLimitKeyBuilder.append("IP:").append(getClientIP());
                    }
                } catch (Exception e) {
                    // 未登录用户使用IP限流
                    rateLimitKeyBuilder.append("IP:").append(getClientIP());
                }
                break;
            }
            case IP -> {
                rateLimitKeyBuilder.append("IP:").append(getClientIP());
                break;
            }
        }
        return rateLimitKeyBuilder.toString();
    }

    private String getClientIP() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理多级代理的情况
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }

}
