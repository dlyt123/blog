package com.back.backeddemo.controller;

import com.back.backeddemo.common.ClientInfo;
import com.back.backeddemo.common.Result;
import com.back.backeddemo.entity.VisitLog;
import com.back.backeddemo.mapper.VisitLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 访客留痕
 *
 * - POST /api/visit                 前台每次进入页面时上报（**无需登录**，游客也要记录）
 * - GET  /api/admin/visits          后台查看访问记录（仅管理员）
 * - GET  /api/admin/visits/stats    后台概览统计（仅管理员）
 * - DELETE /api/admin/visits        清空访问记录（仅管理员）
 */
@RestController
public class VisitController {

    /** 单条 path / referer 的存储上限，防止超长字段 */
    private static final int MAX_PATH = 255;
    private static final int MAX_REFERER = 500;
    private static final int MAX_UA = 500;

    private final VisitLogMapper visitLogMapper;

    @Value("${blog.rate-limit.trust-proxy:true}")
    private boolean trustProxy;

    public VisitController(VisitLogMapper visitLogMapper) {
        this.visitLogMapper = visitLogMapper;
    }

    /** 前台页面访问上报（游客也会上报） */
    @PostMapping("/api/visit")
    public Result<Void> report(@RequestBody(required = false) Map<String, String> body,
                               HttpServletRequest request) {
        Map<String, String> data = body == null ? Map.of() : body;
        String ua = request.getHeader("User-Agent");

        VisitLog log = new VisitLog();
        Object userId = request.getAttribute("userId");
        if (userId instanceof Long id) {
            log.setUserId(id);
        }
        log.setIp(ClientInfo.ip(request, trustProxy));
        log.setPath(ClientInfo.cut(data.get("path"), MAX_PATH));
        log.setReferer(ClientInfo.cut(data.get("referer"), MAX_REFERER));
        log.setUserAgent(ClientInfo.cut(ua, MAX_UA));
        log.setDevice(ClientInfo.device(ua));

        try {
            visitLogMapper.insert(log);
        } catch (Exception e) {
            // 留痕失败不能影响用户正常浏览，静默处理
        }
        return Result.success();
    }

    /** 访问记录列表 */
    @GetMapping("/api/admin/visits")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "20") Integer pageSize,
                                            @RequestParam(required = false) String userType,
                                            @RequestParam(required = false) String keyword) {
        int p = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        String type = userType == null ? "all" : userType;
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        List<VisitLog> list = visitLogMapper.list(type, kw, (p - 1) * size, size);
        long total = visitLogMapper.count(type, kw);

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", p);
        data.put("pageSize", size);
        return Result.success(data);
    }

    /** 概览统计 */
    @GetMapping("/api/admin/visits/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> stats = visitLogMapper.stats();
        return Result.success(stats == null ? new HashMap<>() : stats);
    }

    /** 最近 N 天 PV / UV 趋势（补全无数据的日期，返回连续日期序列） */
    @GetMapping("/api/admin/visits/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "7") int days) {
        int d = Math.max(1, Math.min(days, 30));
        List<Map<String, Object>> raw = visitLogMapper.trend(d);

        Map<String, Map<String, Object>> byDate = new HashMap<>();
        for (Map<String, Object> row : raw) {
            byDate.put(String.valueOf(row.get("date")), row);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = d - 1; i >= 0; i--) {
            String key = today.minusDays(i).toString();
            Map<String, Object> row = byDate.get(key);
            if (row == null) {
                row = new HashMap<>();
                row.put("date", key);
                row.put("pv", 0L);
                row.put("uv", 0L);
            }
            result.add(row);
        }
        return Result.success(result);
    }

    /** 来源分析：按访问来源聚合（来源 / PV / UV） */
    @GetMapping("/api/admin/visits/referrers")
    public Result<List<Map<String, Object>>> referrers(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(visitLogMapper.referrerStats(Math.min(Math.max(limit, 1), 50)));
    }

    /** 清空全部访问记录 */
    @DeleteMapping("/api/admin/visits")
    public Result<Void> clear() {
        visitLogMapper.clearAll();
        return Result.success();
    }
}
