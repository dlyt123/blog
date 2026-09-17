package com.back.backeddemo.mapper;

import com.back.backeddemo.entity.Subscribe;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SubscribeMapper {

    int insert(Subscribe subscribe);

    /** 后台管理：全部订阅（含已退订） */
    List<Subscribe> list();

    /** 发信只发给未退订的 */
    List<Subscribe> listActive();

    Subscribe findByToken(@Param("token") String token);

    int unsubscribe(@Param("token") String token);

    int delete(Long id);
}
