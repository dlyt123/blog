package com.back.backeddemo.service;

import com.back.backeddemo.common.BusinessException;
import com.back.backeddemo.common.PageQuery;
import com.back.backeddemo.common.PageResult;
import com.back.backeddemo.common.Validate;
import com.back.backeddemo.entity.Post;
import com.back.backeddemo.entity.Series;
import com.back.backeddemo.entity.Tag;
import com.back.backeddemo.mapper.PostMapper;
import com.back.backeddemo.mapper.SeriesMapper;
import com.back.backeddemo.mapper.TagMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PostService {

    private final PostMapper postMapper;
    private final TagMapper tagMapper;
    private final SeriesMapper seriesMapper;
    private final NotificationService notificationService;
    private final SensitiveWordService sensitiveWordService;

    public PostService(PostMapper postMapper, TagMapper tagMapper, SeriesMapper seriesMapper,
                       NotificationService notificationService,
                       SensitiveWordService sensitiveWordService) {
        this.postMapper = postMapper;
        this.tagMapper = tagMapper;
        this.seriesMapper = seriesMapper;
        this.notificationService = notificationService;
        this.sensitiveWordService = sensitiveWordService;
    }

    /** 前台文章列表 */
    public PageResult<Post> list(Long categoryId, Long tagId, String keyword, String orderBy, int page, int pageSize) {
        // 即使调用方忘了归一化，这里再兜一层：page / pageSize 的边界规则只有 PageQuery 说了算
        PageQuery pq = PageQuery.of(page, pageSize);
        List<Post> posts = postMapper.list(categoryId, tagId, keyword, orderBy, pq.offset(), pq.size());
        fillTags(posts);
        return buildPage(posts, postMapper.count(categoryId, tagId, keyword), pq.page(), pq.size());
    }

    /** 后台文章列表（管理员看全部，博主只看自己的） */
    public PageResult<Post> listAdmin(Integer status, String keyword, Long authorId, int page, int pageSize) {
        PageQuery pq = PageQuery.of(page, pageSize);
        List<Post> posts = postMapper.listAdmin(status, keyword, authorId, pq.offset(), pq.size());
        return buildPage(posts, postMapper.countAdmin(status, keyword, authorId), pq.page(), pq.size());
    }

    /** 文章详情（含标签、上一篇下一篇） */
    public Post detail(Long id) {
        Post post = postMapper.findById(id);
        if (post == null) {
            throw new BusinessException(404, "文章不存在");
        }
        post.setTags(getTagNames(id));
        post.setTagIds(getTagIds(id));
        post.setPrev(postMapper.findPrev(id));
        post.setNext(postMapper.findNext(id));
        if (post.getSeriesId() != null) {
            Series s = seriesMapper.findById(post.getSeriesId());
            if (s != null) {
                post.setSeriesName(s.getName());
            }
        }
        return post;
    }

    public void incrementViews(Long id) {
        postMapper.incrementViews(id);
    }

    /** 归档（按年月分组） */
    public List<Map<String, Object>> archives() {
        List<Post> posts = postMapper.listArchives();
        Map<Integer, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Post p : posts) {
            LocalDateTime time = p.getPublishTime() != null ? p.getPublishTime() : p.getCreateTime();
            int year = time.getYear();
            int month = time.getMonthValue();
            Map<String, Object> yearMap = grouped.computeIfAbsent(year, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("year", year);
                m.put("months", new ArrayList<Map<String, Object>>());
                return m;
            });
            List<Map<String, Object>> months = (List<Map<String, Object>>) yearMap.get("months");
            Map<String, Object> monthMap = null;
            for (Map<String, Object> m : months) {
                if (month == (int) m.get("month")) {
                    monthMap = m;
                    break;
                }
            }
            if (monthMap == null) {
                monthMap = new LinkedHashMap<>();
                monthMap.put("month", month);
                monthMap.put("posts", new ArrayList<Map<String, Object>>());
                months.add(monthMap);
            }
            List<Map<String, Object>> monthPosts = (List<Map<String, Object>>) monthMap.get("posts");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("title", p.getTitle());
            item.put("createTime", time.toLocalDate().toString());
            monthPosts.add(item);
        }
        return new ArrayList<>(grouped.values());
    }

    public List<Post> listPopular(int limit) {
        return postMapper.listPopular(limit);
    }

    /** 相关推荐：按标签重合度 + 同分类 + 热度综合排序 */
    public List<Post> listRelated(Long id, int limit) {
        return postMapper.listRelated(id, limit);
    }

    /** 关注流：我关注的人发布的文章（分页） */
    public PageResult<Post> listByFollowees(Long followerId, int page, int size) {
        PageQuery pq = PageQuery.of(page, size);
        List<Post> list = postMapper.listByFollowees(followerId, pq.offset(), pq.size());
        fillTags(list);
        return buildPage(list, postMapper.countByFollowees(followerId), pq.page(), pq.size());
    }

    /** 某个系列下的文章（分页） */
    public PageResult<Post> listBySeries(Long seriesId, int page, int size) {
        PageQuery pq = PageQuery.of(page, size);
        List<Post> list = postMapper.listBySeries(seriesId, pq.offset(), pq.size());
        fillTags(list);
        return buildPage(list, postMapper.countBySeries(seriesId), pq.page(), pq.size());
    }

    /**
     * 创建文章（含标签关联），authorId 需在调用前已设置到 post 上。
     *
     * <p>2026-09-20 加了两道关卡：
     * <ol>
     *   <li><b>敏感词</b>：标题 / 摘要 / 正文一起校验，命中直接拦下不让保存。</li>
     *   <li><b>先审后发</b>：管理员免审；普通用户发文进入「待审核」，
     *       并且 status 保持 0（草稿）—— <b>所以前台看不到，审核通过后才可见</b>。</li>
     * </ol>
     */
    @Transactional
    public void create(Post post, List<Long> tagIds, boolean isAdmin) {
        // 标题必填：不然会一路插到数据库，靠 NOT NULL 约束报错（500）
        post.setTitle(Validate.requiredText(post.getTitle(), "文章标题"));
        sensitiveWordService.validateAll("发表文章",
                post.getTitle(), post.getSummary(), post.getContent());
        if (post.getStatus() == null) {
            post.setStatus(0);
        }
        if (post.getPinned() == null) {
            post.setPinned(0);
        }
        if (post.getRecommended() == null) {
            post.setRecommended(0);
        }
        if (isAdmin) {
            // 管理员自己的文章免审
            post.setAuditStatus(0);
        } else {
            post.setAuditStatus(1);
            post.setStatus(0);          // 待审核期间保持草稿态，前台不可见
            post.setPublishTime(null);
        }
        if (post.getStatus() == 1 && post.getPublishTime() == null) {
            post.setPublishTime(LocalDateTime.now());
        }
        postMapper.insert(post);
        saveTags(post.getId(), tagIds);
        if (post.getStatus() == 1) {
            notificationService.notifyNewPost(post);
        }
    }

    /** 更新文章（含标签关联），非管理员只能改自己的文章 */
    @Transactional
    public void update(Post post, List<Long> tagIds, Long currentUserId, boolean isAdmin) {
        checkOwner(post.getId(), currentUserId, isAdmin);
        // 同样先挡住空标题，别让它变成数据库的 NOT NULL 报错
        post.setTitle(Validate.requiredText(post.getTitle(), "文章标题"));
        sensitiveWordService.validateAll("修改文章",
                post.getTitle(), post.getSummary(), post.getContent());
        if (!isAdmin) {
            // 普通用户改动后要【复核】，但**保持原来的发布状态** ✗
            // —— 否则改个错别字文章就下架了，对正常作者太苛刻 ✓
            // 后台会给管理员标出「待复核」，管理员看过再决定 ✓
            post.setAuditStatus(1);
            post.setAuditRemark(null);
        }
        postMapper.update(post);
        if (tagIds != null) {
            saveTags(post.getId(), tagIds);
        }
    }

    /**
     * 审核文章（仅管理员，入口在 AdminPostController 里已做权限校验）。
     *
     * @param pass   true=通过并发布，false=驳回
     * @param remark 驳回理由（作者能看到）
     */
    @Transactional
    public void audit(Long id, boolean pass, String remark) {
        Post existing = postMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(404, "文章不存在");
        }
        Post up = new Post();
        up.setId(id);
        if (pass) {
            up.setAuditStatus(0);
            up.setAuditRemark(null);
            up.setStatus(1);
            up.setPublishTime(existing.getPublishTime() == null
                    ? LocalDateTime.now() : existing.getPublishTime());
            postMapper.update(up);
            // 审核通过才算「真正发布」，这时才通知订阅者
            notificationService.notifyNewPost(postMapper.findById(id));
        } else {
            up.setAuditStatus(2);
            up.setAuditRemark(remark == null || remark.isBlank() ? "内容不符合发布规范" : remark.trim());
            up.setStatus(0);
            postMapper.update(up);
        }
    }

    public void delete(Long id, Long currentUserId, boolean isAdmin) {
        checkOwner(id, currentUserId, isAdmin);
        postMapper.softDelete(id);
    }

    /** 回收站列表（仅管理员，故无需 owner 校验） */
    public PageResult<Post> listTrash(String keyword, int page, int pageSize) {
        PageQuery pq = PageQuery.of(page, pageSize);
        List<Post> posts = postMapper.listTrash(keyword, pq.offset(), pq.size());
        return buildPage(posts, postMapper.countTrash(keyword), pq.page(), pq.size());
    }

    /** 从回收站恢复文章（仅管理员） */
    public void restore(Long id) {
        postMapper.restore(id);
    }

    /** 彻底删除文章及其所有关联数据（仅管理员，不可恢复） */
    @Transactional
    public void hardDelete(Long id) {
        postMapper.deleteTags(id);
        postMapper.hardDeleteLikes(id);
        postMapper.hardDeleteFavorites(id);
        postMapper.hardDeleteComments(id);
        postMapper.hardDelete(id);
    }

    /** 上下架文章；首次发布时通知订阅者 */
    public void updateStatus(Long id, Integer status, Long currentUserId, boolean isAdmin) {
        checkOwner(id, currentUserId, isAdmin);
        if (status == 1) {
            Post p = postMapper.findById(id);
            if (p != null && p.getPublishTime() == null) {
                Post up = new Post();
                up.setId(id);
                up.setPublishTime(LocalDateTime.now());
                postMapper.update(up);
                // 首次发布：通知订阅者
                p.setPublishTime(LocalDateTime.now());
                notificationService.notifyNewPost(p);
            }
        }
        postMapper.updateStatus(id, status);
    }

    public void updatePinned(Long id, Integer pinned, Long currentUserId, boolean isAdmin) {
        checkOwner(id, currentUserId, isAdmin);
        postMapper.updatePinned(id, pinned);
    }

    public void updateRecommended(Long id, Integer recommended, Long currentUserId, boolean isAdmin) {
        checkOwner(id, currentUserId, isAdmin);
        postMapper.updateRecommended(id, recommended);
    }

    public void batchUpdateStatus(List<Long> ids, Integer status, Long currentUserId, boolean isAdmin) {
        checkBatchOwner(ids, currentUserId, isAdmin);
        postMapper.batchUpdateStatus(ids, status);
    }

    public void batchSoftDelete(List<Long> ids, Long currentUserId, boolean isAdmin) {
        checkBatchOwner(ids, currentUserId, isAdmin);
        postMapper.batchSoftDelete(ids);
    }

    public void batchUpdateCategory(List<Long> ids, Long categoryId, Long currentUserId, boolean isAdmin) {
        checkBatchOwner(ids, currentUserId, isAdmin);
        postMapper.batchUpdateCategory(ids, categoryId);
    }

    /** 校验当前用户是否有权操作某篇文章（管理员全部，博主仅自己的） */
    private void checkOwner(Long postId, Long currentUserId, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        Post p = postMapper.findById(postId);
        if (p == null) {
            throw new BusinessException(404, "文章不存在");
        }
        if (p.getAuthorId() == null || !p.getAuthorId().equals(currentUserId)) {
            throw new BusinessException(403, "无权限操作他人文章");
        }
    }

    /** 校验批量操作的文章是否都属于当前用户（管理员跳过） */
    private void checkBatchOwner(List<Long> ids, Long currentUserId, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            checkOwner(id, currentUserId, false);
        }
    }

    private void fillTags(List<Post> posts) {
        for (Post p : posts) {
            p.setTags(getTagNames(p.getId()));
        }
    }

    private List<String> getTagNames(Long postId) {
        List<Tag> tags = tagMapper.listByPostId(postId);
        List<String> names = new ArrayList<>();
        for (Tag t : tags) {
            names.add(t.getName());
        }
        return names;
    }

    private List<Long> getTagIds(Long postId) {
        List<Tag> tags = tagMapper.listByPostId(postId);
        List<Long> ids = new ArrayList<>();
        for (Tag t : tags) {
            ids.add(t.getId());
        }
        return ids;
    }

    private void saveTags(Long postId, List<Long> tagIds) {
        postMapper.deleteTags(postId);
        if (tagIds != null) {
            for (Long tagId : tagIds) {
                postMapper.insertTag(postId, tagId);
            }
        }
    }

    private PageResult<Post> buildPage(List<Post> list, long total, int page, int pageSize) {
        PageResult<Post> result = new PageResult<>();
        result.setList(list);
        result.setTotal(total);
        result.setPage(page);
        result.setPageSize(pageSize);
        return result;
    }
}