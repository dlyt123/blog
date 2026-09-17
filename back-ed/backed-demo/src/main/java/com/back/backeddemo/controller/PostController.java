package com.back.backeddemo.controller;

import com.back.backeddemo.common.PageResult;
import com.back.backeddemo.common.Result;
import com.back.backeddemo.entity.Post;
import com.back.backeddemo.mapper.SettingMapper;
import com.back.backeddemo.service.PostService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PostController {

    private final PostService postService;
    private final SettingMapper settingMapper;

    public PostController(PostService postService, SettingMapper settingMapper) {
        this.postService = postService;
        this.settingMapper = settingMapper;
    }

    /** 文章列表 */
    @GetMapping("/posts")
    public Result<PageResult<Post>> list(@RequestParam(required = false) Long categoryId,
                                         @RequestParam(required = false) Long tagId,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false, defaultValue = "latest") String orderBy,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(postService.list(categoryId, tagId, keyword, orderBy, page, pageSize));
    }

    /** 文章详情（浏览量 +1） */
    @GetMapping("/posts/{id}")
    public Result<Post> detail(@PathVariable Long id) {
        Post post = postService.detail(id);
        postService.incrementViews(id);
        return Result.success(post);
    }

    /** 归档 */
    @GetMapping("/archives")
    public Result<List<Map<String, Object>>> archives() {
        return Result.success(postService.archives());
    }

    /** 全文搜索 */
    @GetMapping("/search")
    public Result<PageResult<Post>> search(@RequestParam String keyword,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(postService.list(null, null, keyword, "latest", page, pageSize));
    }

    /** 关于我 */
    @GetMapping("/about")
    public Result<Map<String, String>> about() {
        var setting = settingMapper.findByKey("about");
        String content = setting != null ? setting.getSettingValue() : "这里还没有填写关于我的内容。";
        return Result.success(Map.of("content", content));
    }

    /** 热门文章（公开，首页侧边栏用）：按阅读量倒序取前 N 篇 */
    @GetMapping("/popular")
    public Result<List<Post>> popular(@RequestParam(defaultValue = "5") int limit) {
        int n = Math.min(Math.max(limit, 1), 20);
        return Result.success(postService.listPopular(n));
    }

    /** 相关推荐（公开，文章详情页底部用） */
    @GetMapping("/posts/{id}/related")
    public Result<List<Post>> related(@PathVariable Long id,
                                      @RequestParam(defaultValue = "5") int limit) {
        int n = Math.min(Math.max(limit, 1), 10);
        return Result.success(postService.listRelated(id, n));
    }
}
