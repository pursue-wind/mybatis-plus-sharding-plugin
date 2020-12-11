package io.github.pursuewind.mybatisplus.plugin.interceptor;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.core.toolkit.TableNameParser;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.google.common.base.CaseFormat;
import io.github.pursuewind.mybatisplus.plugin.support.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author Chan
 */
@Slf4j
@Data
@NoArgsConstructor
@SuppressWarnings({"rawtypes"})
public class TableShardingInnerInterceptor implements InnerInterceptor {
    private HashMap<String, Sharding> shardingStrategyMap = new HashMap<>(32);

    public TableShardingInnerInterceptor with(Sharding sharding) {
        shardingStrategyMap.put(sharding.getTableName(), sharding);
        return this;
    }

    @SneakyThrows
    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        PluginUtils.MPStatementHandler mpSh = PluginUtils.mpStatementHandler(sh);

        MappedStatement mappedStatement = mpSh.mappedStatement();
        ParameterHandler parameterHandler = mpSh.parameterHandler();
        BoundSql boundSql = mpSh.boundSql();
        PluginUtils.MPBoundSql mpBs = PluginUtils.mpBoundSql(boundSql);
        Object parameterObject = parameterHandler.getParameterObject();

        doSharding(mappedStatement, parameterObject, boundSql, mpBs);
    }

    private void doSharding(MappedStatement ms, Object obj, BoundSql boundSql, PluginUtils.MPBoundSql mpBs) {
        String originalSql = boundSql.getSql();

        TableNameParser parser = new TableNameParser(originalSql);

        Collection<String> tables = parser.tables();

        if (1 == tables.size()) {
            tables.stream()
                    .filter(table -> shardingStrategyMap.containsKey(table))
                    .findFirst()
                    .ifPresent(originTableName -> {
                        Sharding sharding = shardingStrategyMap.get(originTableName);
                        final SqlCommandType sqlCommandType = ms.getSqlCommandType();
                        if (originalSql.contains(String.format("(%s IN (", sharding.getShardingParam()))) {
                            //in
                        }
                        if (sqlCommandType == SqlCommandType.INSERT) {
                            String newSql = cHandler.handler(sqlCommandType, obj, originalSql, sharding);
                            mpBs.sql(newSql);
                        } else if (sqlCommandType == SqlCommandType.SELECT || sqlCommandType == SqlCommandType.UPDATE || sqlCommandType == SqlCommandType.DELETE) {
                            String newSql = rudHandler.handler(sqlCommandType, obj, originalSql, sharding);
                            mpBs.sql(newSql);
                        }
                    });
        }
    }

    SqlHandler cHandler = (sqlCommandType, obj, originalSql, sharding) -> {
        // 表名、参数名字和策略
        final String originTableName = sharding.getTableName();
        final String paramName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, sharding.getShardingParam());
        final BiFunction<String, Object, String> tableNameProcessor = sharding.getTableNameProcessor();
        Field[] fields = obj.getClass().getDeclaredFields();
        // 反射获得参数值
        for (Field field : fields) {
            field.setAccessible(true);
            if (field.getName().equals(paramName)) {
                try {
                    // 得到表名
                    String newTableName = tableNameProcessor.apply(originTableName, field.get(obj));
                    log.info("newTableName:{}", newTableName);
                    return StringUtils.replaceOnce(originalSql, originTableName, newTableName);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    e.printStackTrace();
                    throw new RuntimeException();
                }
            }
        }
        throw new RuntimeException();
    };

    SqlHandler rudHandler = (sqlCommandType, obj, originalSql, sharding) -> {
        // 表名、参数名字和策略
        final String originalTableName = sharding.getTableName();
        final String paramName = sharding.getShardingParam();
        final BiFunction<String, Object, String> tableNameProcessor = sharding.getTableNameProcessor();
        if (obj instanceof Map) {
            AbstractWrapper trans = obj2Wrapper(obj);
            // 找到分表字段在条件构造器中的位置，如果为 -1 ，则传参有错误
            int paramIndex = getParamIndex(originalSql, paramName);
            if (-1 == paramIndex) {
                throw new RuntimeException("分表注解，条件构建器中必须要有" + paramName + "字段");
            }
            Object paramVal = getParamVal(trans, paramIndex);
            String newTableName = tableNameProcessor.apply(originalTableName, paramVal);
            return sqlCommandType == SqlCommandType.UPDATE
                    ? StringUtils.replaceOnce(originalSql, "UPDATE " + originalTableName, "UPDATE  " + newTableName)
                    : StringUtils.replaceOnce(originalSql, "FROM " + originalTableName, "FROM " + newTableName);
        } else {
            // 处理单参数方法
            if (obj instanceof Integer || obj instanceof String) {

                // 找到分表字段在条件构造器中的位置，如果为 -1 ，则传参有错误
                int paramIndex = getParamIndex(originalSql, paramName);
                if (-1 == paramIndex) {
                    throw new RuntimeException("分表注解，条件构建器中必须要有" + paramName + "字段");
                }

                String newTableName = tableNameProcessor.apply(originalTableName, obj);
                log.info("newTableName:{}", newTableName);
                return sqlCommandType == SqlCommandType.UPDATE
                        ? StringUtils.replaceOnce(originalSql, "UPDATE " + originalTableName, "UPDATE  " + newTableName)
                        : StringUtils.replaceOnce(originalSql, "FROM " + originalTableName, "FROM " + newTableName);
            } else {
                throw new RuntimeException();
            }
        }
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

    @FunctionalInterface
    public interface SqlHandler {
        /**
         * 针对不同 SqlCommandType 作对应的处理
         *
         * @param sqlCommandType {@link SqlCommandType}
         * @param obj            mybatis 接口方法传入的对象，insert时为数据库的entity 查询修改时可能为列表参数
         * @param sql            原始sql
         * @return newSql
         */
        String handler(SqlCommandType sqlCommandType, Object obj, String sql, TableShardingInnerInterceptor.Sharding sharding);
    }

    @Data
    @AllArgsConstructor
    @Builder
    public static class Sharding {
        /** 原表名 如：user */
        private String tableName;
        /** 分表参数 如：user_id */
        private String shardingParam;
        /**
         * 原表名和分表参数的值处理，返回新表名
         * <code>
         * (tableName, paramVal) -> tableName + "_" + (int) paramVal % 10
         * </code>
         */
        private BiFunction<String, Object, String> tableNameProcessor;
    }
}