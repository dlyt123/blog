package com.back.backeddemo.service;

import com.back.backeddemo.entity.Comment;
import com.back.backeddemo.entity.Post;
import com.back.backeddemo.entity.Setting;
import com.back.backeddemo.entity.Subscribe;
import com.back.backeddemo.entity.User;
import com.back.backeddemo.mapper.CommentMapper;
import com.back.backeddemo.mapper.PostMapper;
import com.back.backeddemo.mapper.SettingMapper;
import com.back.backeddemo.mapper.SubscribeMapper;
import com.back.backeddemo.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 站内邮件通知的统一出口。
 *
 * <p>目前有三类：
 * <ol>
 *   <li><b>新文章</b> → 推送给所有未退订的订阅者</li>
 *   <li><b>评论被回复</b> → 通知被回复的那位</li>
 *   <li><b>文章被评论</b> → 通知文章作者</li>
 * </ol>
 *
 * <p>设计约定：
 * <ul>
 *   <li>全部走 {@link MailService} 的异步发送；未配置 SMTP 时自动跳过，不影响主流程</li>
 *   <li>通知失败一律吞掉异常 —— 绝不能因为发不出邮件而让评论/发布失败</li>
 *   <li>不给自己发通知（自己评论自己的文章不打扰自己）</li>
 * </ul>
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final MailService mailService;
    private final SettingMapper settingMapper;
    private final SubscribeMapper subscribeMapper;
    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;

    public NotificationService(MailService mailService, SettingMapper settingMapper,
                               SubscribeMapper subscribeMapper, UserMapper userMapper,
                               PostMapper postMapper, CommentMapper commentMapper) {
        this.mailService = mailService;
        this.settingMapper = settingMapper;
        this.subscribeMapper = subscribeMapper;
        this.userMapper = userMapper;
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
    }

    /**
     * 新文章发布 → 推送给订阅者。
     *
     * <p>⚠️ 2026-09-20 起<b>默认关闭</b> ✗ —— 订阅推送已改成
     * {@link DailyDigestService} 的「每日精选」模式（每天早上一封、没文章就不发）。
     * 如果这里还开着，作者发一篇文章订阅者就会收到<b>两封</b>（一封即时 + 一封第二天的精选）✗
     *
     * <p>想退回"即时推送"的话，把 {@code blog.notify.immediate-subscribe-push}
     * 设成 true，并关掉 {@code blog.digest.enabled} 即可 ✓
     */
    @Value("${blog.notify.immediate-subscribe-push:false}")
    private boolean immediateSubscribePush;

    /** 新文章发布 → 推送给订阅者 */
    public void notifyNewPost(Post post) {
        if (!immediateSubscribePush) {
            // 已改为「每日精选」模式，这里直接返回，避免重复发送
            return;
        }
        try {
            List<Subscribe> subs = subscribeMapper.listActive();
            if (subs == null || subs.isEmpty()) {
                return;
            }
            String subject = "新文章：" + post.getTitle() + " - " + siteName();
            String link = siteUrl() + "/posts/" + post.getId();
            for (Subscribe s : subs) {
                if (s.getEmail() == null || s.getEmail().isBlank()) {
                    continue;
                }
                String body = "「" + post.getTitle() + "」已发布，点这里阅读：\n" + link + "\n\n"
                        + unsubscribeTip(s.getToken());
                mailService.sendText(s.getEmail(), subject, body);
            }
            log.info("[通知] 新文章「{}」已推送给 {} 位订阅者", post.getTitle(), subs.size());
        } catch (Exception e) {
            log.warn("[通知] 新文章推送失败：{}", e.getMessage());
        }
    }

    /**
     * 有人在文章下留言 → 根据情况通知「被回复的人」或「文章作者」。
     *
     * @param comment 刚插入的评论
     * @param actorId 评论者 id
     */
    public void notifyComment(Comment comment, Long actorId) {
        try {
            Post post = postMapper.findById(comment.getPostId());
            if (post == null) {
                return;
            }
            String link = siteUrl() + "/posts/" + post.getId();
            String who = displayName(actorId);

            if (comment.getParentId() != null && comment.getParentId() != 0) {
                // 楼中楼：通知被回复的那位
                Comment parent = commentMapper.findById(comment.getParentId());
                if (parent == null || parent.getUserId() == null || parent.getUserId().equals(actorId)) {
                    return;
                }
                User target = userMapper.findById(parent.getUserId());
                if (target == null || blank(target.getEmail())) {
                    return;
                }
                mailService.sendText(target.getEmail(),
                        who + " 回复了你在《" + post.getTitle() + "》下的评论",
                        who + " 回复了你：\n\n" + comment.getContent() + "\n\n查看：\n" + link);
            } else {
                // 顶层评论：通知文章作者
                Long authorId = post.getAuthorId();
                if (authorId == null || authorId.equals(actorId)) {
                    return;
                }
                User author = userMapper.findById(authorId);
                if (author == null || blank(author.getEmail())) {
                    return;
                }
                mailService.sendText(author.getEmail(),
                        who + " 评论了你的文章《" + post.getTitle() + "》",
                        who + " 留言说：\n\n" + comment.getContent() + "\n\n查看：\n" + link);
            }
        } catch (Exception e) {
            log.warn("[通知] 评论通知失败：{}", e.getMessage());
        }
    }

    private String displayName(Long userId) {
        if (userId == null) {
            return "有人";
        }
        User u = userMapper.findById(userId);
        if (u == null) {
            return "有人";
        }
        return u.getNickname() != null && !u.getNickname().isBlank() ? u.getNickname() : u.getUsername();
    }

    private String unsubscribeTip(String token) {
        return "——\n不想再收到更新提醒？点这里退订：\n" + siteUrl() + "/unsubscribe?token=" + token;
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private String siteUrl() {
        Setting s = settingMapper.findByKey("siteUrl");
        String v = s == null ? null : s.getSettingValue();
        String url = (v == null || v.isBlank()) ? "http://localhost:8080" : v;
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String siteName() {
        Setting s = settingMapper.findByKey("siteName");
        String v = s == null ? null : s.getSettingValue();
        return (v == null || v.isBlank()) ? "我的博客" : v;
    }
}
