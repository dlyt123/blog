package com.back.backeddemo.mapper;

import com.back.backeddemo.entity.User;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface UserMapper {

    User findByUsername(@Param("username") String username);

    User findById(@Param("id") Long id);

    /** 找回密码：按邮箱查用户 */
    User findByEmail(@Param("email") String email);

    int insert(User user);

    int update(User user);

    /** 写入密码重置令牌与过期时间 */
    int saveResetToken(@Param("id") Long id, @Param("token") String token,
                       @Param("expire") LocalDateTime expire);

    /** 按重置令牌查用户（用于校验令牌是否有效） */
    User findByResetToken(@Param("token") String token);

    /** 重置密码并清掉令牌 */
    int resetPassword(@Param("id") Long id, @Param("password") String password);

    /** 注销账号：删除用户 */
    int deleteById(@Param("id") Long id);
}
