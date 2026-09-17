package com.back.backeddemo.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Tag {
    private Long id;
    private String name;
    private String slug;
    private LocalDateTime createTime;

    // 非表字段
    private Long postCount;
}
