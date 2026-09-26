package com.back.backeddemo.controller;

import com.back.backeddemo.common.PageQuery;
import com.back.backeddemo.common.Result;
import com.back.backeddemo.entity.OperationLog;
import com.back.backeddemo.mapper.OperationLogMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 操作日志 / 审计（仅管理员）
 * - GET    /api/admin/operation-logs   列表（支持关键字、分页）
 * - DELETE /api/admin/operation-logs   清空
 */
@RestController
@RequestMapping("/api/admin/operation-logs")
public class OperationLogController {

    private final OperationLogMapper mapper;

    public OperationLogController(OperationLogMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int pageSize,
                                            @RequestParam(required = false) String keyword) {
        PageQuery pq = PageQuery.of(page, pageSize);
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        List<OperationLog> list = mapper.list(kw, pq.offset(), pq.size());
        return Result.success(Map.of(
                "list", list,
                "total", mapper.count(kw)
        ));
    }

    @DeleteMapping
    public Result<Void> clear() {
        mapper.clear();
        return Result.success();
    }
}
