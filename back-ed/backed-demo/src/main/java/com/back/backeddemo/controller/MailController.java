package com.back.backeddemo.controller;

import com.back.backeddemo.common.BusinessException;
import com.back.backeddemo.common.Result;
import com.back.backeddemo.entity.Setting;
import com.back.backeddemo.mapper.SettingMapper;
import com.back.backeddemo.service.MailService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 邮件配置自检（仅管理员）
 *
 * - GET  /api/admin/mail/status  看当前是否配好、发件人是谁
 * - POST /api/admin/mail/test    真发一封测试邮件，立刻返回成败
 *
 * <p>上线后很有用：部署完点一下就知道 SMTP 通不通，
 * 不用等真的有人用「忘记密码」才发现邮件发不出去。
 */
@RestController
@RequestMapping("/api/admin/mail")
public class MailController {

    private final MailService mailService;
    private final SettingMapper settingMapper;

    public MailController(MailService mailService, SettingMapper settingMapper) {
        this.mailService = mailService;
        this.settingMapper = settingMapper;
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configured", mailService.isConfigured());
        data.put("from", mailService.fromAddress());
        return Result.success(data);
    }

    @PostMapping("/test")
    public Result<Void> test(@RequestBody Map<String, String> body) {
        String to = body == null ? null : body.get("to");
        if (to == null || !to.contains("@")) {
            throw new BusinessException(400, "请输入正确的收件邮箱");
        }
        String siteName = siteName();
        try {
            mailService.sendTextSync(to.trim(), "【测试邮件】" + siteName + " 邮件配置正常",
                    "如果你收到这封邮件，说明站点的邮件通知已经配置成功。\n\n"
                            + "发件人：" + mailService.fromAddress() + "\n"
                            + "发送时间：" + LocalDateTime.now() + "\n\n"
                            + "这封邮件由后台「站点设置 → 邮件配置」的测试按钮触发。");
            return Result.success();
        } catch (Exception e) {
            // 把真实原因回给管理员，方便排查（普通用户看不到这个接口）
            throw new BusinessException(400, "发送失败：" + e.getMessage());
        }
    }

    private String siteName() {
        Setting s = settingMapper.findByKey("siteName");
        String v = s == null ? null : s.getSettingValue();
        return (v == null || v.isBlank()) ? "我的博客" : v;
    }
}
