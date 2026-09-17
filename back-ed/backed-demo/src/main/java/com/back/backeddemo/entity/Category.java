package com.back.backeddemo.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Category {
    private Long id;
    private String name;
    private String slug;
    private Long parentId;
    private Integer sort;
    private LocalDateTime createTime;

    // 非表字段
    private Long postCount;
}
