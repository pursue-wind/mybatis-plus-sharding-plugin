package io.github.pursuewind.sample.mybatisplus.plugin.cache;

import com.google.common.cache.*;

import java.util.concurrent.TimeUnit;

public class GuavaCacheDemo {
//    private Integer cacheSize;
//    private Integer expireTime;


    public static void main(String[] args) {

        LoadingCache<String, String> testCache = CacheBuilder.newBuilder()
                .maximumSize(7)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .removalListener(removal -> System.out.println("[" + removal.getKey() + ":" + removal.getValue() + "] is evicted!"))
                .build(new CacheLoader<String, String>() {
                    @Override
                    public String load(String key) {
                        // 若缓存中无需要的数据,会执行以下方法
                        return null;
                    }
                });

        for (int i = 0; i < 10; i++) {
            String key = "key" + i;
            String value = "value" + i;
            testCache.put(key, value);
        }
        for (int i = 0; i < 10; i++) {
            String key = "key" + i;
            String value = "value" + i;
            testCache.put(key, value);
        }

        System.out.println(testCache.getIfPresent("key6"));
        System.out.println(testCache.getIfPresent("key0"));
        System.out.println(testCache.getIfPresent("key0"));

        try {
            System.out.println(testCache.get("key0"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}