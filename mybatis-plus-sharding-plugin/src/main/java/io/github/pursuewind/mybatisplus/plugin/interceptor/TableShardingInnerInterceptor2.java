package io.github.pursuewind.mybatisplus.plugin.interceptor;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.parser.JsqlParserSupport;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Chan
 */
@Slf4j
@Data
@NoArgsConstructor
@SuppressWarnings({"rawtypes"})
public class TableShardingInnerInterceptor2 extends JsqlParserSupport implements InnerInterceptor {
    private HashMap<String, Sharding> shardingStrategyMap = new HashMap<>(32);

    public TableShardingInnerInterceptor2 with(Sharding sharding) {
        shardingStrategyMap.put(sharding.getTableName(), sharding);
        return this;
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        PluginUtils.MPBoundSql mpBs = PluginUtils.mpBoundSql(boundSql);
        Object parameterObject = boundSql.getParameterObject();
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        mpBs.sql(parserMulti(mpBs.sql(),
                ImmutableMap.of("obj", parameterObject, "mapping", parameterMappings)));
    }

    @SneakyThrows
    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        PluginUtils.MPStatementHandler mpSh = PluginUtils.mpStatementHandler(sh);
        MappedStatement ms = mpSh.mappedStatement();
        SqlCommandType sct = ms.getSqlCommandType();
        if (sct == SqlCommandType.INSERT || sct == SqlCommandType.UPDATE || sct == SqlCommandType.DELETE) {
            PluginUtils.MPBoundSql mpBs = mpSh.mPBoundSql();
            Object parameterObject = mpSh.mPBoundSql().parameterObject();
            List<ParameterMapping> parameterMappings = mpBs.parameterMappings();
            mpBs.sql(parserMulti(mpBs.sql(), ImmutableMap.of("obj", parameterObject, "mapping", parameterMappings)));
        }
    }


    /**
     * 新增
     */
    @Override
    protected void processInsert(Insert insert, int index, Object obj) {
        Table table = insert.getTable();
        String tableName = table.getName();
        Sharding sharding = shardingStrategyMap.get(tableName);
        Map<String, Object> map = (Map<String, Object>) obj;
        if (sharding != null) {
            // 表名、参数名字和策略
            final String paramName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, sharding.getShardingParam());
            final BiFunction<String, Object, String> tableNameProcessor = sharding.getTableNameProcessor();
            Object insertObj = map.get("obj");
            Field[] fields = insertObj.getClass().getDeclaredFields();
            // 反射获得参数值
            for (Field field : fields) {
                field.setAccessible(true);
                if (field.getName().equals(paramName)) {
                    try {
                        // 得到表名

                        String newTableName = tableNameProcessor.apply(tableName, field.get(insertObj));
                        log.info("newTableName:{}", newTableName);
                        table.setName(newTableName);
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        e.printStackTrace();
                        throw new RuntimeException();
                    }
                }
            }
        }
    }

    /**
     * 删除
     */
    @Override
    protected void processDelete(Delete delete, int index, Object obj) {
        Table table = delete.getTable();
        String tableName = table.getName();
        Expression expression = delete.getWhere();
        Map<String, Object> map = (Map<String, Object>) obj;
        doSharding(map, table, tableName, expression);
    }

    /**
     * 更新
     */
    @Override
    protected void processUpdate(Update update, int index, Object obj) {
        Table table = update.getTable();
        String tableName = table.getName();
        Expression expression = update.getWhere();
        Map<String, Object> map = (Map<String, Object>) obj;
        doSharding(map, table, tableName, expression);
    }


    /**
     * 查询
     */
    @Override
    protected void processSelect(Select select, int index, Object obj) {
        SelectBody selectBody = select.getSelectBody();
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;
            FromItem fromItem = plainSelect.getFromItem();
            Table table = (Table) fromItem;
            String tableName = table.getName(); // 表名
            Expression expression = plainSelect.getWhere();
            Map<String, Object> map = (Map<String, Object>) obj;
            doSharding(map, table, tableName, expression);
        }
    }

    private void doSharding(Map<String, Object> obj, Table table, String tableName, Expression expression) {
        String expressionStr = expression.toString();
        Sharding sharding = shardingStrategyMap.get(tableName);
        if (null != sharding) {
            String shardingParam = sharding.getShardingParam();
            BiFunction<String, Object, String> tableNameProcessor = sharding.getTableNameProcessor();

            //找到参数在sql中的位置
            int paramIndex = getParamIndex(expressionStr, shardingParam);
            if (-1 == paramIndex) {
                throw new RuntimeException(shardingParam + "not found");
            }
            Map<String, Object> map = obj;
            Object p = map.get("obj");
            if (p instanceof Map) {
                MapperMethod.ParamMap paramMap = (MapperMethod.ParamMap) p;
                List<ParameterMapping> parameterMappings = (List<ParameterMapping>) map.get("mapping");
                Map<String, ParameterMapping> mappingMap = parameterMappings.stream().collect(Collectors.toMap(ParameterMapping::getProperty, Function.identity()));
                if (null != mappingMap.get(shardingParam)) {
                    Object param = paramMap.get(shardingParam);
                    setNewTableName(table, tableName, tableNameProcessor, param);
                } else if (null != mappingMap.get("ew.paramNameValuePairs.MPGENVAL" + paramIndex)) {
                    // wrapper
                    AbstractWrapper ew = (AbstractWrapper) paramMap.get("ew");
                    Object param = ew.getParamNameValuePairs().get("MPGENVAL" + paramIndex);
                    setNewTableName(table, tableName, tableNameProcessor, param);
                }
            } else if (p instanceof Integer || p instanceof String) {

                setNewTableName(table, tableName, tableNameProcessor, p);
            }
        }
    }

    private void setNewTableName(Table table, String name, BiFunction<String, Object, String> tableNameProcessor, Object p) {
        String newTableName = tableNameProcessor.apply(name, p);
        table.setName(newTableName);
    }

    /**
     * 通过参数找到参数在SQL中的位置
     *
     * @param s     sql
     * @param param 参数
     * @return 找到参数在sql中是第几个问号
     */
    public int getParamIndex(String s, String param) {
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