package com.back.backeddemo.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Media {
    private Long id;
    private String filename;
    private String url;
    private String type;
    private Long size;
    private LocalDateTime createTime;
}
