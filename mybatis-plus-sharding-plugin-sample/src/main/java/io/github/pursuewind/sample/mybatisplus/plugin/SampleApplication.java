package io.github.pursuewind.sample.mybatisplus.plugin;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import io.github.pursuewind.mybatisplus.plugin.interceptor.TableShardingInnerInterceptor2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;

@SpringBootApplication
public class SampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }

    @Bean
    protected MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        TableShardingInnerInterceptor2.Sharding sharding = TableShardingInnerInterceptor2.Sharding.builder()
                .tableName("user")
                .shardingParam("id")
                .tableNameProcessor((tableName, paramVal) -> tableName + "_" + (int) paramVal % 10)
                .build();
        TableShardingInnerInterceptor2.Sharding sharding2 = TableShardingInnerInterceptor2.Sharding.builder()
                .tableName("date_demo")
                .shardingParam("create_time")
                .tableNameProcessor((tableName, paramVal) -> tableName + "_" + ((LocalDateTime) paramVal).getYear() + "_" + ((LocalDateTime) paramVal).getMonthValue())
                .build();
        interceptor.addInnerInterceptor(new TableShardingInnerInterceptor2()
                .with(sharding)
                .with(sharding2));

        return interceptor;
    }
}
