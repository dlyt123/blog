package com.back.backeddemo.entity;

import lombok.Data;

@Data
public class Setting {
    private Long id;
    private String settingKey;
    private String settingValue;
}
