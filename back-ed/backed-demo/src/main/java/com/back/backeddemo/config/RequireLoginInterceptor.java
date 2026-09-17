package com.back.backeddemo.config;

import com.back.backeddemo.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 「需要登录」拦截器
 *
 * 本站在 {@link JwtInterceptor} 里改成软鉴权后，由这里统一决定哪些接口必须登录。
 *
 * 规则（公开博客模型）：
 *   公开：所有 GET 读接口 —— 首页文章、文章详情、分类、标签、归档、搜索、关于、友链、
 *         评论列表、站点信息、RSS、站点地图
 *   需要登录：
 *     1) /api/admin/**            —— 后台管理
 *     2) /api/auth/me             —— 个人资料
 *     3) 点赞 / 收藏（含查询状态）—— 和"我是谁"绑定
 *     4) 其余所有写操作（POST/PUT/DELETE）
 *        例外：/api/auth/login、/api/auth/register、/api/subscribe（邮箱订阅允许访客提交）
 *
 * 注意：必须注册在 JwtInterceptor **之后**，否则拿不到它解析出的 userId。
 */
@Component
public class RequireLoginInterceptor implements HandlerInterceptor {

    /** 点赞 / 收藏相关路径（含 /status 查询） */
    private static final Pattern INTERACTION =
            Pattern.compile("^/api/posts/\\d+/(like|favorite)(/status)?$");

    /** 写操作里允许匿名访问的接口 */
    private static final Set<String> WRITE_WHITELIST = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            // 忘记密码时用户还没登录，必须允许匿名
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/subscribe",
            // 邮件退订：点邮件里的链接进来，可能不在登录态
            "/api/subscribe/unsubscribe",
            // 访客留痕：游客也要上报访问，所以必须允许匿名
            "/api/visit"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        String method = request.getMethod().toUpperCase();

        boolean required =
                // 1) 后台管理
                path.startsWith("/api/admin/")
                // 2) 个人资料
                || "/api/auth/me".equals(path)
                // 3) 点赞 / 收藏
                || INTERACTION.matcher(path).matches()
                // 4) 我的收藏、我的关注等「以我为中心」的读接口
                || path.startsWith("/api/users/me/")
                // 5) 私信：全部接口（含 GET 读）都属于隐私，必须登录
                || path.startsWith("/api/messages/")
                || "/api/messages".equals(path)
                // 6) 其余写操作
                || (!"GET".equals(method) && !WRITE_WHITELIST.contains(path));

        if (required && request.getAttribute("userId") == null) {
            throw new BusinessException(401, "请先登录后再操作");
        }
        return true;
    }
}
