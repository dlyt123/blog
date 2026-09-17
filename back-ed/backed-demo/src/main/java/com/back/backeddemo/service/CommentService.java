package com.back.backeddemo.service;

import com.back.backeddemo.common.BusinessException;
import com.back.backeddemo.common.PageResult;
import com.back.backeddemo.entity.Comment;
import com.back.backeddemo.entity.User;
import com.back.backeddemo.mapper.CommentMapper;
import com.back.backeddemo.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CommentService {

    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final SensitiveWordService sensitiveWordService;
    private final NotificationService notificationService;

    public CommentService(CommentMapper commentMapper, UserMapper userMapper,
                          SensitiveWordService sensitiveWordService,
                          NotificationService notificationService) {
        this.commentMapper = commentMapper;
        this.userMapper = userMapper;
        this.sensitiveWordService = sensitiveWordService;
        this.notificationService = notificationService;
    }

    /**
     * 前台评论列表（顶层评论分页，楼中楼回复随父评论一起返回）
     */
    public PageResult<Comment> listByPost(Long postId, int page, int size) {
        int offset = (Math.max(page, 1) - 1) * size;
        List<Comment> tops = commentMapper.listTopByPostId(postId, 1, offset, size);
        long total = commentMapper.countTopByPostId(postId, 1);

        if (tops != null && !tops.isEmpty()) {
            List<Long> ids = new ArrayList<>();
            for (Comment t : tops) {
                ids.add(t.getId());
            }
            Map<Long, Comment> map = new LinkedHashMap<>();
            for (Comment t : tops) {
                map.put(t.getId(), t);
            }
            for (Comment r : commentMapper.listRepliesByParentIds(ids)) {
                Comment parent = map.get(r.getParentId());
                if (parent != null) {
                    if (parent.getReplies() == null) {
                        parent.setReplies(new ArrayList<>());
                    }
                    parent.getReplies().add(r);
                }
            }
        }

        PageResult<Comment> result = new PageResult<>();
        result.setList(tops == null ? new ArrayList<>() : tops);
        result.setTotal(total);
        result.setPage(Math.max(page, 1));
        result.setPageSize(size);
        return result;
    }

    public List<Comment> listAdmin(Integer status) {
        return commentMapper.listAdmin(status);
    }

    /** 最新评论（首页侧边栏） */
    public List<Comment> listRecent(int limit) {
        return commentMapper.listRecent(limit);
    }

    public void add(Comment comment, Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录后再评论");
        }
        // 昵称与头像自动取自登录用户的真实信息（同时记录 userId，读取时实时联表取最新值）
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(401, "用户不存在，请重新登录");
        }
        comment.setUserId(userId);
        comment.setNickname(user.getNickname() != null ? user.getNickname() : user.getUsername());
        comment.setAvatar(user.getAvatar());
        // 前台评论邮箱仅作占位，不作为必填；统一使用用户邮箱
        comment.setEmail(user.getEmail());
        if (comment.getParentId() == null) {
            comment.setParentId(0L);
        }
        // 敏感词过滤：评论是「发即显示」（不先审后发），所以命中敏感词就直接拦下不让发布
        sensitiveWordService.validate(comment.getContent(), "发表评论");
        // 免审核：直接通过
        comment.setStatus(1);
        commentMapper.insert(comment);
        // 邮件通知：回复别人 → 通知被回复者；顶层评论 → 通知文章作者（未配置 SMTP 时自动跳过）
        notificationService.notifyComment(comment, userId);
    }

    public void audit(Long id, Integer status) {
        commentMapper.updateStatus(id, status);
    }

    public void delete(Long id) {
        commentMapper.delete(id);
    }
}
