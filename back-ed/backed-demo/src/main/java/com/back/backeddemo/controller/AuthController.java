package com.back.backeddemo.controller;

import com.back.backeddemo.common.Result;
import com.back.backeddemo.entity.User;
import com.back.backeddemo.service.AccountService;
import com.back.backeddemo.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AccountService accountService;

    public AuthController(AuthService authService, AccountService accountService) {
        this.authService = authService;
        this.accountService = accountService;
    }

    /** 登录（需图形验证码） */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) {
            return Result.error(400, "用户名和密码不能为空");
        }
        return Result.success(authService.login(username, password,
                body.get("captchaKey"), body.get("captchaCode")));
    }

    /** 注册（普通用户即博主，注册成功自动登录） */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            return Result.error(400, "用户名和密码不能为空");
        }
        return Result.success(authService.register(username, password, body.get("nickname"), body.get("email")));
    }

    /** 忘记密码：发重置邮件（无论邮箱是否存在都返回成功，防止探测注册邮箱） */
    @PostMapping("/forgot-password")
    public Result<Void> forgotPassword(@RequestBody Map<String, String> body) {
        authService.forgotPassword(body.get("email"));
        return Result.success();
    }

    /** 重置密码 */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@RequestBody Map<String, String> body) {
        authService.resetPassword(body.get("token"), body.get("password"));
        return Result.success();
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        // 无状态 JWT，前端删除 token 即可
        return Result.success();
    }

    @GetMapping("/me")
    public Result<User> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(authService.getMe(userId));
    }

    /** 更新个人资料（昵称、头像、邮箱，也支持改密码） */
    @PutMapping("/me")
    public Result<User> updateMe(@RequestBody User user, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(authService.updateProfile(userId, user));
    }

    /** 注销账号（需密码二次确认） */
    @DeleteMapping("/me")
    public Result<Void> deleteMe(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        accountService.deleteAccount(userId, body == null ? null : body.get("password"));
        return Result.success();
    }
}
