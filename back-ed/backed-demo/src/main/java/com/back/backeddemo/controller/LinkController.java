package com.back.backeddemo.controller;

import com.back.backeddemo.common.Result;
import com.back.backeddemo.common.Validate;
import com.back.backeddemo.entity.Link;
import com.back.backeddemo.mapper.LinkMapper;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class LinkController {

    private final LinkMapper linkMapper;

    public LinkController(LinkMapper linkMapper) {
        this.linkMapper = linkMapper;
    }

    @GetMapping("/api/links")
    public Result<List<Link>> list() {
        return Result.success(linkMapper.list());
    }

    @PostMapping("/api/admin/links")
    public Result<Map<String, Long>> create(@RequestBody Link link) {
        link.setName(Validate.requiredText(link.getName(), "友链名称"));
        link.setUrl(Validate.requiredText(link.getUrl(), "友链地址"));
        linkMapper.insert(link);
        return Result.success(Map.of("id", link.getId()));
    }

    @PutMapping("/api/admin/links/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Link link) {
        // 名称/地址必填：否则动态 SET 可能拼成空语句，数据库会直接报语法错误
        link.setName(Validate.requiredText(link.getName(), "友链名称"));
        link.setUrl(Validate.requiredText(link.getUrl(), "友链地址"));
        link.setId(id);
        linkMapper.update(link);
        return Result.success();
    }

    @DeleteMapping("/api/admin/links/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        linkMapper.delete(id);
        return Result.success();
    }

    /** 友链存活检测：**并发**访问所有友链，返回是否可访问 */
    @PostMapping("/api/admin/links/check")
    public Result<List<Map<String, Object>>> check() {
        List<Link> links = linkMapper.list();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        // ⚠️ 必须并发。串行时「条数 × 6 秒」是上界：6 条友链最坏 36 秒，
        //    而前端 axios 只等 15 秒 —— 管理员看到的是「请求超时」，
        //    可服务端其实还在跑。并发后总耗时就≈最慢的那一条。
        List<Map<String, Object>> results = links.parallelStream().map(l -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", l.getId());
            r.put("name", l.getName());
            r.put("url", l.getUrl());
            boolean ok = false;
            int status = 0;
            String err = "";
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(l.getUrl()))
                        .timeout(Duration.ofSeconds(6))
                        .header("User-Agent", "Mozilla/5.0 (compatible; BlogLinkChecker/1.0)")
                        .GET().build();
                HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
                status = resp.statusCode();
                ok = status >= 200 && status < 400;
            } catch (Exception e) {
                err = e.getMessage() == null ? "连接失败" : e.getMessage();
            }
            r.put("ok", ok);
            r.put("status", status);
            r.put("error", err);
            return r;
        }).collect(Collectors.toList());
        return Result.success(results);
    }
}
