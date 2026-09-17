package com.back.backeddemo.controller;

import com.back.backeddemo.common.Result;
import com.back.backeddemo.entity.Post;
import com.back.backeddemo.mapper.CategoryMapper;
import com.back.backeddemo.mapper.CommentMapper;
import com.back.backeddemo.mapper.LinkMapper;
import com.back.backeddemo.mapper.PostMapper;
import com.back.backeddemo.mapper.TagMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/stats")
public class StatsController {

    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final LinkMapper linkMapper;

    public StatsController(PostMapper postMapper, CommentMapper commentMapper,
                           CategoryMapper categoryMapper, TagMapper tagMapper, LinkMapper linkMapper) {
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
        this.linkMapper = linkMapper;
    }

    /** 访问量概览 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> data = new HashMap<>();
        // 统计为管理员专属接口，authorId 传 null 表示统计全部文章
        data.put("totalPosts", postMapper.countAdmin(null, null, null));      // 全部（含草稿）
        data.put("publishedPosts", postMapper.countAdmin(1, null, null));     // 已发布
        data.put("draftPosts", postMapper.countAdmin(0, null, null));         // 草稿
        data.put("totalComments", commentMapper.listAdmin(null).size());
        data.put("totalCategories", categoryMapper.list().size());
        data.put("totalTags", tagMapper.list().size());
        data.put("totalLinks", linkMapper.list().size());
        data.put("totalViews", postMapper.sumViews());
        return Result.success(data);
    }

    /** 热门文章排行 */
    @GetMapping("/popular")
    public Result<List<Post>> popular() {
        return Result.success(postMapper.listPopular(10));
    }
}
