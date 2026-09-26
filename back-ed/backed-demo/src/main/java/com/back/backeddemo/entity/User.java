package com.back.backeddemo.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class User {
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String avatar;
    private String email;
    private String role;
    private LocalDateTime createTime;

    /**
     * 封号标记：0=正常 1=已封号。
     * 封号后无法登录；已发出的 token 在写操作时也会被 {@code UserStatusInterceptor} 拦下。
     */
    private Integer banned;

    /**
     * 禁言到期时间：null=未禁言。
     * 早于当前时间视为已自动解除（不需要人工解封）。
     */
    private LocalDateTime mutedUntil;

    /** 封禁 / 禁言原因（管理员自己看，可选） */
    private String banReason;

    /**
     * 当前是否处于封号状态。
     *
     * ⚠️ 刻意不叫 {@code isBanned()} —— Lombok 的 @Data 已经为字段 banned 生成了
     * {@code getBanned()}，再出现一个 {@code isBanned()} 会让 Jackson 报
     * 「Conflicting getter definitions」而直接 500。换个名字避开。
     */
    public boolean bannedNow() {
        return banned != null && banned == 1;
    }

    /**
     * 当前是否处于禁言中（到期时间还没过）。
     * 到期后自动失效，不需要管理员手动解封。
     */
    public boolean mutedNow() {
        return mutedUntil != null && mutedUntil.isAfter(LocalDateTime.now());
    }
}
