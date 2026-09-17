package com.back.backeddemo.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 解析拦截器（软鉴权）
 *
 * 行为：**带了合法 token 就解析出用户身份，没带 / 无效则按「匿名访客」放行，不报错。**
 *
 * 为什么不再直接拒绝：
 *   本站是公开博客，访客不登录也要能浏览文章。
 *   如果这里对匿名请求直接抛 401，首页、文章详情这些公开接口就全被挡住了。
 *
 * 那「哪些接口必须登录」由谁管？
 *   → {@link RequireLoginInterceptor}：读操作默认公开，写操作 / 后台 / 个人资料要求登录。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public JwtInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            // 匿名访问：放行，让后续拦截器决定是否需要登录
            return true;
        }
        try {
            Claims claims = jwtUtil.parse(auth.substring(7));
            // 用 Number 统一取值，兼容 jjwt-gson 把数字反序列化为 Double 的情况
            Object userIdObj = claims.get("userId");
            Long userId = (userIdObj instanceof Number n) ? n.longValue() : null;
            request.setAttribute("userId", userId);
            request.setAttribute("username", claims.getSubject());
        } catch (Exception e) {
            // token 无效或过期：同样按匿名处理。
            // 这样过期的 token 不会连带把公开页面（首页 / 文章详情）也挡住；
            // 真正需要登录的接口会在 RequireLoginInterceptor 里给出明确提示。
            request.removeAttribute("userId");
            request.removeAttribute("username");
        }
        return true;
    }
}
