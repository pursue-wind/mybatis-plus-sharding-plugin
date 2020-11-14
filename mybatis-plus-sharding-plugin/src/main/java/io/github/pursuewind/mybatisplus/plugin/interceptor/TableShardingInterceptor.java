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
package io.github.pursuewind.mybatisplus.plugin.interceptor;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.google.common.base.CaseFormat;
import io.github.pursuewind.mybatisplus.plugin.support.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.Map;
import java.util.Properties;

/**
 * 分表策略拦截
 *
 * @author chen
 */
@Component
@Slf4j(topic = "分表拦截器")
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
public class TableShardingInterceptor implements Interceptor {

    /**
     * intercept
     *
     * @param invocation
     * @return
     * @throws Throwable
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        MetaObject metaStatementHandler = MetaObject.forObject(
                statementHandler, SystemMetaObject.DEFAULT_OBJECT_FACTORY,
                SystemMetaObject.DEFAULT_OBJECT_WRAPPER_FACTORY,
                new DefaultReflectorFactory());

        Object parameterObject = metaStatementHandler.getValue("delegate.boundSql.parameterObject");
        doSharding(metaStatementHandler, parameterObject);
        // 给下一个拦截器处理
        return invocation.proceed();
    }

    /**
     * plugin
     *
     * @param arg0
     * @return
     */
    @Override
    public Object plugin(Object arg0) {
        return arg0 instanceof StatementHandler ? Plugin.wrap(arg0, this) : arg0;
    }

    /**
     * setProperties
     *
     * @param arg0
     */
    @Override
    public void setProperties(Properties arg0) {
    }

