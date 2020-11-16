package io.github.pursuewind.sample.mybatisplus.plugin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.pursuewind.mybatisplus.plugin.interceptor.DateShardingStrategy;
import io.github.pursuewind.mybatisplus.plugin.support.TableSharding;
import io.github.pursuewind.sample.mybatisplus.plugin.domain.DateDemo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@TableSharding(tableName = "date_demo", paramName = "createTime", strategy = DateShardingStrategy.class)
public interface DateDemoMapper extends BaseMapper<DateDemo> {
}