package com.demo.audit.metrics;

import com.demo.audit.service.DltLagService;
import com.demo.common.config.KafkaTopics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Publishes DLT consumer lag as Micrometer gauges on a fixed schedule.
 *
 * <p>Prometheus scrapes {@code /actuator/prometheus} and emits {@code kafka_dlt_consumer_lag}
 * tagged by topic. Recommended alert rule:
 * <pre>
 * kafka_dlt_consumer_lag &gt; 0 for 5m → PagerDuty/Slack
 * </pre>
 *
 * <p>AdminClient calls are synchronous and relatively expensive, so poll at 30 s —
 * never sub-5 s.
 */
@Component
public class DltMetricsPublisher {

    private static final String GAUGE_NAME = "kafka.dlt.consumer.lag";
    private static final long POLL_INTERVAL_MS = 30_000L;

    /** DLTs that get a gauge registered at startup. */
    private static final List<String> DLT_TOPICS = List.of(
        KafkaTopics.TASK_CHANGED_DLT,
        KafkaTopics.TASK_EVENTS_DLT,
        KafkaTopics.USER_EVENTS_DLT
    );

    private final MeterRegistry meterRegistry;
    private final DltLagService dltLagService;
    private final Map<String, AtomicLong> gaugeValues = new ConcurrentHashMap<>();

    public DltMetricsPublisher(MeterRegistry meterRegistry, DltLagService dltLagService) {
        this.meterRegistry = meterRegistry;
        this.dltLagService = dltLagService;
    }

    /**
     * Registers one gauge per DLT topic. The {@link AtomicLong} backs the gauge so subsequent
     * {@link #refresh()} calls only update the value — no duplicate registrations.
     */
    @PostConstruct
    void registerGauges() {
        for (String topic : DLT_TOPICS) {
            AtomicLong holder = new AtomicLong(0L);
            gaugeValues.put(topic, holder);
            Gauge.builder(GAUGE_NAME, holder, AtomicLong::get)
                    .tag("topic", topic)
                    .description("Number of unprocessed messages in DLT topic (end offset − committed offset)")
                    .register(meterRegistry);
        }
    }

    /** Refreshes gauge values from {@link DltLagService}. */
    @Scheduled(fixedDelay = POLL_INTERVAL_MS)
    public void refresh() {
        dltLagService.getAllLags().forEach((topic, lag) -> {
            AtomicLong holder = gaugeValues.get(topic);
            if (holder != null) holder.set(lag);
        });
    }
}
