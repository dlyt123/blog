package com.back.backeddemo.service;

import com.back.backeddemo.entity.Post;
import com.back.backeddemo.entity.Setting;
import com.back.backeddemo.entity.Subscribe;
import com.back.backeddemo.mapper.PostMapper;
import com.back.backeddemo.mapper.SettingMapper;
import com.back.backeddemo.mapper.SubscribeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 每日精选邮件（订阅推送）。
 *
 * <h3>为什么改成"每日精选"而不是"一发文章就群发"</h3>
 * 原来的做法是：每发布一篇文章，就给所有订阅者各发一封 ✗
 * 订阅者多了以后有两个问题：
 * <ol>
 *   <li><b>骚扰用户</b>：作者一天发 5 篇，订阅者就收 5 封邮件。</li>
 *   <li><b>发不出去</b>：QQ 个人邮箱 SMTP 有发信频率/日限额，
 *       上千封就会被限流甚至封号 ✗✗ —— 这是最硬的瓶颈。</li>
 * </ol>
 *
 * <h3>现在的规则（与作者确认过）</h3>
 * <ul>
 *   <li>每天早上跑一次，看<b>昨天</b>发布的文章里「最优秀」的一篇 ✓</li>
 *   <li>排序：置顶优先 → 点赞数 + 评论数×3 → 最新发布 ✓</li>
 *   <li><b>昨天没有新文章 → 一封都不发</b> ✓（不打扰订阅者）</li>
 * </ul>
 *
 * <h3>发送节流</h3>
 * 分批发送 ✓ 每批之间 sleep 一小会儿 ✓ —— 虽然 {@code MailService} 本身是
 * {@code @Async} 的（不阻塞），但如果一口气把上万个任务全塞进队列，
 * 队列会吃掉大量内存 ✗ 而且 SMTP 那边也会立刻触发限流 ✗
 * 所以这里主动按批放行 ✓
 *
 * <h3>⚠️ 上万人订阅时仍然要换发信通道</h3>
 * QQ 个人邮箱只适合几百封 ✗ 真到上千级别必须换阿里云邮件推送 /
 * 腾讯云 SES 这类专业服务 ✓ 参数 {@code blog.digest.max-recipients}
 * 就是防止"悄无声息地发爆"的保险丝 ✓
 */
@Service
public class DailyDigestService {

    private static final Logger log = LoggerFactory.getLogger(DailyDigestService.class);

    private final PostMapper postMapper;
    private final SubscribeMapper subscribeMapper;
    private final SettingMapper settingMapper;
    private final MailService mailService;

    /** 总开关（默认开） */
    @Value("${blog.digest.enabled:true}")
    private boolean enabled;

    /** 每批发多少封 */
    @Value("${blog.digest.batch-size:20}")
    private int batchSize;

    /** 每批之间停多久（毫秒）—— 用来给 SMTP 限流留余地 */
    @Value("${blog.digest.batch-interval-ms:1000}")
    private long batchIntervalMs;

    /** 单次最多发给多少人（保险丝，防手滑发爆） */
    @Value("${blog.digest.max-recipients:5000}")
    private int maxRecipients;

    public DailyDigestService(PostMapper postMapper, SubscribeMapper subscribeMapper,
                              SettingMapper settingMapper, MailService mailService) {
        this.postMapper = postMapper;
        this.subscribeMapper = subscribeMapper;
        this.settingMapper = settingMapper;
        this.mailService = mailService;
    }

    /**
     * 每天早上 8:00 跑（时区跟随服务器）。
     * cron 可在 application.yml / 环境变量里改：blog.digest.cron
     */
    @Scheduled(cron = "${blog.digest.cron:0 0 8 * * ?}")
    public void sendDailyDigest() {
        if (!enabled) {
            return;
        }
        try {
            runOnce(LocalDate.now());
        } catch (Exception e) {
            // 定时任务绝不能把异常抛出去，否则后续调度可能受影响
            log.error("[每日精选] 执行失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 执行一次（把 date 当作"今天"，推的是 date 前一天的文章）。
     * 抽成 public 是为了方便手工触发 / 测试。
     */
    public int runOnce(LocalDate today) {
        LocalDate day = today.minusDays(1);
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = today.atStartOfDay();

        Post best = postMapper.pickDailyBest(from, to);
        if (best == null) {
            log.info("[每日精选] {} 没有发布新文章，今天不发邮件 ✓", day);
            return 0;
        }

        List<Subscribe> subs = subscribeMapper.listActive();
        if (subs == null || subs.isEmpty()) {
            log.info("[每日精选] 今天有文章但还没有订阅者，跳过");
            return 0;
        }
        if (subs.size() > maxRecipients) {
            log.warn("[每日精选] 订阅者 {} 人，超过上限 {}，本次只发前 {} 人 ✗ 建议尽快换成专业邮件服务",
                    subs.size(), maxRecipients, maxRecipients);
            subs = subs.subList(0, maxRecipients);
        }

        String subject = "【" + siteName() + "】今日精选：" + best.getTitle();
        String url = siteUrl() + "/posts/" + best.getId();
        String body = "你好，\n\n"
                + siteName() + " 昨天发布了新文章，为你精选一篇：\n\n"
                + "《" + best.getTitle() + "》\n"
                + (blank(best.getSummary()) ? "" : "\n" + best.getSummary() + "\n")
                + "\n阅读全文：\n" + url + "\n"
                + "\n——\n来自 " + siteName() + " 的每日精选\n";

        int sent = 0;
        int batch = Math.max(1, batchSize);
        for (int i = 0; i < subs.size(); i++) {
            Subscribe s = subs.get(i);
            if (blank(s.getEmail())) {
                continue;
            }
            try {
                mailService.sendText(s.getEmail(), subject, body + unsubscribeTip(s.getToken()));
                sent++;
            } catch (Exception e) {
                // 单个人发失败不能中断整批
                log.warn("[每日精选] 发给 {} 失败：{}", s.getEmail(), e.getMessage());
            }
            // 每发完一批歇一下，避免一次性把队列灌满、也避开 SMTP 的频率限制
            if ((i + 1) % batch == 0 && i + 1 < subs.size()) {
                try {
                    Thread.sleep(Math.max(0, batchIntervalMs));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("[每日精选] {} 精选《{}》已发给 {} 位订阅者（共 {} 人）",
                day, best.getTitle(), sent, subs.size());
        return sent;
    }

    private String unsubscribeTip(String token) {
        if (blank(token)) {
            return "";
        }
        return "\n不想再收到更新提醒？点这里退订：\n" + siteUrl() + "/unsubscribe?token=" + token + "\n";
    }

    /** 站点地址：邮件里的链接必须用公网地址，不能是 localhost ✗ */
    private String siteUrl() {
        Setting s = settingMapper.findByKey("siteUrl");
        String v = s == null ? null : s.getSettingValue();
        String url = (v == null || v.isBlank()) ? "http://localhost:8080" : v.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String siteName() {
        Setting s = settingMapper.findByKey("siteName");
        String v = s == null ? null : s.getSettingValue();
        return (v == null || v.isBlank()) ? "博客" : v.trim();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
