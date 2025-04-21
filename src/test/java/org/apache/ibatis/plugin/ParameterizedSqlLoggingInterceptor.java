package org.apache.ibatis.plugin;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.lang.reflect.Proxy;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author wuxin
 * @date 2025/04/19 12:09:54
 * 对于 mybatis plus 3.1.2 的分页插件 统计数量的sql 无法打印解析后的 sql 是正常的
 * 因为在 3.1.2 版本是直接使用PreparedStatement进行调用，没有经过 statementHandler 无法经过代理
 *
 */
@Intercepts(value = {
        @Signature(type = StatementHandler.class, method = "query", args = { Statement.class, ResultHandler.class }),
        @Signature(type = StatementHandler.class, method = "update", args = { Statement.class}),
        @Signature(type = StatementHandler.class, method = "batch", args = { Statement.class}),
        @Signature(type = StatementHandler.class, method = "queryCursor", args = { Statement.class}),
})
public class ParameterizedSqlLoggingInterceptor implements Interceptor {

    private Configuration configuration;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public ParameterizedSqlLoggingInterceptor(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object proceed = invocation.proceed();
      StatementHandler target = this.<StatementHandler>realTarget(invocation.getTarget());
        try {
          String pretty = pretty(parseSql(target));
          System.out.println("解析后的SQL" + pretty);
        }catch (Exception e){
          System.out.println("解析SQL 失败:{}" + e);
        }
        return proceed;
    }


    public <T> T realTarget(Object target) {
      if (Proxy.isProxyClass(target.getClass())) {
        MetaObject metaObject = SystemMetaObject.forObject(target);
        return realTarget(metaObject.getValue("h.target"));
      } else {
        return (T)target;
      }
    }


    private String parseSql(StatementHandler target){
        BoundSql boundSql = target.getBoundSql();
        String sql = boundSql.getSql();
//        log.info("原始sql ::{}", sql);
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        if (parameterMappings == null || parameterMappings.isEmpty()) {
            return sql;
        }
        Object parameterObject = boundSql.getParameterObject();

        TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
        MetaObject metaObject = parameterObject == null ? null : configuration.newMetaObject(parameterObject);

        StringBuffer result = new StringBuffer();
        String regex = "\\?";
        Pattern compile = Pattern.compile(regex);
        Matcher matcher = compile.matcher(sql);
        int index = 0;
        while (matcher.find()){
            String valueAsString = "";
            if(index < parameterMappings.size()){
                Object value;
                ParameterMapping parameterMapping = parameterMappings.get(index);
                if (parameterMapping.getMode() != ParameterMode.OUT) {
                    String propertyName = parameterMapping.getProperty();
                    if (boundSql.hasAdditionalParameter(propertyName)) {
                        value = boundSql.getAdditionalParameter(propertyName);
                    } else if (parameterObject == null) {
                        value = null;
                    } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                        value = parameterObject;
                    } else if (metaObject != null) {
                        value = metaObject.getValue(propertyName);
                    } else {
                        value = null;
                    }
                    valueAsString = format2String(value);
                }
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(valueAsString));
            index++;
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String format2String(Object e){
        if (Objects.isNull(e)) {
            return "";
        }
        if (e instanceof String) {
            String str = (String) e;
            str = str.replace("'", "''");
            return "'" + str + "'";
        }
        if (e instanceof LocalDateTime) {
            return "'" + ((LocalDateTime) e).format(DATE_TIME_FORMATTER) + "'";
        }
        if (e instanceof LocalDate) {
            return "'" + ((LocalDate) e).format(DATE_FORMATTER) + "'";
        }
        if (e instanceof java.util.Date) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return "'" + sdf.format((Date) e) + "'";
        }
        if(e instanceof Enum){
            return "'" + ((Enum<?>) e).name() + "'";
        }
        return e.toString();
    }

    private String pretty(String sql){
        if(Objects.isNull(sql)){
            return null;
        }
        return sql.replaceAll("\n"," ").replaceAll("\\s+", " ");
    }

    @Override
    public Object plugin(Object target) {
        return target instanceof StatementHandler ? Plugin.wrap(target, this) : target;
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
