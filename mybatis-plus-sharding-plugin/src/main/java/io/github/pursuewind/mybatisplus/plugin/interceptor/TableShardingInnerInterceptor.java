package io.github.pursuewind.mybatisplus.plugin.interceptor;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.google.common.base.CaseFormat;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.github.pursuewind.mybatisplus.plugin.support.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * @author Chan
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings({"rawtypes"})
public class TableShardingInnerInterceptor implements InnerInterceptor {

    private LoadingCache<String, String> cache = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(60, TimeUnit.MINUTES)
            .removalListener(removal -> log.info("[{} : {}] is evicted!", removal.getKey(), removal.getValue()))
            .build(new CacheLoader<String, String>() {
                @Override
                public String load(String key) {
                    // 若缓存中无需要的数据,会执行以下方法
                    return null;
                }
            });

    @SneakyThrows
    @Override
    public void beforeQuery(Executor executor,
                            MappedStatement ms,
                            Object param,
                            RowBounds rowBounds,
                            ResultHandler resultHandler,
                            BoundSql boundSql) {
//        PluginUtils.MPBoundSql mpBs = PluginUtils.mpBoundSql(boundSql);
//        doSharding(ms, param, boundSql, mpBs);
    }

    @SneakyThrows
    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        PluginUtils.MPStatementHandler mpSh = PluginUtils.mpStatementHandler(sh);
        MappedStatement ms = mpSh.mappedStatement();
        ParameterHandler parameterHandler = mpSh.parameterHandler();
        BoundSql boundSql = mpSh.boundSql();
        PluginUtils.MPBoundSql mpBs = PluginUtils.mpBoundSql(boundSql);
        doSharding(ms, parameterHandler.getParameterObject(), boundSql, mpBs);
    }

    private void doSharding(MappedStatement ms,
                            Object param,
                            BoundSql boundSql,
                            PluginUtils.MPBoundSql mpBs)
            throws ClassNotFoundException {
        String originalSql = boundSql.getSql();
        String id = ms.getId();
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
        SqlCommandType sqlCommandType = ms.getSqlCommandType();
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
        mpBs.sql(newSql);
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

        // 缓存
        String cacheKey = sql + trans.getParamNameValuePairs().toString();
        String sqlFromCache = cache.getIfPresent(cacheKey);
        if (null != sqlFromCache) {
            log.debug("Find SQL in Cache -> {}:{}", cacheKey, sqlFromCache);
            return sqlFromCache;
        }

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
                    ? StringUtils.replaceOnce(sql, "UPDATE " + originTableName, "UPDATE  " + tableName, s -> cache.put(cacheKey, s))
                    : StringUtils.replaceOnce(sql, "FROM " + originTableName, "FROM " + tableName, s -> cache.put(cacheKey, s));
        }
    };
    private FuncProcessor singleParamProcessor = (sqlCommandType, obj, sql, sharding, shardingStrategy) -> {
        // 获取注解上面的表名、参数名字和策略
        final String originTableName = sharding.tableName();
        final String paramName = sharding.paramName();
        final String prefixParam = sharding.prefixParam();

        if (obj instanceof Integer || obj instanceof String) {

            // 缓存
            String cacheKey = sql + obj.toString();
            String sqlFromCache = cache.getIfPresent(cacheKey);
            if (null != sqlFromCache) {
                log.debug("Find SQL in Cache -> {}:{}", cacheKey, sqlFromCache);
                return sqlFromCache;
            }

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
                    ? StringUtils.replaceOnce(sql, "UPDATE " + originTableName, "UPDATE  " + tableName, s -> cache.put(cacheKey, s))
                    : StringUtils.replaceOnce(sql, "FROM " + originTableName, "FROM " + tableName, s -> cache.put(cacheKey, s));
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

        // 缓存
        String cacheKey = sql + obj.toString();
        String sqlFromCache = cache.getIfPresent(cacheKey);
        if (null != sqlFromCache) {
//            log.info("Find SQL in Cache -> {}:{}", cacheKey, sqlFromCache);
            return sqlFromCache;
        }

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
        return StringUtils.replaceOnce(sql, originTableName, tableName, s -> cache.put(cacheKey, s));
    };

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