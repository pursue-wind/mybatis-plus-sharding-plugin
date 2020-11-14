package io.github.pursuewind.sample.mybatisplus.plugin.mapper;

import io.github.pursuewind.mybatisplus.plugin.support.ShardingStrategy;

import java.time.LocalDate;

/**
 * @author Mireal
 */
public class DateShardingStrategy implements ShardingStrategy {
    @Override
    public String getTableName(String tableName, Object param, Object prefixParam) {
        LocalDate now = LocalDate.now();
        return tableName + "_" + now.getYear() + "_" + now.getMonth().getValue();
    }
}
