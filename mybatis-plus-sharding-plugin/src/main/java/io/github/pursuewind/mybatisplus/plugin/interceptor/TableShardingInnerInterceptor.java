package io.github.pursuewind.mybatisplus.plugin.interceptor;

import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.parser.JsqlParserSupport;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.google.common.base.CaseFormat;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.lang.reflect.Field;
import java.sql.Connection;
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
public class TableShardingInnerInterceptor extends JsqlParserSupport implements InnerInterceptor {
    private static final String WRAPPER_PARAM_FORMAT = "%s.paramNameValuePairs.%s%d";
    private HashMap<String, Sharding> shardingStrategyMap = new HashMap<>(32);

    public TableShardingInnerInterceptor with(Sharding sharding) {
        shardingStrategyMap.put(sharding.getTableName(), sharding);
        return this;
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        PluginUtils.MPBoundSql mpBs = PluginUtils.mpBoundSql(boundSql);
        mpBs.sql(parserMulti(mpBs.sql(), ShardingHandler.of(ms, boundSql)));
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        PluginUtils.MPStatementHandler mpSh = PluginUtils.mpStatementHandler(sh);
        MappedStatement ms = mpSh.mappedStatement();
        SqlCommandType sct = ms.getSqlCommandType();
        if (sct == SqlCommandType.INSERT || sct == SqlCommandType.UPDATE || sct == SqlCommandType.DELETE) {
            PluginUtils.MPBoundSql mpBs = mpSh.mPBoundSql();
            mpBs.sql(parserMulti(mpBs.sql(), ShardingHandler.of(ms, mpSh.boundSql())));
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
        ShardingHandler helper = (ShardingHandler) obj;
        BoundSql boundSql = helper.getBoundSql();
        Object insertObj = boundSql.getParameterObject();
        if (sharding != null) {
            final String paramName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, sharding.getShardingParam());
            final BiFunction<String, Object, String> tableNameProcessor = sharding.getTableNameProcessor();
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
        ShardingHandler helper = (ShardingHandler) obj;
        BoundSql boundSql = helper.getBoundSql();
        MappedStatement mappedStatement = helper.getMappedStatement();
        doSharding(boundSql, table, tableName, expression, mappedStatement);
    }

    /**
     * 更新
     */
    @Override
    protected void processUpdate(Update update, int index, Object obj) {
        Table table = update.getTable();
        String tableName = table.getName();
        Expression expression = update.getWhere();
        ShardingHandler helper = (ShardingHandler) obj;
        BoundSql boundSql = helper.getBoundSql();
        MappedStatement mappedStatement = helper.getMappedStatement();
        doSharding(boundSql, table, tableName, expression, mappedStatement);
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
            ShardingHandler helper = (ShardingHandler) obj;
            BoundSql boundSql = helper.getBoundSql();
            MappedStatement mappedStatement = helper.getMappedStatement();
            doSharding(boundSql, table, tableName, expression, mappedStatement);
        }
    }

    private void doSharding(BoundSql boundSql, Table table, String tableName, Expression expression, MappedStatement mappedStatement) {
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

            List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
            Object parameterObject = boundSql.getParameterObject();

            if (parameterObject instanceof Map) {
                MapperMethod.ParamMap paramMap = (MapperMethod.ParamMap) parameterObject;

                // key: ParameterMapping#property  value: ParameterMapping
                Map<String, ParameterMapping> mappingMap = parameterMappings.stream()
                        .collect(Collectors.toMap(ParameterMapping::getProperty, Function.identity()));

                if (null != mappingMap.get(shardingParam)) {
                    Object param = paramMap.get(shardingParam);
                    setNewTableName(table, tableName, tableNameProcessor, param);
                } else {
                    String key = String.format(WRAPPER_PARAM_FORMAT, Constants.WRAPPER, Constants.WRAPPER_PARAM, paramIndex);
                    if (null != mappingMap.get(key)) {
                        MetaObject metaObject = mappedStatement.getConfiguration().newMetaObject(parameterObject);
                        Object param = metaObject.getValue(key);
                        setNewTableName(table, tableName, tableNameProcessor, param);
                    }
                }
            } else {
                setNewTableName(table, tableName, tableNameProcessor, parameterObject);
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
    @AllArgsConstructor(staticName = "of")
    private static class ShardingHandler {
        private MappedStatement mappedStatement;
        private BoundSql boundSql;
    }

    @Data
    @AllArgsConstructor
    @Builder
    public static class Sharding {
        /**
         * 原表名 如：user
         */
        private String tableName;
        /**
         * 分表参数 如：user_id
         */
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