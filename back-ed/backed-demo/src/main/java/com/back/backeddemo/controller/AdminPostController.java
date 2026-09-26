package com.back.backeddemo.controller;

import com.back.backeddemo.config.AdminExempt;
import com.back.backeddemo.common.PageQuery;
import com.back.backeddemo.common.PageResult;
import com.back.backeddemo.common.Result;
import com.back.backeddemo.entity.Post;
import com.back.backeddemo.entity.User;
import com.back.backeddemo.mapper.UserMapper;
import com.back.backeddemo.service.PostService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 文章管理（后台）。
 *
 * <p>整类标 {@link AdminExempt}：这里**刻意**不要求调用者一定是管理员 ——
 * 博主也要能管理**自己的**文章。所以每个方法的鉴权是「管理员 / 本人」二选一，
 * 由本类内部用 {@code isAdmin()} + {@code currentUserId()} 判断，
 * 真正的删除 / 恢复 / 审核 / 加精等纯管理操作再单独调 {@code requireAdmin()}。
 *
 * <p>⚠️ 往这个类里加新接口时，必须自己想清楚鉴权条件，
 * 因为拦截器那一层已经因为 {@code @AdminExempt} 让开了。
 */
@RestController
@RequestMapping("/api/admin/posts")
@AdminExempt
public class AdminPostController {

    private final PostService postService;
    private final UserMapper userMapper;

    public AdminPostController(PostService postService, UserMapper userMapper) {
        this.postService = postService;
        this.userMapper = userMapper;
    }

    /** 后台文章列表（管理员看全部，博主只看自己的） */
    @GetMapping
    public Result<PageResult<Post>> list(@RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         HttpServletRequest request) {
        boolean admin = isAdmin(request);
        Long authorId = admin ? null : currentUserId(request);
        PageQuery pq = PageQuery.of(page, pageSize);
        return Result.success(postService.listAdmin(status, keyword, authorId, pq.page(), pq.size()));
    }

    /** 创建文章（作者固定为当前登录用户） */
    @PostMapping
    public Result<Map<String, Long>> create(@RequestBody Post post, HttpServletRequest request) {
        post.setAuthorId(currentUserId(request));
        postService.create(post, post.getTagIds(), isAdmin(request));
        return Result.success(Map.of("id", post.getId()));
    }

    /** 更新文章（非管理员只能改自己的） */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Post post, HttpServletRequest request) {
        post.setId(id);
        postService.update(post, post.getTagIds(), currentUserId(request), isAdmin(request));
        return Result.success();
    }

    /** 删除文章（软删除，非管理员只能删自己的） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        postService.delete(id, currentUserId(request), isAdmin(request));
        return Result.success();
    }

    /** 回收站列表（仅管理员） */
    @GetMapping("/trash")
    public Result<PageResult<Post>> trash(@RequestParam(required = false) String keyword,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int pageSize,
                                          HttpServletRequest request) {
        requireAdmin(request);
        PageQuery pq = PageQuery.of(page, pageSize);
        return Result.success(postService.listTrash(keyword, pq.page(), pq.size()));
    }

    /** 从回收站恢复（仅管理员） */
    @PostMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable Long id, HttpServletRequest request) {
        requireAdmin(request);
        postService.restore(id);
        return Result.success();
    }

    /** 彻底删除（仅管理员，不可恢复） */
    @DeleteMapping("/{id}/purge")
    public Result<Void> purge(@PathVariable Long id, HttpServletRequest request) {
        requireAdmin(request);
        postService.hardDelete(id);
        return Result.success();
    }

    /** 发布 / 下线 */
    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id, @RequestBody Map<String, Integer> body, HttpServletRequest request) {
        Integer status = body.get("status");
        postService.updateStatus(id, status, currentUserId(request), isAdmin(request));
        return Result.success();
    }

    /**
     * 审核文章（仅管理员）。
     * body: { "pass": true|false, "remark": "驳回理由（驳回时建议填）" }
     *
     * <p>通过 = 置为已发布并通知订阅者；驳回 = 退回草稿并把理由写给作者看。
     */
    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body,
                              HttpServletRequest request) {
        requireAdmin(request);
        Object passRaw = body == null ? null : body.get("pass");
        boolean pass = passRaw == null || Boolean.parseBoolean(String.valueOf(passRaw));
        String remark = body == null ? null : (String) body.get("remark");
        postService.audit(id, pass, remark);
        return Result.success();
    }

    /** 置顶 / 取消置顶（仅管理员） */
    @PostMapping("/{id}/pin")
    public Result<Void> pin(@PathVariable Long id, @RequestBody Map<String, Integer> body, HttpServletRequest request) {
        requireAdmin(request);
        postService.updatePinned(id, body.get("pinned"), currentUserId(request), true);
        return Result.success();
    }

    /** 推荐 / 取消推荐（仅管理员） */
    @PostMapping("/{id}/recommend")
    public Result<Void> recommend(@PathVariable Long id, @RequestBody Map<String, Integer> body, HttpServletRequest request) {
        requireAdmin(request);
        postService.updateRecommended(id, body.get("recommended"), currentUserId(request), true);
        return Result.success();
    }

    /** 批量操作（非管理员仅能操作自己的文章） */
    @PostMapping("/batch")
    public Result<Void> batch(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        List<Long> ids = parseIds(body.get("ids"));
        String action = (String) body.get("action");
        if (ids == null || ids.isEmpty()) {
            return Result.error(400, "ids 不能为空");
        }
        boolean admin = isAdmin(request);
        Long userId = currentUserId(request);
        switch (action == null ? "" : action) {
            case "delete" -> postService.batchSoftDelete(ids, userId, admin);
            case "publish" -> postService.batchUpdateStatus(ids, 1, userId, admin);
            case "offline" -> postService.batchUpdateStatus(ids, 0, userId, admin);
            default -> {
                if (body.get("categoryId") != null) {
                    postService.batchUpdateCategory(ids, Long.valueOf(body.get("categoryId").toString()), userId, admin);
                } else {
                    return Result.error(400, "未知的批量操作");
                }
            }
        }
        return Result.success();
    }

    // ===== 辅助方法 =====

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    private boolean isAdmin(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return false;
        }
        User u = userMapper.findById(userId);
        return u != null && "ADMIN".equals(u.getRole());
    }

    private void requireAdmin(HttpServletRequest request) {
        if (!isAdmin(request)) {
            throw new com.back.backeddemo.common.BusinessException(403, "仅管理员可执行此操作");
        }
    }

    /** 把 JSON 传来的 ids 归一化成 List<Long>（JSON 数字可能被反序列化成 Integer/Double） */
    private List<Long> parseIds(Object idsObj) {
        if (!(idsObj instanceof List<?> raw)) {
            return null;
        }
        List<Long> ids = new java.util.ArrayList<>();
        for (Object o : raw) {
            if (o == null) {
                continue;
            }
            if (o instanceof Number n) {
                ids.add(n.longValue());
            } else {
                try {
                    ids.add(Long.valueOf(o.toString().trim()));
                } catch (NumberFormatException ignored) {
                    // 跳过非法值
                }
            }
        }
        return ids;
    }
}