package com.back.backeddemo.controller;

import com.back.backeddemo.entity.Post;
import com.back.backeddemo.mapper.PostMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 数据导出（仅管理员）
 * GET /api/admin/export → 把全部文章打包成 Markdown 的 zip 下载
 */
@RestController
public class ExportController {

    private final PostMapper postMapper;

    public ExportController(PostMapper postMapper) {
        this.postMapper = postMapper;
    }

    @GetMapping("/api/admin/export")
    public void export(HttpServletResponse response) throws IOException {
        List<Post> posts = postMapper.listAllForExport();

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=blog-posts-" + LocalDateTime.now().toLocalDate() + ".zip");

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            for (Post p : posts) {
                String name = p.getId() + "-" + sanitize(p.getTitle()) + ".md";
                ZipEntry entry = new ZipEntry(name);
                zos.putNextEntry(entry);
                zos.write(buildMarkdown(p).getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            zos.finish();
        }
    }

    private String buildMarkdown(Post p) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("title: ").append(p.getTitle()).append('\n');
        if (p.getCategoryName() != null) {
            sb.append("category: ").append(p.getCategoryName()).append('\n');
        }
        if (p.getSummary() != null && !p.getSummary().isBlank()) {
            sb.append("summary: ").append(p.getSummary()).append('\n');
        }
        sb.append("status: ").append(p.getStatus() != null && p.getStatus() == 1 ? "已发布" : "草稿").append('\n');
        if (p.getPublishTime() != null) {
            sb.append("published: ").append(p.getPublishTime()).append('\n');
        }
        sb.append("---\n\n");
        sb.append(p.getContent() == null ? "" : p.getContent());
        sb.append('\n');
        return sb.toString();
    }

    /** 标题里的非法文件名字符替换掉，避免 zip 条目名出错 */
    private String sanitize(String title) {
        String t = title == null || title.isBlank() ? "untitled" : title.trim();
        return t.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "-").substring(0, Math.min(t.length(), 40));
    }
}
