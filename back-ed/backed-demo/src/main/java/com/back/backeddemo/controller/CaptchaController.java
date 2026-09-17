package com.back.backeddemo.controller;

import com.back.backeddemo.common.CaptchaStore;
import com.back.backeddemo.common.CaptchaUtil;
import com.back.backeddemo.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 图形验证码（公开接口，供登录 / 注册 / 找回密码使用）
 *
 * <p>返回图片的 base64，前端直接塞进 {@code <img src="...">}，
 * 这样不必再开一个图片接口、也不用担心跨域。
 */
@RestController
public class CaptchaController {

    private final CaptchaStore captchaStore;

    public CaptchaController(CaptchaStore captchaStore) {
        this.captchaStore = captchaStore;
    }

    @GetMapping("/api/captcha")
    public Result<Map<String, String>> captcha() throws Exception {
        String code = CaptchaUtil.randomCode(4);
        String key = captchaStore.put(code);
        byte[] png = CaptchaUtil.render(code);

        Map<String, String> data = new LinkedHashMap<>();
        data.put("key", key);
        data.put("image", "data:image/png;base64," + Base64.getEncoder().encodeToString(png));
        return Result.success(data);
    }
}
