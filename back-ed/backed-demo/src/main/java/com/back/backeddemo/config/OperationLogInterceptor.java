package com.back.backeddemo.config;

import com.back.backeddemo.common.ClientInfo;
import com.back.backeddemo.entity.OperationLog;
import com.back.backeddemo.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * 后台操作日志（审计）。
 *
 * <p>只记录 <b>非 GET</b> 的 /api/admin/** 请求 —— 也就是所有「会改变数据」的后台动作。
 * 这样不用在每个 Controller 里手动埋点，也能保证不漏记。
 *
 * <p>记录内容：谁（用户名/id）、做了什么（新增/修改/删除 + 对象）、什么时间、从哪个 IP、
 * 成功还是失败。用于事后追溯「这篇文章是谁删的」这类问题。
 */
@Component
public class OperationLogInterceptor implements HandlerInterceptor {

    /** 路径片段 → 中文名，让日志可读 */
    private static final Map<String, String> RESOURCE_NAMES = Map.ofEntries(
            Map.entry("posts", "文章"),
            Map.entry("categories", "分类"),
            Map.entry("tags", "标签"),
            Map.entry("comments", "评论"),
            Map.entry("links", "友链"),
            Map.entry("series", "系列"),
            Map.entry("settings", "站点设置"),
            Map.entry("users", "用户"),
            Map.entry("reports", "举报"),
            Map.entry("sensitive-words", "敏感词"),
            Map.entry("sensitive-logs", "敏感词命中记录"),
            Map.entry("subscribes", "邮件订阅"),
            Map.entry("visits", "访客记录"),
            Map.entry("media", "媒体库"),
            Map.entry("operation-logs", "操作日志"),
            Map.entry("backup", "数据库备份"),
            Map.entry("export", "数据导出")
    );

    private final OperationLogMapper operationLogMapper;

    @Value("${blog.rate-limit.trust-proxy:true}")
    private boolean trustProxy;

    public OperationLogInterceptor(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            String method = request.getMethod();
            if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
                return;
            }
            String path = request.getRequestURI();
            if (path == null || !path.startsWith("/api/admin/")) {
                return;
            }

            OperationLog log = new OperationLog();
            log.setUserId((Long) request.getAttribute("userId"));
            log.setUsername(ClientInfo.cut((String) request.getAttribute("username"), 64));
            log.setAction(resolveAction(method));
            log.setTarget(resolveTarget(path));
            log.setMethod(method);
            log.setPath(ClientInfo.cut(path, 200));
            log.setIp(ClientInfo.cut(ClientInfo.ip(request, trustProxy), 64));
            log.setSuccess(ex == null ? 1 : 0);
            operationLogMapper.insert(log);
        } catch (Exception ignore) {
            // 审计日志写失败绝不影响业务
        }
    }

    private String resolveAction(String method) {
        return switch (method.toUpperCase()) {
            case "POST" -> "新增";
            case "PUT", "PATCH" -> "修改";
            case "DELETE" -> "删除";
            default -> method;
        };
    }

    /** 从 /api/admin/posts/3/status 解析出「文章 #3」 */
    private String resolveTarget(String path) {
        String[] parts = path.split("/");
        // ["", "api", "admin", "posts", "3", "status"]
        if (parts.length < 4) {
            return path;
        }
        String resource = RESOURCE_NAMES.getOrDefault(parts[3], parts[3]);
        if (parts.length >= 5 && parts[4].matches("\\d+")) {
            return resource + " #" + parts[4];
        }
        return resource;
    }
}
