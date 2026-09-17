package com.back.backeddemo.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败次数限制（防暴力破解）。
 *
 * <p>与「图形验证码」「接口限流」是三道不同的防线：
 * <ul>
 *   <li>限流：按 IP 限制请求频率（挡高频脚本）</li>
 *   <li>验证码：挡无脑自动化提交</li>
 *   <li>本类：按<b>账号</b>锁定（挡针对某个用户名的持续尝试，即使换 IP）</li>
 * </ul>
 *
 * <p>放在内存里：单机部署够用；多实例部署需换 Redis（改这一个类即可）。
 */
@Component
public class LoginAttemptService {

    /** 连续失败多少次后锁定 */
    @Value("${blog.login.max-attempts:5}")
    private int maxAttempts;

    /** 锁定时长（分钟） */
    @Value("${blog.login.lock-minutes:15}")
    private int lockMinutes;

    private static class Attempt {
        int count;
        long lockUntil;
    }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** 当前是否处于锁定期；是则返回剩余分钟数（0 表示没锁） */
    public long lockedMinutes(String username) {
        if (username == null) {
            return 0;
        }
        Attempt a = attempts.get(key(username));
        if (a == null || a.lockUntil == 0) {
            return 0;
        }
        long remain = a.lockUntil - System.currentTimeMillis();
        if (remain <= 0) {
            attempts.remove(key(username));
            return 0;
        }
        // 向上取整，避免显示「剩 0 分钟」
        return (remain + 59_999) / 60_000;
    }

    /** 记一次失败；达到阈值就锁定 */
    public void recordFailure(String username) {
        if (username == null) {
            return;
        }
        Attempt a = attempts.computeIfAbsent(key(username), k -> new Attempt());
        synchronized (a) {
            a.count++;
            if (a.count >= maxAttempts) {
                a.lockUntil = System.currentTimeMillis() + lockMinutes * 60_000L;
                a.count = 0;
            }
        }
    }

    /** 登录成功后清空计数 */
    public void clear(String username) {
        if (username != null) {
            attempts.remove(key(username));
        }
    }

    /** 还剩几次机会（给前端提示用，可选） */
    public int remaining(String username) {
        if (username == null) {
            return maxAttempts;
        }
        Attempt a = attempts.get(key(username));
        if (a == null) {
            return maxAttempts;
        }
        return Math.max(0, maxAttempts - a.count);
    }

    /** 定期清理已过期的锁定记录 */
    @Scheduled(fixedDelay = 600_000L)
    public void cleanup() {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(e -> e.getValue().lockUntil != 0 && e.getValue().lockUntil < now);
    }

    private String key(String username) {
        return username.trim().toLowerCase();
    }
}
