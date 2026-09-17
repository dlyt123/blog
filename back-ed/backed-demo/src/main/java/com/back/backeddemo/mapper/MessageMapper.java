package com.back.backeddemo.mapper;

import com.back.backeddemo.entity.Message;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface MessageMapper {

    int insert(Message message);

    /** 会话列表：每个对端一条，带最后一条消息与未读数 */
    List<Message> conversations(@Param("userId") Long userId);

    /** 与某人的聊天记录（按时间正序） */
    List<Message> chat(@Param("userId") Long userId, @Param("otherId") Long otherId,
                       @Param("offset") int offset, @Param("limit") int limit);

    long countChat(@Param("userId") Long userId, @Param("otherId") Long otherId);

    /** 把某人发给我的消息标记为已读 */
    int markRead(@Param("userId") Long userId, @Param("otherId") Long otherId);

    long unreadCount(@Param("userId") Long userId);

    /** 注销账号时清理该用户的所有私信 */
    int deleteByUser(@Param("userId") Long userId);
}
