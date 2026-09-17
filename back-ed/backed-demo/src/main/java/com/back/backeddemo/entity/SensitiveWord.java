package com.back.backeddemo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感词（用于评论内容过滤）
 */
@Data
public class SensitiveWord {
    private Long id;
    private String word;
    private LocalDateTime createTime;
}
