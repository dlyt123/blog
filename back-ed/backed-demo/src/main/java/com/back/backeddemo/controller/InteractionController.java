package com.back.backeddemo.controller;

import com.back.backeddemo.common.Result;
import com.back.backeddemo.entity.Post;
import com.back.backeddemo.mapper.PostFavoriteMapper;
import com.back.backeddemo.mapper.PostLikeMapper;
import com.back.backeddemo.mapper.PostMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/posts/{id}")
public class InteractionController {

    private final PostMapper postMapper;
    private final PostLikeMapper postLikeMapper;
    private final PostFavoriteMapper postFavoriteMapper;

    public InteractionController(PostMapper postMapper,
                                 PostLikeMapper postLikeMapper,
                                 PostFavoriteMapper postFavoriteMapper) {
        this.postMapper = postMapper;
        this.postLikeMapper = postLikeMapper;
        this.postFavoriteMapper = postFavoriteMapper;
    }

    // ==================== 点赞 ====================

    /** 点赞（幂等：一个用户只能点一次，重复点击不报错也不重复计数） */
    @PostMapping("/like")
    public Result<Map<String, Object>> like(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "请先登录后再点赞");
        }
        if (postLikeMapper.exists(userId, id) == 0) {
            postLikeMapper.insert(userId, id);
            postMapper.incrementLikes(id);
        }
        return Result.success(Map.of("liked", true, "likes", currentLikes(id)));
    }

    /** 取消点赞（幂等） */
    @DeleteMapping("/like")
    public Result<Map<String, Object>> unlike(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId != null && postLikeMapper.exists(userId, id) > 0) {
            postLikeMapper.delete(userId, id);
            postMapper.decrementLikes(id);
        }
        return Result.success(Map.of("liked", false, "likes", currentLikes(id)));
    }

    /** 查询当前用户对该文章的点赞状态（刷新页面后据此回显） */
    @GetMapping("/like/status")
    public Result<Map<String, Object>> likeStatus(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        boolean liked = userId != null && postLikeMapper.exists(userId, id) > 0;
        return Result.success(Map.of("liked", liked, "likes", currentLikes(id)));
    }

    // ==================== 收藏 ====================

    /** 收藏（幂等） */
    @PostMapping("/favorite")
    public Result<Map<String, Object>> favorite(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "请先登录后再收藏");
        }
        if (postFavoriteMapper.exists(userId, id) == 0) {
            postFavoriteMapper.insert(userId, id);
        }
        return Result.success(Map.of("favorited", true, "favorites", postFavoriteMapper.countByPost(id)));
    }

    /** 取消收藏（幂等） */
    @DeleteMapping("/favorite")
    public Result<Map<String, Object>> unfavorite(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId != null && postFavoriteMapper.exists(userId, id) > 0) {
            postFavoriteMapper.delete(userId, id);
        }
        return Result.success(Map.of("favorited", false, "favorites", postFavoriteMapper.countByPost(id)));
    }

    /** 查询当前用户对该文章的收藏状态 */
    @GetMapping("/favorite/status")
    public Result<Map<String, Object>> favoriteStatus(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        boolean favorited = userId != null && postFavoriteMapper.exists(userId, id) > 0;
        return Result.success(Map.of("favorited", favorited, "favorites", postFavoriteMapper.countByPost(id)));
    }

    /** 取文章当前的点赞总数（以 post.likes 为准，保留种子数据的初始值） */
    private int currentLikes(Long id) {
        Post p = postMapper.findById(id);
        return (p != null && p.getLikes() != null) ? p.getLikes() : 0;
    }
}
