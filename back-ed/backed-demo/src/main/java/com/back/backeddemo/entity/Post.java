package com.back.backeddemo.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class Post {
    private Long id;
    private String title;
    private String summary;
    private String content;
    private String cover;
    private Long categoryId;
    private Long authorId;
    private Long seriesId;
    private String authorName;
    private String authorAvatar;
    private Integer status;
    private Integer pinned;
    private Integer recommended;
    private Integer views;
    private Integer likes;
    private LocalDateTime publishTime;
    private LocalDateTime scheduledAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;

    // 非表字段（联表查询结果）
    private String categoryName;
    private String seriesName;
    private List<String> tags;
    private List<Long> tagIds;
    private Post prev;
    private Post next;
}
