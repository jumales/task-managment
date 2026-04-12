package com.demo.task.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Pre-warms the user Caffeine caches (USER_DTOS and USER_NAMES) at startup by fetching
 * all users from user-service once. This prevents the cache-stampede problem that occurs
 * when 100+ concurrent task creations all miss the cache simultaneously and hammer the
 * user-service (and its downstream Keycloak admin API) on the very first wave of requests.
 *
 * <p>After warmup, all subsequent {@code fetchUser()} calls are served from the in-memory
 * cache and user-service receives near-zero additional Feign traffic for the TTL window.
 */
@Component
public class UserCacheWarmup {

    private static final Logger log = LoggerFactory.getLogger(UserCacheWarmup.class);

    private static final int WARMUP_PAGE_SIZE = 500;

    private final UserClient      userClient;
    private final UserClientHelper userClientHelper;

    public UserCacheWarmup(UserClient userClient, UserClientHelper userClientHelper) {
        this.userClient       = userClient;
        this.userClientHelper = userClientHelper;
    }

    /**
     * Fetches up to {@value #WARMUP_PAGE_SIZE} users from user-service and calls
     * {@link UserClientHelper#fetchUser} and {@link UserClientHelper#resolveUserName}
     * for each, so both USER_DTOS and USER_NAMES caches are populated before
     * the service starts receiving traffic.
     * Failures are logged as warnings and do not block startup — the caches will
     * warm lazily under live load in that case.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        try {
            UserPageResponse page = userClient.getUsers(0, WARMUP_PAGE_SIZE);
            int count = 0;
            for (var user : page.content()) {
                // Calling through userClientHelper (not userClient directly) ensures the
                // @Cacheable proxy intercepts the call and stores the result in Caffeine.
                userClientHelper.fetchUser(user.getId());        // → USER_DTOS cache
                userClientHelper.resolveUserName(user.getId()); // → USER_NAMES cache
                count++;
            }
            log.info("User cache warmed: {} entries cached (page 1 of {}, {} total users)",
                    count, page.totalPages(), page.totalElements());
        } catch (Exception e) {
            log.warn("User cache warmup failed — caches will warm lazily: {}", e.getMessage());
        }
    }
}
