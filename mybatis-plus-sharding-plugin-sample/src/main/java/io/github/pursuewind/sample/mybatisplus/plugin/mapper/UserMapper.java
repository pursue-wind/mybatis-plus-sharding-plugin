package io.github.pursuewind.sample.mybatisplus.plugin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.pursuewind.mybatisplus.plugin.interceptor.IdModTenStrategy;
import io.github.pursuewind.mybatisplus.plugin.support.TableSharding;
import io.github.pursuewind.sample.mybatisplus.plugin.domain.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author Chan
 */
@TableSharding(tableName = "user", paramName = "id", strategy = IdModTenStrategy.class)
@Mapper
public interface UserMapper extends BaseMapper<User> {
}