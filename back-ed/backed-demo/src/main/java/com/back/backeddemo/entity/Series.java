package com.back.backeddemo.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Series {
    private Long id;
    private String name;
    private String description;
    private Integer sort;
    private LocalDateTime createTime;
    // 展示辅助字段
    private Integer postCount;
}
