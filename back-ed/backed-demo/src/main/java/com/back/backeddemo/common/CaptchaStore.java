package com.back.backeddemo.common;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图形验证码存储（内存）。
 *
 * <p>为什么放内存而不放数据库：验证码 5 分钟就过期、量小、丢失无损失，
 * 落库反而增加读写负担。单机部署完全够用。
 * （若以后多实例部署，需要换成 Redis —— 届时改这一个类即可。）
 */
@Component
public class CaptchaStore {

    /** 有效期 5 分钟 */
    private static final long TTL_MS = 5 * 60_000L;

    private record Entry(String code, long expireAt) {
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    /** 存一个验证码，返回给前端的 key */
    public String put(String code) {
        String key = UUID.randomUUID().toString().replace("-", "");
        store.put(key, new Entry(code.toUpperCase(), System.currentTimeMillis() + TTL_MS));
        return key;
    }

    /**
     * 校验并**一次性消费**：不管对错都把它删掉，
     * 避免同一个验证码被反复拿来暴力试密码。
     */
    public boolean verify(String key, String input) {
        if (key == null || input == null || key.isBlank() || input.isBlank()) {
            return false;
        }
        Entry e = store.remove(key);
        if (e == null || e.expireAt() < System.currentTimeMillis()) {
            return false;
        }
        return e.code().equalsIgnoreCase(input.trim());
    }

    /** 定期清理过期条目，防止内存里越积越多 */
    @Scheduled(fixedDelay = 300_000L)
    public void cleanup() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(en -> en.getValue().expireAt() < now);
    }
}
