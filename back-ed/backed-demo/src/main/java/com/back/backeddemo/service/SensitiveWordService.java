package com.back.backeddemo.service;

import com.back.backeddemo.common.BusinessException;
import com.back.backeddemo.entity.SensitiveLog;
import com.back.backeddemo.mapper.SensitiveLogMapper;
import com.back.backeddemo.mapper.SensitiveWordMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 敏感词过滤服务。
 *
 * <h3>词库来源（两处合并生效）</h3>
 * <ol>
 *   <li><b>内置基础库</b>：{@code src/main/resources/sensitive-words.txt}，
 *       随 jar 一起部署，<b>服务器不需要执行任何 SQL</b>。
 *       格式 {@code 分类|词语}，按「广告 / 赌博 / 违法 / 诈骗 / 色情 / 辱骂 / 黑产」分类。</li>
 *   <li><b>自定义词</b>：数据库表 {@code sensitive_word}，
 *       管理员在后台「敏感词管理」里增删，改完调用 {@link #refresh()} 立即生效。</li>
 * </ol>
 *
 * <h3>匹配方式</h3>
 * 由 {@link SensitiveWordEngine} 做归一化 + Trie 一次扫描，
 * 所以「办*证」「办 证」「办-证」「ＢＡＮ ＺＨＥＮＧ」这类变体都能命中。
 * 性能与词库规模基本无关（原来那种「逐个 contains」在词多以后会很慢）。
 *
 * <h3>策略</h3>
 * 评论是「发即显示」（不先审后发），所以这里采用<b>直接拦截</b>：
 * 命中就不让发布，并提示用户修改。
 *
 * <p>⚠️ 静态词库只能挡住「写得比较直白」的内容。变体、图片、外链指向
 * 靠词库是拦不住的 —— 那部分建议接入合规的内容安全服务。
 */
@Service
public class SensitiveWordService {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordService.class);

    /** 内置词库文件名（放 resources 根目录） */
    private static final String BUILTIN_FILE = "sensitive-words.txt";

    private final SensitiveWordMapper mapper;
    private final SensitiveLogMapper sensitiveLogMapper;

    /** 匹配引擎（构建完成后只读，可并发使用） */
    private volatile SensitiveWordEngine engine = new SensitiveWordEngine();

    /** 内置词条数（统计用） */
    private volatile int builtinCount = 0;

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
        List<String> all = new ArrayList<>();

        // ① 内置基础库
        int builtin = 0;
        try {
            ClassPathResource res = new ClassPathResource(BUILTIN_FILE);
            if (res.exists()) {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        String w = parseBuiltinLine(line);
                        if (w != null) {
                            all.add(w);
                            builtin++;
                        }
                    }
                }
            } else {
                log.warn("内置敏感词库 {} 不存在，将只用数据库里的词", BUILTIN_FILE);
            }
        } catch (Exception e) {
            log.warn("内置敏感词库读取失败：{}", e.getMessage());
        }
        this.builtinCount = builtin;

        // ② 数据库自定义词
        int custom = 0;
        try {
            for (String w : mapper.listAllWords()) {
                if (w != null && !w.isBlank()) {
                    all.add(w.trim());
                    custom++;
                }
            }
        } catch (Exception e) {
            // 数据库读不到也不能让应用起不来 —— 至少还有内置库兜着
            log.warn("数据库敏感词读取失败（仍会使用内置词库）：{}", e.getMessage());
        }

        SensitiveWordEngine built = new SensitiveWordEngine();
        built.build(all);
        this.engine = built;

        log.info("敏感词库加载完成：内置 {} 条 + 自定义 {} 条 = 去重后 {} 条",
                builtin, custom, built.size());
    }

    /**
     * 解析内置词库的一行：{@code 分类|词语}。
     * 也兼容只写「词语」不带分类的写法；注释和空行返回 null。
     */
    private static String parseBuiltinLine(String line) {
        if (line == null) {
            return null;
        }
        String s = line.trim();
        if (s.isEmpty() || s.startsWith("#")) {
            return null;
        }
        int bar = s.indexOf('|');
        String w = (bar >= 0 ? s.substring(bar + 1) : s).trim();
        return w.isEmpty() ? null : w;
    }

    /** 当前生效的词库条数（内置 + 自定义，已去重） */
    public int size() {
        return engine.size();
    }

    /** 内置词条数 */
    public int builtinSize() {
        return builtinCount;
    }

    /**
     * 找出文本里命中的第一个敏感词；没有命中返回 null。
     * 仅供服务端日志使用，不返回给前端（避免被用来试探词库）。
     */
    public String firstHit(String text) {
        return engine.firstHit(text);
    }

    /** 校验文本，命中敏感词则抛出业务异常，阻止发布 */
    public void validate(String text, String scene) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String hit = firstHit(text);
        if (hit != null) {
            // 命中词只写服务端日志，不回给客户端（防止被用来反推词库）
            log.info("[敏感词拦截] 场景={} 命中词={}", scene, hit);
            // 同时记一条「命中记录」，让管理员在后台能看到被拦了什么
            try {
                SensitiveLog record = new SensitiveLog();
                record.setWord(hit);
                record.setScene(scene);
                record.setContent(text.length() > 500 ? text.substring(0, 500) : text);
                sensitiveLogMapper.insert(record);
            } catch (Exception ignore) {
                // 命中记录写失败不影响拦截本身
            }
            throw new BusinessException(400, "内容包含敏感词，请修改后再提交");
        }
    }

    /**
     * 校验多个字段（标题 / 摘要 / 正文 一起传）。
     * 任一字段命中就拦下，场景名里会带上字段名，方便后台定位。
     */
    public void validateAll(String scene, String... texts) {
        if (texts == null) {
            return;
        }
        for (String t : texts) {
            validate(t, scene);
        }
    }
}
