package com.back.backeddemo.controller;

import com.back.backeddemo.common.BusinessException;
import com.back.backeddemo.common.Result;
import com.back.backeddemo.service.BackupService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据库备份（仅管理员）
 * - POST /api/admin/backup   立即备份
 * - GET  /api/admin/backup   备份文件列表
 */
@RestController
@RequestMapping("/api/admin/backup")
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @PostMapping
    public Result<Map<String, Object>> backupNow() {
        try {
            return Result.success(backupService.backupNow());
        } catch (Exception e) {
            throw new BusinessException(500, "备份失败：" + e.getMessage());
        }
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.success(backupService.listBackups());
    }
}
