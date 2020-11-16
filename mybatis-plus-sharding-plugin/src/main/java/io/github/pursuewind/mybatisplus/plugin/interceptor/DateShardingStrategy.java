package io.github.pursuewind.mybatisplus.plugin.interceptor;

import io.github.pursuewind.mybatisplus.plugin.support.ShardingStrategy;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 简单的时间策略 eg: sourceTableName_2020_11
 *
 * @author Chan
 */
public class DateShardingStrategy implements ShardingStrategy {
    @Override
    public String getTableName(String tableName, Object param, Object prefixParam) {
        if (null == param) {
            LocalDate now = LocalDate.now();
            return tableName + "_" + now.getYear() + "_" + now.getMonth().getValue();
        } else {
            if (param instanceof Timestamp) {
                return tableName + "_" + (((Date) param).getYear() + 1900) + "_" + (((Date) param).getMonth() + 1);
            } else if (param instanceof Date) {
                return tableName + "_" + (((Date) param).getYear() + 1900) + "_" + (((Date) param).getMonth() + 1);
            } else if (param instanceof LocalDateTime) {
                return tableName + "_" + ((LocalDateTime) param).getYear() + "_" + ((LocalDateTime) param).getMonth().getValue();
            } else if (param instanceof LocalDate) {
                return tableName + "_" + ((LocalDate) param).getYear() + "_" + ((LocalDate) param).getMonth().getValue();
            } else {
                throw new RuntimeException("类型不支持");
            }
        }
    }
}
