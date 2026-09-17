package com.back.backeddemo.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Subscribe {
    private Long id;
    private String email;
    /** 退订令牌：邮件里的退订链接带着它 */
    private String token;
    /** 1 = 已退订，不再给它发信 */
    private Integer unsubscribed;
    private LocalDateTime createTime;
}
