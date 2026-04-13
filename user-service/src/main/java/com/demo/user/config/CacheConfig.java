package com.demo.user.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis cache configuration for the user service.
 *
 * <p>Replaces the former per-instance Caffeine cache with a shared Redis cache so that
 * cache eviction on any instance is immediately visible to all other replicas.
 *
 * <p>TTL: 10 minutes — long enough to absorb read spikes, short enough to self-heal
 * if an eviction is somehow missed.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Cache name for single-user lookups keyed by UUID. */
    public static final String USERS = "users";

    /** Cache name for username-based lookups. */
    public static final String USERS_BY_USERNAME = "usersByUsername";

    /** Creates a Redis-backed cache manager with JSON serialization and a 10-minute TTL. */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .withCacheConfiguration(USERS, config)
                .withCacheConfiguration(USERS_BY_USERNAME, config)
                .build();
    }
}
