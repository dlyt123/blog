package com.back.backeddemo.controller;

import com.back.backeddemo.common.PageQuery;
import com.back.backeddemo.common.Result;
import com.back.backeddemo.entity.SensitiveLog;
import com.back.backeddemo.mapper.SensitiveLogMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 敏感词命中记录（仅管理员，路径在 WebConfig 的 AdminOnlyInterceptor 里登记）
 */
@RestController
@RequestMapping("/api/admin/sensitive-logs")
public class SensitiveLogController {

    private final SensitiveLogMapper mapper;

    public SensitiveLogController(SensitiveLogMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int pageSize) {
        PageQuery pq = PageQuery.of(page, pageSize);
        List<SensitiveLog> list = mapper.list(pq.offset(), pq.size());
        return Result.success(Map.of(
                "list", list,
                "total", mapper.count()
        ));
    }

    @DeleteMapping
    public Result<Void> clear() {
        mapper.clear();
        return Result.success();
    }
}
