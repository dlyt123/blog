package com.back.backeddemo.controller;

import com.back.backeddemo.common.BusinessException;
import com.back.backeddemo.common.Result;
import com.back.backeddemo.entity.Report;
import com.back.backeddemo.mapper.ReportMapper;
import com.back.backeddemo.service.SensitiveWordService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 内容举报
 * - POST /api/reports                    提交举报（需登录）
 * - GET  /api/admin/reports              举报列表（仅管理员）
 * - PUT  /api/admin/reports/{id}/handle  标记已处理（仅管理员）
 */
@RestController
public class ReportController {

    private final ReportMapper reportMapper;
    private final SensitiveWordService sensitiveWordService;

    public ReportController(ReportMapper reportMapper, SensitiveWordService sensitiveWordService) {
        this.reportMapper = reportMapper;
        this.sensitiveWordService = sensitiveWordService;
    }

    /** 提交举报 */
    @PostMapping("/api/reports")
    public Result<Void> submit(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String targetType = body == null ? null : (String) body.get("targetType");
        Object targetId = body == null ? null : body.get("targetId");
        String reason = body == null ? null : (String) body.get("reason");

        if (targetType == null || (!"post".equals(targetType) && !"comment".equals(targetType))) {
            throw new BusinessException(400, "举报对象类型不正确");
        }
        if (targetId == null) {
            throw new BusinessException(400, "缺少被举报对象");
        }
        if (reason != null && reason.length() > 200) {
            reason = reason.substring(0, 200);
        }
        // 举报理由也是用户输入的文字，同样过敏感词
        sensitiveWordService.validate(reason, "提交举报");

        Report r = new Report();
        r.setTargetType(targetType);
        r.setTargetId(Long.parseLong(String.valueOf(targetId)));
        r.setReason(reason);
        r.setUserId((Long) request.getAttribute("userId"));
        reportMapper.insert(r);
        return Result.success();
    }

    /** 举报列表（管理员） */
    @GetMapping("/api/admin/reports")
    public Result<List<Report>> list(@RequestParam(required = false) Integer status) {
        return Result.success(reportMapper.list(status));
    }

    /** 标记已处理（管理员） */
    @PutMapping("/api/admin/reports/{id}/handle")
    public Result<Void> handle(@PathVariable Long id) {
        reportMapper.updateStatus(id, 1);
        return Result.success();
    }
}
