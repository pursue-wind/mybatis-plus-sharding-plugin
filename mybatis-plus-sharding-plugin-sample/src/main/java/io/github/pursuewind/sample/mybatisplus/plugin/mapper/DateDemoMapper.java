package io.github.pursuewind.sample.mybatisplus.plugin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.pursuewind.sample.mybatisplus.plugin.domain.DateDemo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DateDemoMapper extends BaseMapper<DateDemo> {
}