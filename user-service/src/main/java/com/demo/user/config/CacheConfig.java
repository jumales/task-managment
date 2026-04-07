package com.demo.user.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine cache configuration for the user service.
 *
 * <p>Caffeine is used as the in-process cache provider — it requires no external
 * infrastructure and offers high-performance near-optimal eviction.
 *
 * <p>TTL: 10 minutes — long enough to absorb read spikes, short enough to self-heal
 * if a cache eviction is somehow missed.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Cache name for single-user lookups keyed by UUID. */
    public static final String USERS = "users";

    /** Cache name for username-based lookups. */
    public static final String USERS_BY_USERNAME = "usersByUsername";

    /** Creates a Caffeine-backed cache manager with explicit cache names. */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheNames(java.util.List.of(USERS, USERS_BY_USERNAME));
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .recordStats()
        );
        return manager;
    }
}
