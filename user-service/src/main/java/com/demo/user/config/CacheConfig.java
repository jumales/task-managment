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

    /** Creates a Caffeine-backed cache manager. */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .recordStats()
        );
        return manager;
    }
}
