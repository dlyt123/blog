package com.back.backeddemo.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class Comment {
    private Long id;
    private Long postId;
    private Long parentId;
    private Long userId;
    private String content;
    private String nickname;
    private String email;
    private String website;
    private String avatar;
    private Integer status;
    private LocalDateTime createTime;

    // 非表字段
    private List<Comment> replies;
    /** 所属文章标题（首页「最新评论」侧边栏用，非表字段） */
    private String postTitle;
}
