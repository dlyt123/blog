package com.back.backeddemo.controller;

import com.back.backeddemo.common.BusinessException;
import com.back.backeddemo.common.PageResult;
import com.back.backeddemo.common.Result;
import com.back.backeddemo.entity.Post;
import com.back.backeddemo.entity.User;
import com.back.backeddemo.mapper.PostFavoriteMapper;
import com.back.backeddemo.mapper.UserFollowMapper;
import com.back.backeddemo.mapper.UserMapper;
import com.back.backeddemo.service.PostService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户主页 / 关注 / 我的收藏
 *
 * - GET    /api/users/{id}              用户公开主页信息（无需登录）
 * - GET    /api/users/{id}/posts        该用户已发布的文章（无需登录）
 * - POST   /api/users/{id}/follow       关注（需登录）
 * - DELETE /api/users/{id}/follow       取消关注（需登录）
 * - GET    /api/users/me/favorites      我的收藏（需登录，路径已在 RequireLoginInterceptor 里登记）
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserMapper userMapper;
    private final UserFollowMapper userFollowMapper;
    private final PostFavoriteMapper postFavoriteMapper;
    private final PostService postService;

    public UserController(UserMapper userMapper,
                          UserFollowMapper userFollowMapper,
                          PostFavoriteMapper postFavoriteMapper,
                          PostService postService) {
        this.userMapper = userMapper;
        this.userFollowMapper = userFollowMapper;
        this.postFavoriteMapper = postFavoriteMapper;
        this.postService = postService;
    }

    /** 用户公开主页信息：只暴露昵称/头像等展示字段，**不返回邮箱和密码** */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> profile(@PathVariable Long id, HttpServletRequest request) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        Long me = currentUserId(request);

        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("id", user.getId());
        brief.put("username", user.getUsername());
        brief.put("nickname", user.getNickname() != null ? user.getNickname() : user.getUsername());
        brief.put("avatar", user.getAvatar());
        brief.put("role", user.getRole());
        brief.put("createTime", user.getCreateTime());
        data.put("user", brief);

        data.put("postCount", postService.listAdmin(1, null, id, 1, 1).getTotal());
        data.put("followerCount", userFollowMapper.countFollowers(id));
        data.put("followingCount", userFollowMapper.countFollowing(id));
        data.put("isSelf", me != null && me.equals(id));
        data.put("isFollowing", me != null && !me.equals(id)
                && userFollowMapper.exists(me, id) > 0);
        return Result.success(data);
    }

    /** 该用户已发布的文章（分页） */
    @GetMapping("/{id}/posts")
    public Result<PageResult<Post>> posts(@PathVariable Long id,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int pageSize) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        int size = Math.min(Math.max(pageSize, 1), 50);
        return Result.success(postService.listAdmin(1, null, id, Math.max(page, 1), size));
    }

    /** 关注 */
    @PostMapping("/{id}/follow")
    public Result<Map<String, Object>> follow(@PathVariable Long id, HttpServletRequest request) {
        Long me = requireLogin(request);
        if (me.equals(id)) {
            throw new BusinessException(400, "不能关注自己");
        }
        if (userMapper.findById(id) == null) {
            throw new BusinessException(404, "用户不存在");
        }
        userFollowMapper.insert(me, id);
        return Result.success(Map.of("following", true,
                "followerCount", userFollowMapper.countFollowers(id)));
    }

    /** 取消关注 */
    @DeleteMapping("/{id}/follow")
    public Result<Map<String, Object>> unfollow(@PathVariable Long id, HttpServletRequest request) {
        Long me = requireLogin(request);
        userFollowMapper.delete(me, id);
        return Result.success(Map.of("following", false,
                "followerCount", userFollowMapper.countFollowers(id)));
    }

    /** 我的收藏（分页） */
    @GetMapping("/me/favorites")
    public Result<PageResult<Post>> myFavorites(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int pageSize,
                                                HttpServletRequest request) {
        Long me = requireLogin(request);
        int p = Math.max(page, 1);
        int size = Math.min(Math.max(pageSize, 1), 50);
        List<Post> list = postFavoriteMapper.listByUser(me, (p - 1) * size, size);

        PageResult<Post> result = new PageResult<>();
        result.setList(list == null ? new ArrayList<>() : list);
        result.setTotal(postFavoriteMapper.countActiveByUser(me));
        result.setPage(p);
        result.setPageSize(size);
        return Result.success(result);
    }

    /** 关注流：我关注的人发布的文章（分页） */
    @GetMapping("/me/following/posts")
    public Result<PageResult<Post>> followingPosts(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int pageSize,
                                                   HttpServletRequest request) {
        Long me = requireLogin(request);
        int p = Math.max(page, 1);
        int size = Math.min(Math.max(pageSize, 1), 50);
        return Result.success(postService.listByFollowees(me, p, size));
    }

    // ===== 辅助 =====

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    private Long requireLogin(HttpServletRequest request) {
        Long me = currentUserId(request);
        if (me == null) {
            throw new BusinessException(401, "请先登录后再操作");
        }
        return me;
    }
}
