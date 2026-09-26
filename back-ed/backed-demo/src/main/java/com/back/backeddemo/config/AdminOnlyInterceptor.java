package com.back.backeddemo.config;

import com.back.backeddemo.common.BusinessException;
import com.back.backeddemo.entity.User;
import com.back.backeddemo.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员专属拦截器：{@code /api/admin/**} 下的接口默认仅 ADMIN 可访问。
 *
 * <p><b>为什么改成"按前缀 + 显式豁免"</b>：
 * 原来的写法是在 {@code WebConfig} 里手写一份路径清单（十几条 {@code addPathPatterns}）。
 * 那种写法是 <b>fail-open</b> 的 —— 新加一个 {@code /api/admin/xxx} 而忘了往清单里补一行，
 * 这个接口就完全没有管理员校验，而且不会有任何报错提示。
 * 现在默认值反过来：{@code /api/admin/**} 一律要求管理员，
 * 确实不能要求管理员的接口必须主动标 {@link AdminExempt}。
 * 忘记标注的后果从「接口被暴露」变成「接口暂时访问不了」，是安全的失败方向。
 *
 * <p>注意：必须注册在 {@link JwtInterceptor} 之后，否则拿不到 userId。
 */
@Component
public class AdminOnlyInterceptor implements HandlerInterceptor {

    private final UserMapper userMapper;

    public AdminOnlyInterceptor(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 静态资源等非控制器请求直接放行
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        // 方法或类上标了 @AdminExempt 的，交给 Controller 自己鉴权
        if (method.hasMethodAnnotation(AdminExempt.class)
                || method.getBeanType().isAnnotationPresent(AdminExempt.class)) {
            return true;
        }

        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(401, "未登录或 Token 缺失");
        }
        User user = userMapper.findById(userId);
        if (user == null || !"ADMIN".equals(user.getRole())) {
            throw new BusinessException(403, "仅管理员可访问此功能");
        }
        return true;
    }
}
