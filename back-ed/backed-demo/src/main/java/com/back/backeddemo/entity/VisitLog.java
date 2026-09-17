package com.back.backeddemo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 访客访问留痕
 */
@Data
public class VisitLog {
    private Long id;
    /** 访问者用户ID；为空表示未登录的游客 */
    private Long userId;
    private String ip;
    /** 访问的前端路由路径，如 /posts/1 */
    private String path;
    private String referer;
    private String userAgent;
    /** 由 UA 粗略解析出的设备信息，如「Chrome / Windows」 */
    private String device;
    private LocalDateTime createTime;

    /** 联表查出的用户名 / 昵称，仅用于后台展示 */
    private String username;
    private String nickname;
}
