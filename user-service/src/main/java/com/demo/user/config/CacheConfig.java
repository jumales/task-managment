package com.demo.user.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine cache configuration for roles and rights.
 *
 * <p>Roles and rights are read frequently but change rarely, making them ideal candidates
 * for in-process caching. Caffeine is used as the provider — it requires no external
 * infrastructure and offers high-performance near-optimal eviction.
 *
 * <p>Cache names are declared as {@code public static final String} constants so they can
 * be referenced in {@code @Cacheable} / {@code @CacheEvict} annotations as compile-time
 * constants (annotation attributes require constant expressions).
 *
 * <p>TTL: 10 minutes — long enough to absorb read spikes, short enough to self-heal
 * if a cache eviction is somehow missed.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Cache name for role entries (list and individual lookups). */
    public static final String ROLES = "roles";

    /** Cache name for right entries (list and individual lookups). */
    public static final String RIGHTS = "rights";

    /** Creates a Caffeine-backed cache manager pre-configured with the roles and rights caches. */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(ROLES, RIGHTS);
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .recordStats()
        );
        return manager;
    }
}
