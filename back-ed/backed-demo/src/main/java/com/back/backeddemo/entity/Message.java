package com.back.backeddemo.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Message {
    private Long id;
    private Long fromUserId;
    private Long toUserId;
    private String content;
    private Integer isRead;
    private LocalDateTime createTime;

    // 展示辅助字段（联表查询结果）
    private Long otherId;
    private String otherName;
    private String otherAvatar;
    private String lastContent;
    private LocalDateTime lastTime;
    private Integer unread;
}
