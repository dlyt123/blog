package com.back.backeddemo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查（公开，供阿里云云监控 / UptimeRobot 等探活使用）。
 *
 * <p>返回 200 表示服务可用；同时探测数据库连通性。
 * 注意：这里刻意不套 {@code Result} 包装，方便监控平台直接读字段。
 */
@RestController
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "UP");
        m.put("app", "blog");
        m.put("time", LocalDateTime.now().toString());
        try (Connection c = dataSource.getConnection()) {
            m.put("db", c.isValid(2) ? "UP" : "DOWN");
        } catch (Exception e) {
            m.put("db", "DOWN");
            m.put("status", "DEGRADED");
        }
        return m;
    }
}
