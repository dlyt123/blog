package com.back.backeddemo.controller;

import com.back.backeddemo.common.BusinessException;
import com.back.backeddemo.common.Result;
import com.back.backeddemo.entity.User;
import com.back.backeddemo.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台「用户管理」：封号 / 禁言 / 解封。
 *
 * <ul>
 *   <li>GET  /api/admin/users                  用户列表（含封禁状态）</li>
 *   <li>PUT  /api/admin/users/{id}/ban         封号 / 解封</li>
 *   <li>PUT  /api/admin/users/{id}/mute        禁言 / 解除（按分钟数）</li>
 * </ul>
 *
 * <p>⚠️ 这里<b>手动挑字段</b>返回，不直接丢 User 实体 ✗ ——
 * 实体里有 password（BCrypt 哈希），直接序列化会把口令哈希暴露给前端 ✗
 *
 * <p>另外做了两个保护 ✓：
 * <ul>
 *   <li>管理员不能封禁 / 禁言<b>其他管理员</b> ✗（普通用户之间才允许）</li>
 *   <li>管理员也不能封自己 ✗ —— 否则会把自己锁在门外 ✓</li>
 * </ul>
 * 生效逻辑在 {@code UserStatusInterceptor}：封号后写操作一律 403、也无法再登录。
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserController.class);

    /** 禁言时长上限（天）—— 防止手滑输入一个天文数字 */
    private static final int MAX_MUTE_MINUTES = 60 * 24 * 365;

    private final UserMapper userMapper;

    public AdminUserController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 用户列表 */
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        List<User> users = userMapper.listAll();
        if (users != null) {
            for (User u : users) {
                out.add(view(u));
            }
        }
        return Result.success(out);
    }

    /**
     * 封号 / 解封。
     * body: { "banned": 1|0, "reason": "可选" }
     */
    @PutMapping("/{id}/ban")
    public Result<Void> ban(@PathVariable Long id, @RequestBody Map<String, Object> body,
                            HttpServletRequest request) {
        User target = requireTarget(id, request);
        int banned = toInt(body == null ? null : body.get("banned"), 1) == 0 ? 0 : 1;
        String reason = trim(body == null ? null : (String) body.get("reason"), 200);

        if (banned == 1 && reason == null) {
            reason = "违反社区规范";
        }
        userMapper.updateBanned(id, banned, reason);
        log.info("[封禁] 管理员 {} {} 用户 {}（{}）",
                request.getAttribute("userId"), banned == 1 ? "封禁" : "解封",
                target.getUsername(), reason);
        return Result.success();
    }

    /**
     * 禁言 / 解除。
     * body: { "minutes": 60, "reason": "可选" } —— minutes 传 0 表示解除禁言。
     */
    @PutMapping("/{id}/mute")
    public Result<Void> mute(@PathVariable Long id, @RequestBody Map<String, Object> body,
                             HttpServletRequest request) {
        User target = requireTarget(id, request);
        int minutes = toInt(body == null ? null : body.get("minutes"), 0);
        if (minutes < 0) {
            minutes = 0;
        }
        if (minutes > MAX_MUTE_MINUTES) {
            minutes = MAX_MUTE_MINUTES;
        }
        String reason = trim(body == null ? null : (String) body.get("reason"), 200);

        LocalDateTime until = minutes == 0 ? null : LocalDateTime.now().plusMinutes(minutes);
        if (until == null) {
            reason = null;      // 解除禁言时把原因也清掉，避免留着让人误会
        } else if (reason == null) {
            reason = "违反社区规范";
        }
        userMapper.updateMuted(id, until, reason);
        log.info("[禁言] 管理员 {} {} 用户 {}（{} 分钟）",
                request.getAttribute("userId"), minutes == 0 ? "解除" : "禁言",
                target.getUsername(), minutes);
        return Result.success();
    }

    /**
     * 取出目标用户并做两项保护：不能操作管理员、不能操作自己。
     */
    private User requireTarget(Long id, HttpServletRequest request) {
        if (id == null) {
            throw new BusinessException(400, "缺少用户 ID");
        }
        Long me = (Long) request.getAttribute("userId");
        if (id.equals(me)) {
            throw new BusinessException(400, "不能对自己执行封禁或禁言");
        }
        User target = userMapper.findById(id);
        if (target == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if ("ADMIN".equals(target.getRole())) {
            throw new BusinessException(403, "不能封禁或禁言管理员账号");
        }
        return target;
    }

    /** 只挑安全的字段返回（绝不含 password） */
    private Map<String, Object> view(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("nickname", u.getNickname());
        m.put("avatar", u.getAvatar());
        m.put("email", u.getEmail());
        m.put("role", u.getRole());
        m.put("createTime", u.getCreateTime() == null ? null : u.getCreateTime().toString());
        m.put("banned", u.bannedNow());
        m.put("muted", u.mutedNow());
        m.put("mutedUntil", u.getMutedUntil() == null ? null : u.getMutedUntil().toString());
        m.put("banReason", u.getBanReason());
        return m;
    }

    private static int toInt(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > max ? t.substring(0, max) : t;
    }
}
