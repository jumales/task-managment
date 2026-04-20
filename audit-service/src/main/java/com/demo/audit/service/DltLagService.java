package com.demo.audit.service;

import com.demo.common.config.KafkaTopics;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;

/**
 * Computes unconsumed-message lag per dead-letter topic.
 *
 * <p>Lag = {@code end_offset − committed_offset_for_group}. This is the correct "pending work"
 * signal — unlike raw end offset, it drops back to zero after the DLT is drained.
 *
 * <p>If no consumer group has committed to the DLT yet, {@code committed_offset = 0} and
 * lag equals the end offset (every dead-lettered message is unprocessed).
 *
 * <p>Shared by {@code DlqController}, {@code DltHealthIndicator}, and {@code DltMetricsPublisher}
 * so lag is computed in one place.
 */
@Service
public class DltLagService {

    /** Returned when an {@link AdminClient} call fails — distinguishable from a genuine 0 lag. */
    public static final long UNKNOWN_LAG = -1L;

    /** DLT topic → consumer group used to track its lag. */
    private static final Map<String, String> DLT_GROUPS = Map.of(
        KafkaTopics.TASK_CHANGED_DLT, KafkaTopics.TASK_CHANGED_DLT_GROUP,
        KafkaTopics.TASK_EVENTS_DLT,  KafkaTopics.TASK_EVENTS_DLT_GROUP,
        KafkaTopics.USER_EVENTS_DLT,  KafkaTopics.USER_EVENTS_DLT_GROUP
    );

    private final KafkaAdmin kafkaAdmin;

    public DltLagService(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    /** Returns lag per DLT topic. Preserves insertion order so output is stable. */
    public Map<String, Long> getAllLags() {
        Map<String, Long> lags = new LinkedHashMap<>();
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            DLT_GROUPS.forEach((topic, groupId) -> lags.put(topic, consumerLag(admin, topic, groupId)));
        }
        return lags;
    }

    /**
     * Computes consumer lag for one (topic, group) pair. Returns 0 if the topic does not yet
     * exist (no failures have occurred) or if the DLT consumer group has never committed
     * (treated as 0 committed offset). Returns {@link #UNKNOWN_LAG} on any other failure.
     */
    long consumerLag(AdminClient admin, String topic, String groupId) {
        try {
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets = endOffsets(admin, topic);
            if (endOffsets.isEmpty()) return 0L;

            Map<TopicPartition, OffsetAndMetadata> committed = committedOffsets(admin, groupId);

            return endOffsets.entrySet().stream().mapToLong(entry -> {
                long end = entry.getValue().offset();
                long committedOffset = Optional.ofNullable(committed.get(entry.getKey()))
                        .map(OffsetAndMetadata::offset)
                        .orElse(0L);
                return Math.max(0L, end - committedOffset);
            }).sum();
        } catch (Exception e) {
            return UNKNOWN_LAG;
        }
    }

    /** Returns end offsets for the topic; empty map if the topic does not exist. */
    private Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets(AdminClient admin, String topic)
            throws InterruptedException, ExecutionException {
        try {
            var descriptions = admin.describeTopics(List.of(topic)).allTopicNames().get();
            if (!descriptions.containsKey(topic)) return Collections.emptyMap();
            Map<TopicPartition, OffsetSpec> query = new HashMap<>();
            descriptions.get(topic).partitions()
                    .forEach(p -> query.put(new TopicPartition(topic, p.partition()), OffsetSpec.latest()));
            return admin.listOffsets(query).all().get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof UnknownTopicOrPartitionException) return Collections.emptyMap();
            throw e;
        }
    }

    /**
     * Returns committed offsets for the DLT consumer group; empty map if the group does not
     * exist yet (common case — no DLT consumer has run, so every dead-lettered message counts
     * as unprocessed).
     */
    private Map<TopicPartition, OffsetAndMetadata> committedOffsets(AdminClient admin, String groupId) {
        try {
            return admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata().get();
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
