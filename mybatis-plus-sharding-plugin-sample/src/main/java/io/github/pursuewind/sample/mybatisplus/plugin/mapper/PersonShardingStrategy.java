package io.github.pursuewind.sample.mybatisplus.plugin.mapper;

import io.github.pursuewind.mybatisplus.plugin.support.ShardingStrategy;

/**
 * @author Mireal
 */
public class PersonShardingStrategy implements ShardingStrategy {
    @Override
    public String getTableName(String tableName, Object idVal, Object personType) {
        int id = (int) idVal;
        String prefix = (String) personType;
        return prefix + "_" + tableName + "_" + ((id & 1) == 0 ? 1 : 2);
    }

    public static void main(String[] args) {
        System.out.println(3 & 1);
    }
}
