package com.back.backeddemo.service;

import com.back.backeddemo.common.BusinessException;
import com.back.backeddemo.entity.User;
import com.back.backeddemo.mapper.CommentMapper;
import com.back.backeddemo.mapper.MessageMapper;
import com.back.backeddemo.mapper.PostFavoriteMapper;
import com.back.backeddemo.mapper.PostLikeMapper;
import com.back.backeddemo.mapper.PostMapper;
import com.back.backeddemo.mapper.UserFollowMapper;
import com.back.backeddemo.mapper.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 账号注销。
 *
 * <p>处理原则：**个人数据彻底删除，公开内容保留但匿名化**。
 * <ul>
 *   <li>删除：私信、收藏、点赞、关注关系、账号本身</li>
 *   <li>保留但匿名：他发布的文章（作者置空，页面显示「已注销用户」）、他发过的评论</li>
 * </ul>
 * 这样既满足「用户有权删除自己的个人信息」，又不会让站点的内容凭空出现空洞。
 */
@Service
public class AccountService {

    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final PostFavoriteMapper favoriteMapper;
    private final PostLikeMapper likeMapper;
    private final UserFollowMapper followMapper;
    private final MessageMapper messageMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AccountService(UserMapper userMapper, PostMapper postMapper, CommentMapper commentMapper,
                          PostFavoriteMapper favoriteMapper, PostLikeMapper likeMapper,
                          UserFollowMapper followMapper, MessageMapper messageMapper) {
        this.userMapper = userMapper;
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
        this.favoriteMapper = favoriteMapper;
        this.likeMapper = likeMapper;
        this.followMapper = followMapper;
        this.messageMapper = messageMapper;
    }

    /** 注销账号（需密码二次确认）；管理员不允许自助注销，避免把站点锁死 */
    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(404, "账号不存在");
        }
        if ("ADMIN".equalsIgnoreCase(String.valueOf(user.getRole()))) {
            throw new BusinessException(400, "管理员账号不能自助注销，请先取消管理员身份");
        }
        if (password == null || !encoder.matches(password, user.getPassword())) {
            throw new BusinessException(400, "密码不正确，无法注销");
        }

        // 点赞数在 post 表里是冗余字段，删除前要先把他点过赞的文章计数扣掉，否则数字会对不上
        List<Long> likedPostIds = likeMapper.listLikedPostIds(userId);
        if (likedPostIds != null) {
            for (Long postId : likedPostIds) {
                postMapper.decrementLikes(postId);
            }
        }

        messageMapper.deleteByUser(userId);
        favoriteMapper.deleteByUser(userId);
        likeMapper.deleteByUser(userId);
        followMapper.deleteByUser(userId);
        postMapper.anonymizeAuthor(userId);
        commentMapper.anonymizeUser(userId);
        userMapper.deleteById(userId);
    }
}
