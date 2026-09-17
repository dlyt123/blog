package com.back.backeddemo.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OperationLog {
    private Long id;
    private Long userId;
    private String username;
    private String action;
    private String target;
    private String method;
    private String path;
    private String ip;
    private Integer success;
    private LocalDateTime createTime;
}
