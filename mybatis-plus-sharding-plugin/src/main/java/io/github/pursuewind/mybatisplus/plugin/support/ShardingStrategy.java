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

/**
 * 分表策略服务接口
 *
 * @author chen
 */
@FunctionalInterface
public interface ShardingStrategy {
    /**
     * <code>
     *  exampleMapper.selectOne(
     *      Wrappers.<ClassName>query()
     *              .eq(ShardingStrategy.TABLE_NAME, "test_2")
     *              .eq("xx", xx));
     * </code>
     * 使用分表注解后，条件构造器传入此参数使用传入的固定的表名进行查询
     */
    String TABLE_NAME = "__TABLE_NAME";

    /**
     * 返回新的表名
     *
     * @param tableName   原表名
     * @param param       处理参数对应的值
     * @param prefixParam 前缀参数值对应的值
     * @return 新表名
     */
    String getTableName(String tableName, Object param, Object prefixParam);
}

