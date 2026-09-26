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

    /**
     * 审核状态：0=无需审核/已通过 1=待审核 2=已驳回
     *
     * <p>规则：**管理员发文免审**（直接 0），**普通用户发文章要审**（置 1）。
     * 待审核期间 status 保持 0（草稿），所以前台看不到 —— 通过后才会变已发布。
     *
     * <p>修改已发布文章时会【重新进入待审核】—— 否则存在"先发正常内容过审、
     * 再偷偷改成违规内容"的绕过路径。代价是作者改完文章后会暂时下架，
     * 这是合规与体验之间的取舍，选了合规优先。
     */
    private Integer auditStatus;

    /** 审核备注 / 驳回理由（驳回时作者能看到） */
    private String auditRemark;

    // 非表字段（联表查询结果）
    private String categoryName;
    private String seriesName;
    private List<String> tags;
    private List<Long> tagIds;
    private Post prev;
    private Post next;
}
