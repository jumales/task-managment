package com.demo.task.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine cache configuration for task-service.
 *
 * <p>User names are resolved from user-service via Feign and cached here to avoid
 * repeated remote calls on every timeline read. Names change rarely, so a 10-minute
 * TTL provides a good balance between freshness and performance.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Cache name for resolved user display names, keyed by user UUID. */
    public static final String USER_NAMES = "userNames";

    /** Creates a Caffeine-backed cache manager configured with the userNames cache. */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(USER_NAMES);
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .maximumSize(1000)
                        .expireAfterWrite(10, TimeUnit.MINUTES)
        );
        return manager;
    }
}
