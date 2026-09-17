package com.back.backeddemo.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Link {
    private Long id;
    private String name;
    private String url;
    private String groupName;
    private Integer sort;
    private LocalDateTime createTime;
}
