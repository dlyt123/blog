package com.back.backeddemo.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SensitiveLog {
    private Long id;
    private String word;
    private String scene;
    private String content;
    private LocalDateTime createTime;
}
