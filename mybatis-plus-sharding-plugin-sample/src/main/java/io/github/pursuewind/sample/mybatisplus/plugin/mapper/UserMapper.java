package io.github.pursuewind.sample.mybatisplus.plugin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.pursuewind.sample.mybatisplus.plugin.domain.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author Chan
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}