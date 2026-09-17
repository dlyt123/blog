package com.back.backeddemo.service;

import com.back.backeddemo.common.BusinessException;
import com.back.backeddemo.common.CaptchaStore;
import com.back.backeddemo.common.LoginAttemptService;
import com.back.backeddemo.config.JwtUtil;
import com.back.backeddemo.entity.Setting;
import com.back.backeddemo.entity.User;
import com.back.backeddemo.mapper.SettingMapper;
import com.back.backeddemo.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 重置链接有效期（分钟） */
    private static final int RESET_EXPIRE_MINUTES = 30;

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final CaptchaStore captchaStore;
    private final LoginAttemptService loginAttemptService;
    private final MailService mailService;
    private final SettingMapper settingMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Value("${blog.site-url:http://localhost:8080}")
    private String defaultSiteUrl;

    /**
     * 是否启用图形验证码。
     * <p>⚠️ 仅供本地联调 / 自动化测试临时关闭，**生产环境必须保持 true**，
     * 否则登录接口就少了防脚本撞库的一层。
     */
    @Value("${blog.captcha.enabled:true}")
    private boolean captchaEnabled;

    public AuthService(UserMapper userMapper, JwtUtil jwtUtil,
                       CaptchaStore captchaStore, LoginAttemptService loginAttemptService,
                       MailService mailService, SettingMapper settingMapper) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.captchaStore = captchaStore;
        this.loginAttemptService = loginAttemptService;
        this.mailService = mailService;
        this.settingMapper = settingMapper;
    }

    /**
     * 登录。
     * 三道防线：① 图形验证码 → ② 账号锁定检查 → ③ 密码校验（失败计数）
     */
    public Map<String, Object> login(String username, String password,
                                     String captchaKey, String captchaCode) {
        // ① 验证码（一次性消费）
        if (captchaEnabled && !captchaStore.verify(captchaKey, captchaCode)) {
            throw new BusinessException(400, "验证码错误或已过期，请重新输入");
        }
        // ② 账号是否处于锁定期
        long lockedMinutes = loginAttemptService.lockedMinutes(username);
        if (lockedMinutes > 0) {
            throw new BusinessException(429,
                    "失败次数过多，账号已锁定，请 " + lockedMinutes + " 分钟后再试");
        }
        // ③ 密码校验
        User user = userMapper.findByUsername(username == null ? null : username.trim());
        if (user == null || !encoder.matches(password, user.getPassword())) {
            loginAttemptService.recordFailure(username);
            // 这一次失败可能刚好触发锁定，要按锁定提示，不能再说「还可尝试 N 次」
            long lockedAfter = loginAttemptService.lockedMinutes(username);
            if (lockedAfter > 0) {
                throw new BusinessException(429,
                        "失败次数过多，账号已锁定，请 " + lockedAfter + " 分钟后再试");
            }
            throw new BusinessException(401,
                    "用户名或密码错误，还可尝试 " + loginAttemptService.remaining(username) + " 次");
        }
        loginAttemptService.clear(username);
        return buildLoginResult(user);
    }

    /**
     * 注册：普通用户即博主（role=USER），注册成功后自动登录。
     */
    public Map<String, Object> register(String username, String password, String nickname, String email) {
        if (username == null || username.trim().length() < 3) {
            throw new BusinessException(400, "用户名至少 3 个字符");
        }
        if (password == null || password.length() < 6) {
            throw new BusinessException(400, "密码至少 6 位");
        }
        if (userMapper.findByUsername(username.trim()) != null) {
            throw new BusinessException(400, "用户名已被占用");
        }
        User user = new User();
        user.setUsername(username.trim());
        user.setPassword(encoder.encode(password));
        user.setNickname(nickname != null && !nickname.trim().isEmpty() ? nickname.trim() : username.trim());
        user.setEmail(email);
        user.setAvatar("");
        // 普通用户 = 博主，仅能管理自己的文章
        user.setRole("USER");
        userMapper.insert(user);
        return buildLoginResult(user);
    }

    public User getMe(Long userId) {
        return safe(userMapper.findById(userId));
    }

    /** 返回给前端之前清掉密码哈希，避免敏感字段被序列化出去 */
    private User safe(User user) {
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }

    public User updateProfile(Long userId, User user) {
        user.setId(userId);
        // 密码单独处理，空密码不更新
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            if (user.getPassword().length() < 6) {
                throw new BusinessException(400, "密码至少 6 位");
            }
            user.setPassword(encoder.encode(user.getPassword()));
        } else {
            user.setPassword(null);
        }
        // 防止通过资料更新接口篡改角色与用户名
        user.setRole(null);
        user.setUsername(null);

        // 一个字段都没改就别往下走了：Mapper 的动态 SET 会拼成
        // "UPDATE user WHERE id=?"，直接触发 SQL 语法错误（500）。
        if (user.getNickname() == null && user.getEmail() == null
                && user.getAvatar() == null && user.getPassword() == null) {
            throw new BusinessException(400, "没有需要修改的内容");
        }

        userMapper.update(user);
        return safe(userMapper.findById(userId));
    }

    /**
     * 忘记密码：按邮箱生成重置链接并发送。
     *
     * <p>安全考虑：**无论邮箱是否注册过都返回成功**，
     * 否则可以拿这个接口探测「哪些邮箱在这站注册过」。
     */
    public void forgotPassword(String email) {
        if (email == null || email.isBlank()) {
            throw new BusinessException(400, "请输入邮箱");
        }
        User user = userMapper.findByEmail(email.trim());
        if (user == null) {
            log.info("[找回密码] 邮箱 {} 未注册，静默忽略", email);
            return;
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        userMapper.saveResetToken(user.getId(), token,
                LocalDateTime.now().plusMinutes(RESET_EXPIRE_MINUTES));

        String link = siteUrl() + "/reset-password?token=" + token;
        mailService.sendText(email.trim(), "重置密码 - " + siteName(),
                "你好 " + (user.getNickname() == null ? user.getUsername() : user.getNickname()) + "：\n\n"
                        + "点下面的链接重置密码（" + RESET_EXPIRE_MINUTES + " 分钟内有效）：\n"
                        + link + "\n\n"
                        + "如果这不是你本人的操作，忽略这封邮件即可，你的密码不会被修改。");
    }

    /** 重置密码：校验令牌有效性后覆盖密码 */
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(400, "链接无效");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException(400, "密码至少 6 位");
        }
        User user = userMapper.findByResetToken(token.trim());
        if (user == null) {
            throw new BusinessException(400, "链接无效或已过期，请重新申请");
        }
        userMapper.resetPassword(user.getId(), encoder.encode(newPassword));
        // 重置成功顺手解除登录锁定，避免用户改完密码还是进不去
        loginAttemptService.clear(user.getUsername());
    }

    private String siteUrl() {
        Setting s = settingMapper.findByKey("siteUrl");
        String v = s == null ? null : s.getSettingValue();
        String url = (v == null || v.isBlank()) ? defaultSiteUrl : v;
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String siteName() {
        Setting s = settingMapper.findByKey("siteName");
        String v = s == null ? null : s.getSettingValue();
        return (v == null || v.isBlank()) ? "我的博客" : v;
    }

    private Map<String, Object> buildLoginResult(User user) {
        String token = jwtUtil.generateToken(user.getUsername(), user.getId());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("role", user.getRole());
        return data;
    }
}
