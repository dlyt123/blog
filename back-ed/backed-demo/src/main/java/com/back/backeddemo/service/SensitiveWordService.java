package com.back.backeddemo.service;

import com.back.backeddemo.common.BusinessException;
import com.back.backeddemo.entity.SensitiveLog;
import com.back.backeddemo.mapper.SensitiveLogMapper;
import com.back.backeddemo.mapper.SensitiveWordMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 敏感词过滤服务。
 *
 * <p>词库存在数据库表 sensitive_word 里（管理员可在后台维护），
 * 启动时一次性读进内存缓存，之后每次校验只查内存，不再打数据库。
 * 管理员增删词后调用 {@link #refresh()} 立即生效。
 *
 * <p>本站评论是「发即显示」（不先审后发），所以敏感词采用**直接拦截**的策略：
 * 命中就不让发布，并提示用户修改。这样既不影响正常评论的即时性，
 * 又挡住了垃圾广告和辱骂内容。
 *
 * <p>匹配方式是朴素子串匹配，词库只有几百条时开销可以忽略。
 * 如果以后词库涨到上万条，建议换成 DFA（Aho-Corasick）算法。
 */
@Service
public class SensitiveWordService {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordService.class);

    private final SensitiveWordMapper mapper;
    private final SensitiveLogMapper sensitiveLogMapper;

    /** 内存词库缓存（小写化，便于英文词忽略大小写） */
    private volatile Set<String> words = Set.of();

    public SensitiveWordService(SensitiveWordMapper mapper, SensitiveLogMapper sensitiveLogMapper) {
        this.mapper = mapper;
        this.sensitiveLogMapper = sensitiveLogMapper;
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    /** 重新加载词库（启动时 + 管理员增删词后调用） */
    public synchronized void refresh() {
        try {
            Set<String> loaded = new HashSet<>();
            for (String w : mapper.listAllWords()) {
                if (w != null && !w.isBlank()) {
                    loaded.add(w.trim().toLowerCase(Locale.ROOT));
                }
            }
            this.words = loaded;
            log.info("敏感词库加载完成，共 {} 条", loaded.size());
        } catch (Exception e) {
            // 词库加载失败不能让整个应用起不来，退化为「不过滤」
            log.warn("敏感词库加载失败，本次启动将不做敏感词过滤：{}", e.getMessage());
        }
    }

    /** 当前词库条数 */
    public int size() {
        return words.size();
    }

    /**
     * 找出文本里命中的第一个敏感词；没有命中返回 null。
     * 仅供服务端日志使用，不返回给前端（避免被用来试探词库）。
     */
    public String firstHit(String text) {
        if (text == null || text.isEmpty() || words.isEmpty()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String w : words) {
            if (lower.contains(w)) {
                return w;
            }
        }
        return null;
    }

    /** 校验文本，命中敏感词则抛出业务异常，阻止发布 */
    public void validate(String text, String scene) {
        String hit = firstHit(text);
        if (hit != null) {
            // 命中词只写服务端日志，不回给客户端
            log.info("[敏感词拦截] 场景={} 命中词={}", scene, hit);
            // 同时记一条「命中记录」，让管理员在后台能看到被拦了什么
            try {
                SensitiveLog record = new SensitiveLog();
                record.setWord(hit);
                record.setScene(scene);
                record.setContent(text != null && text.length() > 500
                        ? text.substring(0, 500) : text);
                sensitiveLogMapper.insert(record);
            } catch (Exception ignore) {
                // 命中记录写失败不影响拦截本身
            }
            throw new BusinessException(400, "内容包含敏感词，请修改后再提交");
        }
    }
}
