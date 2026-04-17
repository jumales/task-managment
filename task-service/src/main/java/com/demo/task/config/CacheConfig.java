package com.demo.task.config;

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
 * Redis cache configuration for task-service.
 *
 * <p>User data fetched from user-service via Feign is cached here to avoid repeated remote
 * calls on every task write or timeline read. Using Redis instead of a per-instance JVM
 * cache ensures that evictions triggered by user updates are visible to all replicas.
 *
 * <ul>
 *   <li>{@link #USER_NAMES} — display name strings, keyed by user UUID</li>
 *   <li>{@link #USER_DTOS}  — full {@code UserDto} objects, keyed by user UUID</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Cache name for resolved user display names, keyed by user UUID. */
    public static final String USER_NAMES = "userNames";

    /** Cache name for full UserDto objects fetched from user-service, keyed by user UUID. */
    public static final String USER_DTOS  = "userDtos";

    /** Cache name for assembled TaskResponse objects, keyed by task UUID. Evicted on any task mutation. */
    public static final String TASKS = "tasks";

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
                .withCacheConfiguration(USER_NAMES, config)
                .withCacheConfiguration(USER_DTOS, config)
                .withCacheConfiguration(TASKS, config)
                .build();
    }
}
