package com.back.backeddemo.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 邮件通知服务。
 *
 * <p>用于：订阅欢迎、评论通知、新文章推送。全部走 {@link #send} 异步发送。
 *
 * <p>关键设计：SMTP 没配置时，Spring Boot 不会创建 JavaMailSender（mail auto-config 依赖
 * spring.mail.host），所以这里用 Optional 注入；没配置或发件人为空时**静默跳过并打日志**，
 * 不影响订阅/评论/发布这些主流程。
 *
 * <p>配置（application-local.yml 或环境变量）：
 * <pre>
 * spring.mail.host / port / username / password   # SMTP 账号
 * blog.mail.from                                   # 发件人邮箱
 * </pre>
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final Optional<JavaMailSender> mailSender;

    @Value("${blog.mail.from:}")
    private String from;

    public MailService(Optional<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
    }

    public boolean isConfigured() {
        return mailSender.isPresent() && from != null && !from.isBlank();
    }

    /** 异步发送 HTML 邮件，失败不影响主流程 */
    @Async
    public void send(String to, String subject, String html) {
        if (!isConfigured()) {
            log.info("[邮件] 未配置 SMTP，跳过发送（{} -> {}）", subject, to);
            return;
        }
        try {
            MimeMessage message = build(to, subject, html);
            mailSender.get().send(message);
            log.info("[邮件] 已发送 {} -> {}", to, subject);
        } catch (Exception e) {
            log.warn("[邮件] 发送失败 {} -> {}：{}", to, subject, e.getMessage());
        }
    }

    /**
     * 同步发送，失败直接抛异常。
     * 用于「发送测试邮件」这类需要立刻把结果告诉用户的场景 ——
     * 异步发的话接口会秒返回成功，但邮件其实可能已经失败了，那是假成功。
     */
    public void sendSync(String to, String subject, String html) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("未配置 SMTP（需要 spring.mail.host/username 和 blog.mail.from）");
        }
        mailSender.get().send(build(to, subject, html));
        log.info("[邮件] 已同步发送 {} -> {}", to, subject);
    }

    /** 组装 MimeMessage */
    private MimeMessage build(String to, String subject, String html) throws Exception {
        MimeMessage message = mailSender.get().createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        return message;
    }

    /** 简单文本邮件（自动按 HTML 转义） */
    public void sendText(String to, String subject, String text) {
        send(to, subject, escape(text));
    }

    /** 简单文本邮件（同步版） */
    public void sendTextSync(String to, String subject, String text) throws Exception {
        sendSync(to, subject, escape(text));
    }

    private String escape(String text) {
        String escaped = text == null ? "" : text
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br>");
        return escaped;
    }

    /** 当前发件人（给「测试邮件」页展示用） */
    public String fromAddress() {
        return from;
    }
}
