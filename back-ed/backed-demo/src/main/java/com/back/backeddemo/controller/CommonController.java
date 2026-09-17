package com.back.backeddemo.controller;

import com.back.backeddemo.common.Result;
import com.back.backeddemo.entity.Post;
import com.back.backeddemo.entity.Setting;
import com.back.backeddemo.entity.Subscribe;
import com.back.backeddemo.mapper.PostMapper;
import com.back.backeddemo.mapper.SettingMapper;
import com.back.backeddemo.mapper.SubscribeMapper;
import com.back.backeddemo.service.MailService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class CommonController {

    private final PostMapper postMapper;
    private final SettingMapper settingMapper;
    private final SubscribeMapper subscribeMapper;
    private final MailService mailService;

    public CommonController(PostMapper postMapper, SettingMapper settingMapper,
                            SubscribeMapper subscribeMapper, MailService mailService) {
        this.postMapper = postMapper;
        this.settingMapper = settingMapper;
        this.subscribeMapper = subscribeMapper;
        this.mailService = mailService;
    }

    /** RSS 订阅源（含全文） */
    @GetMapping(value = "/api/rss", produces = MediaType.APPLICATION_XML_VALUE)
    public String rss() {
        String siteName = getSetting("siteName", "我的博客");
        String siteDesc = getSetting("description", "个人博客");
        String siteUrl = getSetting("siteUrl", "http://localhost:8080");
        List<Post> posts = postMapper.listPopular(20);
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<rss version=\"2.0\" xmlns:content=\"http://purl.org/rss/1.0/modules/content/\"><channel>");
        sb.append("<title>").append(escape(siteName)).append("</title>");
        sb.append("<description>").append(escape(siteDesc)).append("</description>");
        sb.append("<link>").append(escape(siteUrl)).append("</link>");
        for (Post p : posts) {
            sb.append("<item>");
            sb.append("<title>").append(escape(p.getTitle())).append("</title>");
            sb.append("<link>").append(siteUrl).append("/posts/").append(p.getId()).append("</link>");
            LocalDateTime pub = p.getPublishTime() != null ? p.getPublishTime() : p.getCreateTime();
            if (pub != null) {
                sb.append("<pubDate>").append(pub).append("</pubDate>");
            }
            sb.append("<description><![CDATA[")
              .append(p.getSummary() == null ? "" : p.getSummary())
              .append("]]></description>");
            sb.append("<content:encoded><![CDATA[")
              .append(p.getContent() == null ? "" : p.getContent())
              .append("]]></content:encoded>");
            sb.append("</item>");
        }
        sb.append("</channel></rss>");
        return sb.toString();
    }

    /** 转义 XML 特殊字符 */
    private String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    /** 站点地图 */
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        String siteUrl = getSetting("siteUrl", "http://localhost:8080");
        List<Post> posts = postMapper.listPopular(1000);
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
        sb.append("<url><loc>").append(siteUrl).append("/</loc></url>");
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        for (Post p : posts) {
            sb.append("<url><loc>").append(siteUrl).append("/posts/").append(p.getId()).append("</loc>");
            LocalDateTime t = p.getUpdateTime() != null ? p.getUpdateTime()
                    : (p.getPublishTime() != null ? p.getPublishTime() : p.getCreateTime());
            if (t != null) {
                sb.append("<lastmod>").append(t.format(fmt)).append("</lastmod>");
            }
            sb.append("</url>");
        }
        sb.append("</urlset>");
        return sb.toString();
    }

    /** 邮件订阅 */
    @PostMapping("/api/subscribe")
    public Result<Void> subscribe(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || !email.contains("@")) {
            return Result.error(400, "邮箱格式不正确");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        Subscribe s = new Subscribe();
        s.setEmail(email.trim());
        s.setToken(token);
        try {
            subscribeMapper.insert(s);
        } catch (Exception e) {
            // 邮箱已存在：拿库里已有的 token 来发退订链接
            Subscribe exist = subscribeMapper.listActive().stream()
                    .filter(x -> email.trim().equalsIgnoreCase(x.getEmail()))
                    .findFirst().orElse(null);
            if (exist != null) {
                token = exist.getToken();
            }
        }
        // 订阅欢迎邮件（异步，未配置 SMTP 时静默跳过）；邮件里必须带退订入口
        String siteName = getSetting("siteName", "我的博客");
        mailService.sendText(email.trim(), "订阅成功 - " + siteName,
                "你已成功订阅「" + siteName + "」的更新提醒，新文章发布时会第一时间通知你。\n\n"
                        + "如果不想再收到，点这里退订：\n" + unsubscribeLink(token));
        return Result.success();
    }

    /** 退订（前端 /unsubscribe 页面拿到 token 后调它） */
    @PostMapping("/api/subscribe/unsubscribe")
    public Result<Void> unsubscribe(@RequestBody Map<String, String> body) {
        String token = body == null ? null : body.get("token");
        if (token == null || token.isBlank()) {
            return Result.error(400, "退订链接无效");
        }
        if (subscribeMapper.findByToken(token.trim()) == null) {
            return Result.error(400, "退订链接无效或已失效");
        }
        subscribeMapper.unsubscribe(token.trim());
        return Result.success();
    }

    /** 查询退订令牌是否有效（给退订页展示用） */
    @GetMapping("/api/subscribe/unsubscribe/check")
    public Result<Map<String, Object>> checkUnsubscribe(@RequestParam String token) {
        Subscribe s = subscribeMapper.findByToken(token);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("valid", s != null);
        data.put("email", s == null ? null : maskEmail(s.getEmail()));
        data.put("unsubscribed", s != null && s.getUnsubscribed() != null && s.getUnsubscribed() == 1);
        return Result.success(data);
    }

    private String unsubscribeLink(String token) {
        String siteUrl = getSetting("siteUrl", "http://localhost:8080");
        if (siteUrl.endsWith("/")) {
            siteUrl = siteUrl.substring(0, siteUrl.length() - 1);
        }
        return siteUrl + "/unsubscribe?token=" + token;
    }

    /** 邮箱打码：ad***@qq.com */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int at = email.indexOf('@');
        String name = email.substring(0, at);
        String shown = name.length() <= 2 ? name.substring(0, 1) + "*" : name.substring(0, 2) + "***";
        return shown + email.substring(at);
    }

    private String getSetting(String key, String def) {
        Setting s = settingMapper.findByKey(key);
        return s != null && s.getSettingValue() != null ? s.getSettingValue() : def;
    }
}
