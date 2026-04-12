# Finding #4 — Replace per-instance Caffeine caches with Redis

## Status
UNRESOLVED

## Severity
HIGH — stale reads across replicas under multi-instance deployment

## Context
`user-service` and `task-service` both use Caffeine (JVM-local, per-instance cache). When a user is
updated on instance A, `@CacheEvict` fires on A only. Instance B continues serving stale user data
for up to 10 minutes. Redis is already deployed in the stack (used by api-gateway for rate limiting).

## Root Cause
- `user-service/src/main/java/com/demo/user/config/CacheConfig.java` — `CaffeineCacheManager` with caches `USERS`, `USERS_BY_USERNAME`, maxSize 500, TTL 10 min
- `task-service/src/main/java/com/demo/task/config/CacheConfig.java` — `CaffeineCacheManager` with caches `USER_NAMES`, `USER_DTOS`, maxSize 1000, TTL 10 min
- Cache eviction on writes in `user-service/src/main/java/com/demo/user/keycloak/KeycloakUserClient.java` (all write methods: create, update, disable, updateAttribute, setUserRoles)
- Cache reads in `task-service/src/main/java/com/demo/task/client/UserClientHelper.java` (resolveUserName, fetchUser)

## Files to Modify

### 1. `user-service/pom.xml`
Add Redis dependency, remove Caffeine:
```xml
<!-- Add -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- Remove -->
<!-- <dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency> -->
```

### 2. `task-service/pom.xml`
Same: add `spring-boot-starter-data-redis`, remove `caffeine`.

### 3. `user-service/src/main/java/com/demo/user/config/CacheConfig.java`
Replace `CaffeineCacheManager` with `RedisCacheManager`:
```java
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String USERS = "users";
    public static final String USERS_BY_USERNAME = "usersByUsername";

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
```

### 4. `task-service/src/main/java/com/demo/task/config/CacheConfig.java`
Same pattern, cache names `USER_NAMES` and `USER_DTOS`.

### 5. `user-service/src/main/resources/application.yml`
Add Redis connection config:
```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
```

### 6. `task-service/src/main/resources/application.yml`
Same Redis config block.

### No Changes Needed
- `KeycloakUserClient.java` — `@Cacheable`/`@CacheEvict` work identically with `RedisCacheManager`
- `UserClientHelper.java` — same

## Integration Test Changes
Both service IT classes need a Redis Testcontainer:
```java
@Container
static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

@DynamicPropertySource
static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
}
```

## Verification
1. Start two user-service instances
2. Update a user via instance-1 (`PUT /api/v1/users/{id}`)
3. Immediately fetch the same user via instance-2 — must return updated data (not stale)
4. Check Redis for cache keys: `redis-cli KEYS "users*"`

## Notes
- Cache keys will now be namespaced as `cacheName::key` by Spring's `RedisCacheManager`
- `GenericJackson2JsonRedisSerializer` requires Jackson's `@JsonTypeInfo` for polymorphic types — verify `UserDto` serializes/deserializes correctly
- The `UserDto` class lives in `common` — ensure it has a no-arg constructor (Lombok `@NoArgsConstructor` or `@Data`)
