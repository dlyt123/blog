package com.back.backeddemo.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Report {
    private Long id;
    private String targetType;
    private Long targetId;
    private String reason;
    private Long userId;
    private Integer status;
    private LocalDateTime createTime;
    // 展示辅助字段
    private String targetTitle;
    private String reporterName;
}
