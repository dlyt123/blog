package com.back.backeddemo.controller;

import com.back.backeddemo.common.Result;
import com.back.backeddemo.service.InteractionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 点赞 / 收藏接口。
 *
 * <p>业务逻辑在 {@link InteractionService} —— 点一次赞要写两张表
 * （post_like 插记录 + post.likes 计数），必须在事务里。
 * 这里只负责取当前用户、拼返回值。
 */
@RestController
@RequestMapping("/api/posts/{id}")
public class InteractionController {

    private final InteractionService interactionService;

    public InteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    // ==================== 点赞 ====================

    /** 点赞（幂等：一个用户只能点一次，重复点击不报错也不重复计数） */
    @PostMapping("/like")
    public Result<Map<String, Object>> like(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "请先登录后再点赞");
        }
        return Result.success(Map.of("liked", true, "likes", interactionService.like(userId, id)));
    }

    /** 取消点赞（幂等） */
    @DeleteMapping("/like")
    public Result<Map<String, Object>> unlike(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.success(Map.of("liked", false, "likes", interactionService.likeCount(id)));
        }
        return Result.success(Map.of("liked", false, "likes", interactionService.unlike(userId, id)));
    }

    /** 查询当前用户对该文章的点赞状态（刷新页面后据此回显） */
    @GetMapping("/like/status")
    public Result<Map<String, Object>> likeStatus(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(Map.of(
                "liked", interactionService.isLiked(userId, id),
                "likes", interactionService.likeCount(id)
        ));
    }

    // ==================== 收藏 ====================

    /** 收藏（幂等） */
    @PostMapping("/favorite")
    public Result<Map<String, Object>> favorite(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "请先登录后再收藏");
        }
        return Result.success(Map.of("favorited", true, "favorites", interactionService.favorite(userId, id)));
    }

    /** 取消收藏（幂等） */
    @DeleteMapping("/favorite")
    public Result<Map<String, Object>> unfavorite(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.success(Map.of("favorited", false, "favorites", interactionService.favoriteCount(id)));
        }
        return Result.success(Map.of("favorited", false, "favorites", interactionService.unfavorite(userId, id)));
    }

    /** 查询当前用户对该文章的收藏状态 */
    @GetMapping("/favorite/status")
    public Result<Map<String, Object>> favoriteStatus(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(Map.of(
                "favorited", interactionService.isFavorited(userId, id),
                "favorites", interactionService.favoriteCount(id)
        ));
    }
}