    /**
     * select update delete语句处理
     * <p>
     * obj: mybatis参数对象，可能为 list
     */
    private FuncProcessor rudProcessor = (sqlCommandType, obj, sql, sharding, shardingStrategy) -> {
        // 获取注解上面的表名、参数名字和策略
        final String originTableName = sharding.tableName();
        final String paramName = sharding.paramName();
        final String prefixParam = sharding.prefixParam();

        AbstractWrapper trans = obj2Wrapper(obj);

        // 如果含有表名参数，直接用于替换表名
        int idx = getParamIndex(sql, ShardingStrategy.TABLE_NAME);
        if (idx != -1) {
            String newTableName = String.valueOf(getParamVal(trans, idx));
            String newSql = StringUtils.replaceOnce(sql, originTableName, newTableName);
            if (newSql.contains(ShardingStrategy.TABLE_NAME + " = ?")) {
                newSql = StringUtils.replaceOnce(newSql, ShardingStrategy.TABLE_NAME + " = ?", "'" + newTableName + "' = ?");
            }
            return newSql;
        }
        // 没有表名参数，查询条件构造器中的分表字段和前缀字段
        else {
            // 找到分表字段在条件构造器中的位置，如果为 -1 ，则传参有错误
            int paramIndex = getParamIndex(sql, CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, paramName));
            if (-1 == paramIndex) {
                throw new RuntimeException("分表注解，条件构建器中必须要有" + paramName + "对应数据库的字段");
            }

            Object paramVal = getParamVal(trans, paramIndex);
            Object prefixVal = "";
            if (!StringUtils.isEmpty(prefixParam)) {
                int prefixParamIndex = getParamIndex(sql, CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, prefixParam));
                if (-1 == prefixParamIndex) {
                    throw new RuntimeException("分表注解 prefixParam 不为空，条件构建器中必须要有" + prefixParam + "对应数据库的字段");
                }
                prefixVal = getParamVal(trans, prefixParamIndex);
            }
            TwoTuple<Object, Object> tuple = TwoTuple.of(paramVal, prefixVal);
            String tableName = shardingStrategy.getTableName(originTableName, tuple.getFirst(), tuple.getSecond());
            return sqlCommandType == SqlCommandType.UPDATE
                    ? StringUtils.replaceOnce(sql, "UPDATE " + originTableName, "UPDATE  " + tableName)
                    : StringUtils.replaceOnce(sql, "FROM " + originTableName, "FROM " + tableName);
        }
    };
    private FuncProcessor singleParamProcessor = (sqlCommandType, obj, sql, sharding, shardingStrategy) -> {
        // 获取注解上面的表名、参数名字和策略
        final String originTableName = sharding.tableName();
        final String paramName = sharding.paramName();
        final String prefixParam = sharding.prefixParam();

        if (obj instanceof Integer || obj instanceof String) {
            // 找到分表字段在条件构造器中的位置，如果为 -1 ，则传参有错误
            int paramIndex = getParamIndex(sql, CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, paramName));
            if (-1 == paramIndex) {
                throw new RuntimeException("分表注解，条件构建器中必须要有" + paramName + "对应数据库的字段");
            }
            if (!StringUtils.isEmpty(prefixParam)) {
                int prefixParamIndex = getParamIndex(sql, CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, prefixParam));
                if (-1 == prefixParamIndex) {
                    throw new RuntimeException("分表注解 prefixParam 不为空，条件构建器中必须要有" + prefixParam + "对应数据库的字段");
                }
            }
            TwoTuple<Object, Object> tuple = TwoTuple.of(obj, null);
            String tableName = shardingStrategy.getTableName(originTableName, tuple.getFirst(), tuple.getSecond());
            return sqlCommandType == SqlCommandType.UPDATE
                    ? StringUtils.replaceOnce(sql, "UPDATE " + originTableName, "UPDATE  " + tableName)
                    : StringUtils.replaceOnce(sql, "FROM " + originTableName, "FROM " + tableName);
        } else {
            throw new RuntimeException("分表注解 prefixParam 不为空，SQL 必须要有" + prefixParam + "对应数据库的字段");
        }
    };

    /**
     * insert 语句处理 obj此时为插入的对象，从对象中取出对应的值
     */
    private FuncProcessor cProcessor = (sqlCommandType, obj, sql, sharding, shardingStrategy) -> {
        // 获取注解上面的表名、参数名字和策略
        final String originTableName = sharding.tableName();
        final String paramName = sharding.paramName();
        final String prefixParam = sharding.prefixParam();

        Field[] fields = obj.getClass().getDeclaredFields();
        TwoTuple<Object, Object> tuple = new TwoTuple<>();
        // 反射获得参数值
        for (Field field : fields) {
            field.setAccessible(true);
            if (field.getName().equals(paramName)) {
                try {
                    tuple.setFirst(field.get(obj));
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            if (!StringUtils.isEmpty(prefixParam)) {
                if (field.getName().equals(prefixParam)) {
                    try {
                        tuple.setSecond(field.get(obj));
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // 根据策略得到表名
        String tableName = shardingStrategy.getTableName(originTableName, tuple.getFirst(), tuple.getSecond());
        return StringUtils.replaceOnce(sql, originTableName, tableName);
    };

    /**
     * 分表处理
     *
     * @param metaStatementHandler MetaObject
     * @param param                Object
     * @throws ClassNotFoundException
     */
    private void doSharding(MetaObject metaStatementHandler, Object param) throws ClassNotFoundException {
        String originalSql = (String) metaStatementHandler.getValue("delegate.boundSql.sql");
        if (!StringUtils.isEmpty(originalSql)) {
            MappedStatement mappedStatement = (MappedStatement) metaStatementHandler.getValue("delegate.mappedStatement");
            String id = mappedStatement.getId();
            String className = id.substring(0, id.lastIndexOf("."));
            Class<?> classObj = Class.forName(className);
            // 根据配置自动生成分表SQL
            TableSharding sharding = classObj.getAnnotation(TableSharding.class);
            if (sharding == null) {
                return;
            }
            // 获取注解上面的表名、参数名字和策略
            Class<? extends ShardingStrategy> strategy = sharding.strategy();
            ShardingStrategy shardingStrategy = null;
            try {
                shardingStrategy = (ShardingStrategy) Class.forName(strategy.getName()).newInstance();
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                e.printStackTrace();
            }
            if (shardingStrategy == null) {
                return;
            }

            String newSql = originalSql;
            SqlCommandType sqlCommandType = (SqlCommandType) metaStatementHandler.getValue("delegate.parameterHandler.sqlCommandType");
            switch (sqlCommandType) {
                case INSERT:
                    newSql = cProcessor.proccessor(sqlCommandType, param, originalSql, sharding, shardingStrategy);
                    break;
                case SELECT:
                case UPDATE:
                case DELETE:
                    if (param instanceof Map) {
                        newSql = rudProcessor.proccessor(sqlCommandType, param, originalSql, sharding, shardingStrategy);
                    } else {
                        newSql = singleParamProcessor.proccessor(sqlCommandType, param, originalSql, sharding, shardingStrategy);
                    }
                    break;
                default:
                    // do nothing
                    break;
            }

            log.info("NEW SQL：" + newSql);
            // 设置新sql
            metaStatementHandler.setValue("delegate.boundSql.sql", newSql);
        }
    }


    /**
     * 根据位置取出当初传入条件构造器的值
     *
     * @param wrapper
     * @param index
     * @return
     */
    public Object getParamVal(AbstractWrapper wrapper, Integer index) {
        return wrapper.getParamNameValuePairs().get(Constants.WRAPPER_PARAM + index);
    }

    /**
     * 通过参数找到参数在SQL中的位置
     *
     * @param s     sql
     * @param param 参数
     * @return 找到参数在sql中是第几个问号
     */
    public int getParamIndex(String s, String param) {
        String[] strArr = s.split("WHERE");
        if (strArr.length < 2) {
            throw new RuntimeException("SQL 通过WHERE切分数组长度不正确");
        }
        // 只取where后面的数组
        s = strArr[1];

        int index = 1;
        int pos = 0;
        boolean flag = false;
        String str = s.contains("=?") ? param + "=" : param + " = ";
        while (-1 != pos) {
            pos = s.indexOf("?", pos + 1);
            int beginIndex = pos - str.length();
            if (beginIndex < 0) {
                continue;
            }
            if (str.equals(s.substring(beginIndex, pos))) {
                flag = true;
                break;
            } else {
                index++;
            }
        }
        return flag ? index : -1;
    }

    /**
     * 用于判断 select 语句是否使用条件构造器并返回 AbstractWrapper
     *
     * @param obj
     * @return AbstractWrapper
     */
    private AbstractWrapper obj2Wrapper(Object obj) {
        if (obj instanceof Map) {
            Map map = ((Map) obj);
            if (map.containsKey(Constants.WRAPPER)) {
                Object wrapper = map.get(Constants.WRAPPER);
                if (wrapper instanceof AbstractWrapper) {
                    return (AbstractWrapper) wrapper;
                }
            }
        }
        return null;
    }
}
