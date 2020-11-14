package io.github.pursuewind.sample.mybatisplus.plugin;

import io.github.pursuewind.mybatisplus.plugin.interceptor.TableShardingInterceptor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }

    @Bean
    public TableShardingInterceptor mybatisPlusShardingPlugin() {
        return new TableShardingInterceptor();
    }
}
