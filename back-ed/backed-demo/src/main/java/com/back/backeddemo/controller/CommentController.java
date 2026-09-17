package com.back.backeddemo.controller;

import com.back.backeddemo.common.PageResult;
import com.back.backeddemo.common.Result;
import com.back.backeddemo.entity.Comment;
import com.back.backeddemo.service.CommentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /** 前台评论列表（顶层分页） */
    @GetMapping("/api/posts/{id}/comments")
    public Result<PageResult<Comment>> list(@PathVariable Long id,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 50);
        return Result.success(commentService.listByPost(id, page, size));
    }

    /** 最新评论（公开，首页侧边栏用） */
    @GetMapping("/api/comments/recent")
    public Result<List<Comment>> recent(@RequestParam(defaultValue = "5") int limit) {
        return Result.success(commentService.listRecent(Math.min(Math.max(limit, 1), 20)));
    }

    /** 发表评论（昵称、头像自动取自登录用户） */
    @PostMapping("/api/posts/{id}/comments")
    public Result<Void> add(@PathVariable Long id, @RequestBody Comment comment, HttpServletRequest request) {
        comment.setPostId(id);
        Long userId = (Long) request.getAttribute("userId");
        commentService.add(comment, userId);
        return Result.success();
    }

    /** 后台评论列表 */
    @GetMapping("/api/admin/comments")
    public Result<List<Comment>> listAdmin(@RequestParam(required = false) Integer status) {
        return Result.success(commentService.listAdmin(status));
    }

    /** 审核评论 */
    @PostMapping("/api/admin/comments/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        commentService.audit(id, body.get("status"));
        return Result.success();
    }

    /** 删除评论 */
    @DeleteMapping("/api/admin/comments/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return Result.success();
    }
}
