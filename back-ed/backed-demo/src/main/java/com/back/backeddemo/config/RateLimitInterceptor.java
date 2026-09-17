package com.back.backeddemo.config;

import com.back.backeddemo.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简易限流拦截器（单机内存版，够个人博客用）
 *
 * 目的：防止
 *  - 登录接口被暴力破解
 *  - 注册接口被批量刷号
 *  - 评论 / 点赞 / 上传被刷
 *
 * 说明：
 *  - 只统计「写」请求（GET / OPTIONS 直接放行）
 *  - 采用滑动窗口计数，同一 IP 命中规则后超过阈值返回 429
 *  - 多实例部署时彼此独立，如需精确限流请换 Redis 方案
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    /** 规则：路径前缀 -> 窗口内允许次数 / 窗口秒数 */
    private record Rule(String prefix, int limit, int windowSeconds) {}

    private static final List<Rule> RULES = List.of(
            new Rule("/api/auth/login", 10, 300),      // 5 分钟内最多 10 次登录尝试
            new Rule("/api/auth/register", 5, 3600),   // 1 小时内最多注册 5 个账号
            new Rule("/api/posts/", 30, 60),           // 评论 / 点赞 / 收藏：1 分钟最多 30 次
            new Rule("/api/admin/media", 30, 600),     // 上传：10 分钟最多 30 张
            new Rule("/api/visit", 120, 60)            // 访客留痕上报：1 分钟最多 120 次（正常浏览足够）
    );

    private static final int MAX_KEYS = 10000;

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    @Value("${blog.rate-limit.enabled:true}")
    private boolean enabled;

    /** 是否信任反向代理传来的 X-Forwarded-For / X-Real-IP（Nginx 部署时需要开启） */
    @Value("${blog.rate-limit.trust-proxy:true}")
    private boolean trustProxy;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!enabled) {
            return true;
        }
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        String path = request.getRequestURI();
        for (Rule rule : RULES) {
            if (path.startsWith(rule.prefix())) {
                String key = clientIp(request) + "|" + rule.prefix();
                if (!allow(key, rule.limit(), rule.windowSeconds())) {
                    throw new BusinessException(429, "操作太频繁了，请稍后再试");
                }
                break;
            }
        }
        return true;
    }

    /** 滑动窗口计数 */
    private boolean allow(String key, int limit, int windowSeconds) {
        long now = System.currentTimeMillis();
        long from = now - windowSeconds * 1000L;
        if (hits.size() > MAX_KEYS) {
            hits.clear(); // 兜底，避免 key 无限增长
        }
        Deque<Long> queue = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst() < from) {
                queue.pollFirst();
            }
            if (queue.size() >= limit) {
                return false;
            }
            queue.addLast(now);
            return true;
        }
    }

    /**
     * 取客户端真实 IP。
     * 部署在 Nginx 后面时，request.getRemoteAddr() 拿到的是代理 IP，
     * 所有用户会共用一个计数桶，因此优先取代理透传的头部。
     */
    private String clientIp(HttpServletRequest request) {
        if (trustProxy) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                int comma = xff.indexOf(',');
                return (comma > 0 ? xff.substring(0, comma) : xff).trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
