package com.back.backeddemo.config;

import com.back.backeddemo.common.BusinessException;
import com.back.backeddemo.entity.User;
import com.back.backeddemo.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员专属接口拦截器：分类/标签/评论/友链/设置/统计 的后台管理仅 ADMIN 可访问。
 * 注意：文章管理（/api/admin/posts/**）不走这里，由 AdminPostController 内部做「管理员全部 / 博主自己的」细粒度校验。
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