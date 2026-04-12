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
 * <p>User data is resolved from user-service via Feign and cached here to avoid
 * repeated remote calls on every task write or timeline read. User records change
 * rarely, so a 10-minute TTL provides a good balance between freshness and performance.
 *
 * <ul>
 *   <li>{@link #USER_NAMES} — display name strings, keyed by user UUID</li>
 *   <li>{@link #USER_DTOS}  — full {@code UserDto} objects, keyed by user UUID;
 *       used during task creation to avoid hammering user-service under load</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Cache name for resolved user display names, keyed by user UUID. */
    public static final String USER_NAMES = "userNames";

    /** Cache name for full UserDto objects fetched from user-service, keyed by user UUID. */
    public static final String USER_DTOS  = "userDtos";

    /** Creates a Caffeine-backed cache manager configured with all user-related caches. */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(USER_NAMES, USER_DTOS);
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .maximumSize(1000)
                        .expireAfterWrite(10, TimeUnit.MINUTES)
        );
        return manager;
    }
}
