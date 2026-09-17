package com.back.backeddemo.controller;

import com.back.backeddemo.common.Result;
import com.back.backeddemo.entity.Subscribe;
import com.back.backeddemo.mapper.SubscribeMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 邮件订阅管理（管理员专属）
 * 前台提交订阅的接口在 CommonController 的 POST /api/subscribe（允许访客调用）
 */
@RestController
@RequestMapping("/api/admin/subscribes")
public class SubscribeController {

    private final SubscribeMapper subscribeMapper;

    public SubscribeController(SubscribeMapper subscribeMapper) {
        this.subscribeMapper = subscribeMapper;
    }

    @GetMapping
    public Result<List<Subscribe>> list() {
        return Result.success(subscribeMapper.list());
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        subscribeMapper.delete(id);
        return Result.success();
    }
}
