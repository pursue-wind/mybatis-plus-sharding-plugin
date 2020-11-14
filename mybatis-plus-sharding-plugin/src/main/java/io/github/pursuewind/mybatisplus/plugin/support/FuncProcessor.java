/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.pursuewind.mybatisplus.plugin.support;

import org.apache.ibatis.mapping.SqlCommandType;

/**
 * @author chen
 */
@FunctionalInterface
public interface FuncProcessor {
    /**
     * 针对不同 SqlCommandType 作对应的处理
     *
     * @param sqlCommandType {@link SqlCommandType}
     * @param obj            mybatis 接口方法传入的对象，insert时为数据库的entity 查询修改时可能为列表参数
     * @param sql            原始sql
     * @param tableSharding  分表策略
     * @return newSql
     */
    String proccessor(SqlCommandType sqlCommandType, Object obj, String sql, TableSharding tableSharding, ShardingStrategy shardingStrategy);
}
