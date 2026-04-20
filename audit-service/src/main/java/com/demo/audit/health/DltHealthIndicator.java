package com.demo.audit.health;

import com.demo.audit.service.DltLagService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Reports {@code DOWN} when any DLT topic has unconsumed messages (consumer lag &gt; 0).
 *
 * <p>Exposed via {@code /actuator/health/dlt}. Can gate a Kubernetes readiness probe or feed
 * an alerting system — any pending DLT messages are a real production incident because they
 * represent events the normal consumer failed to process after the bounded retry budget.
 *
 * <p>Uses {@link DltLagService} so lag computation stays consistent with the DLQ controller
 * and Micrometer metrics.
 */
@Component("dlt")
public class DltHealthIndicator implements HealthIndicator {

    private final DltLagService dltLagService;

    public DltHealthIndicator(DltLagService dltLagService) {
        this.dltLagService = dltLagService;
    }

    @Override
    public Health health() {
        Map<String, Long> lags = dltLagService.getAllLags();
        boolean anyPending = lags.values().stream().anyMatch(lag -> lag > 0);
        if (anyPending) {
            return Health.down()
                    .withDetails(lags)
                    .withDetail("message", "Dead-letter topics have unconsumed messages")
                    .build();
        }
        return Health.up().withDetails(lags).build();
    }
}
