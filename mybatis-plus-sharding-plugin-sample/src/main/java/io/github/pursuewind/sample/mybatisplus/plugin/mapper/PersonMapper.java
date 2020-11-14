package io.github.pursuewind.sample.mybatisplus.plugin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.pursuewind.mybatisplus.plugin.support.TableSharding;
import io.github.pursuewind.sample.mybatisplus.plugin.domain.Person;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author Mireal
 */
@Mapper
@TableSharding(tableName = "person", paramName = "id", prefixParam = "type", strategy = PersonShardingStrategy.class)
public interface PersonMapper extends BaseMapper<Person> {
}