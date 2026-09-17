package com.back.backeddemo.controller;

import com.back.backeddemo.common.Result;
import com.back.backeddemo.entity.SensitiveWord;
import com.back.backeddemo.mapper.SensitiveWordMapper;
import com.back.backeddemo.service.SensitiveWordService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 敏感词管理（仅管理员，路径已在 WebConfig 的 AdminOnlyInterceptor 里注册）
 *
 * - GET    /api/admin/sensitive-words        词库列表
 * - POST   /api/admin/sensitive-words        新增（支持一次粘贴多个，用换行 / 逗号 / 空格分隔）
 * - DELETE /api/admin/sensitive-words/{id}   删除
 *
 * 增删后立即刷新内存缓存，无需重启服务。
 */
@RestController
@RequestMapping("/api/admin/sensitive-words")
public class SensitiveWordController {

    /** 单个词的长度上限，跟数据库列宽保持一致 */
    private static final int MAX_WORD_LEN = 64;
    /** 一次最多批量添加多少个词 */
    private static final int MAX_BATCH = 500;

    private final SensitiveWordMapper mapper;
    private final SensitiveWordService sensitiveWordService;

    public SensitiveWordController(SensitiveWordMapper mapper, SensitiveWordService sensitiveWordService) {
        this.mapper = mapper;
        this.sensitiveWordService = sensitiveWordService;
    }

    /** 词库列表 */
    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(required = false) String keyword) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        List<SensitiveWord> list = mapper.list(kw);
        return Result.success(Map.of(
                "list", list,
                "total", list.size(),
                "cacheSize", sensitiveWordService.size()
        ));
    }

    /**
     * 新增敏感词。
     * 请求体二选一：
     *   { "word": "单个词" }
     *   { "words": "词一\n词二\n词三" }   // 也支持用中英文逗号、分号、空格分隔
     */
    @PostMapping
    public Result<Map<String, Object>> add(@RequestBody Map<String, String> body) {
        Set<String> parsed = parseWords(body);
        if (parsed.isEmpty()) {
            return Result.error(400, "请输入要添加的敏感词");
        }
        if (parsed.size() > MAX_BATCH) {
            return Result.error(400, "一次最多添加 " + MAX_BATCH + " 个词");
        }

        long before = mapper.countAll();
        for (String w : parsed) {
            mapper.insert(w);
        }
        sensitiveWordService.refresh();

        long added = mapper.countAll() - before;
        return Result.success(Map.of(
                "submitted", parsed.size(),
                "added", added,
                "total", mapper.countAll()
        ));
    }

    /** 删除敏感词 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mapper.delete(id);
        sensitiveWordService.refresh();
        return Result.success();
    }

    /** 把请求体里的词解析成去重后的集合 */
    private Set<String> parseWords(Map<String, String> body) {
        Set<String> result = new LinkedHashSet<>();
        String raw = body == null ? null : body.get("words");
        if (raw == null || raw.isBlank()) {
            raw = body == null ? null : body.get("word");
        }
        if (raw == null) {
            return result;
        }
        // 支持换行 / 中英文逗号 / 分号 / 顿号 / 空格 分隔
        for (String piece : raw.split("[\\r\\n,，;；、\\s]+")) {
            String w = piece.trim();
            if (w.isEmpty()) {
                continue;
            }
            if (w.length() > MAX_WORD_LEN) {
                continue;
            }
            result.add(w);
        }
        return result;
    }
}
