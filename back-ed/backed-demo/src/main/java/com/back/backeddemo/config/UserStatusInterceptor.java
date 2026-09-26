package com.back.backeddemo.config;

import com.back.backeddemo.common.BusinessException;
import com.back.backeddemo.entity.User;
import com.back.backeddemo.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.format.DateTimeFormatter;

/**
 * 用户状态拦截器：封号 / 禁言的统一生效点。
 *
 * <p>为什么单独做一个拦截器，而不是塞进 {@link RequireLoginInterceptor}：
 * 那个拦截器管的是「**要不要登录**」✗，这里管的是「**登录了但是不是被处罚了**」✓
 * —— 两件事，分开更好维护 ✓。
 *
 * <p><b>规则：</b>
 * <ul>
 *   <li><b>GET 请求直接放行</b> ✓ —— 被封的人仍可浏览公开内容，
 *       只是不能发表任何东西。这样处理更克制，也避免误封时把人彻底挡在外面。</li>
 *   <li><b>未登录（userId 为空）也放行</b> ✓ —— 那不是这里该管的事，
 *       后面的 {@link RequireLoginInterceptor} 会拦下来。</li>
 *   <li><b>封号</b> ✗ —— 任何写操作都拒绝，并提示封禁原因。</li>
 *   <li><b>禁言</b> ✗ —— 写操作拒绝，并提示解禁时间。
 *       禁言到期后<b>自动失效</b>，不需要管理员手动解封 ✓
 *       （因为判断的是 {@code mutedUntil > now} ✓）。</li>
 * </ul>
 *
 * <p>⚠️ 必须注册在 {@link JwtInterceptor} <b>之后</b>，否则拿不到它解析出的 userId。
 */
@Component
public class UserStatusInterceptor implements HandlerInterceptor {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserMapper userMapper;

    public UserStatusInterceptor(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 只读请求不管：被处罚的人还能看，只是不能写
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            // 没登录 → 交给 RequireLoginInterceptor 处理
            return true;
        }

        User user;
        try {
            user = userMapper.findById(userId);
        } catch (Exception e) {
            // 查库失败时不误伤用户，放行让业务继续（真有问题会在业务层暴露）
            return true;
        }
        if (user == null) {
            return true;
        }

        if (user.bannedNow()) {
            String reason = user.getBanReason();
            throw new BusinessException(403, "您的账号已被封禁"
                    + (reason != null && !reason.isBlank() ? "：" + reason : ""));
        }

        if (user.mutedNow()) {
            throw new BusinessException(403, "您已被禁言，解禁时间："
                    + user.getMutedUntil().format(FMT));
        }

        return true;
    }
}
