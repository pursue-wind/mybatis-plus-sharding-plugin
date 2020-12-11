package io.github.pursuewind.sample.mybatisplus.plugin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.pursuewind.sample.mybatisplus.plugin.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @author Chan
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("select * from user where id = #{id} and name = #{name} ")
    User selectByIdAndName(@Param("id") Integer id, @Param("name") String name);
}