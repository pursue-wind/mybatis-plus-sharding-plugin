package io.github.pursuewind.mybatisplus.plugin.interceptor;

import io.github.pursuewind.mybatisplus.plugin.support.ShardingStrategy;

import java.time.LocalDate;

/**
 * 简单的时间策略 eg: sourceTableName_2020_11
 *
 * @author Chan
 */
public class DateShardingStrategy implements ShardingStrategy {
    @Override
    public String getTableName(String tableName, Object param, Object prefixParam) {
        LocalDate now = LocalDate.now();
        return tableName + "_" + now.getYear() + "_" + now.getMonth().getValue();
    }
}
