package com.back.backeddemo.controller;

import com.back.backeddemo.common.Result;
import com.back.backeddemo.entity.Setting;
import com.back.backeddemo.mapper.SettingMapper;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class SettingController {

    private final SettingMapper settingMapper;

    public SettingController(SettingMapper settingMapper) {
        this.settingMapper = settingMapper;
    }

    /** 可公开的站点展示字段（登录页也要显示站点名 / Logo，所以这个接口不鉴权） */
    private static final String[] PUBLIC_KEYS = {
            "siteName", "slogan", "description", "logo", "icp", "pageSize",
            // SEO 关键词：前端 site store 里有对应的 getter，之前漏了没返回，一并补上
            "keywords",
            // 版权声明要显示在公开的文章页底部，所以也是公开字段
            "copyright"
    };

    /**
     * 站点基础信息（公开，无需登录）
     * 只返回展示用的字段，不含「关于我」等内容。
     */
    @GetMapping("/api/site-info")
    public Result<Map<String, String>> siteInfo() {
        Map<String, String> map = new HashMap<>();
        for (String key : PUBLIC_KEYS) {
            Setting s = settingMapper.findByKey(key);
            if (s != null && s.getSettingValue() != null) {
                map.put(key, s.getSettingValue());
            }
        }
        return Result.success(map);
    }

    /** 获取站点信息（前台，需登录） */
    @GetMapping("/api/settings")
    public Result<Map<String, String>> get() {
        Map<String, String> map = new HashMap<>();
        for (Setting s : settingMapper.list()) {
            map.put(s.getSettingKey(), s.getSettingValue());
        }
        return Result.success(map);
    }

    /** 更新站点设置（后台） */
    @PutMapping("/api/admin/settings")
    public Result<Void> update(@RequestBody Map<String, String> body) {
        for (Map.Entry<String, String> entry : body.entrySet()) {
            Setting existing = settingMapper.findByKey(entry.getKey());
            if (existing != null) {
                existing.setSettingValue(entry.getValue());
                settingMapper.update(existing);
            } else {
                Setting s = new Setting();
                s.setSettingKey(entry.getKey());
                s.setSettingValue(entry.getValue());
                settingMapper.insert(s);
            }
        }
        return Result.success();
    }
}
