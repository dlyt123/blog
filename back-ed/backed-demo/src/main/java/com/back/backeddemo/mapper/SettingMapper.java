package com.back.backeddemo.mapper;

import com.back.backeddemo.entity.Setting;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SettingMapper {

    List<Setting> list();

    Setting findByKey(@Param("key") String key);

    int insert(Setting setting);

    int update(Setting setting);
}
