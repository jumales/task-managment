package com.demo.audit.controller;

import com.demo.common.config.KafkaTopics;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes a lightweight endpoint to inspect dead-letter topic message counts.
 * A non-zero count on any DLT indicates messages that failed all retries and require
 * manual investigation or replay.
 */
@Tag(name = "DLQ", description = "Dead-letter queue monitoring")
@RestController
@RequestMapping("/api/v1/dlq")
public class DlqController {

    private final KafkaAdmin kafkaAdmin;

    public DlqController(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    /**
     * Returns the total message count (end offset sum across all partitions) for each DLT topic.
     * A non-zero value means messages have been dead-lettered and require attention.
     * Returns -1 for a topic that does not yet exist (no failures have occurred on it).
     */
    @Operation(
        summary = "Get DLT message counts",
        description = "Returns the end offset per DLT topic. Non-zero values indicate "
                    + "failed messages that exhausted all retries and need investigation or replay."
    )
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getDltStatus() {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            Map<String, Long> counts = Map.of(
                KafkaTopics.TASK_CHANGED_DLT, endOffset(admin, KafkaTopics.TASK_CHANGED_DLT),
                KafkaTopics.TASK_EVENTS_DLT,  endOffset(admin, KafkaTopics.TASK_EVENTS_DLT),
                KafkaTopics.USER_EVENTS_DLT,  endOffset(admin, KafkaTopics.USER_EVENTS_DLT)
            );
            return Map.of(
                "dltMessageCounts", counts,
                "note", "End offsets per DLT topic. Each unit is one dead-lettered message. -1 means the topic does not exist yet."
            );
        }
    }

    /** Sums end offsets across all partitions of a topic. Returns -1 if the topic does not exist. */
    private long endOffset(AdminClient admin, String topic) {
        try {
            var descriptions = admin.describeTopics(List.of(topic)).allTopicNames().get();
            if (!descriptions.containsKey(topic)) return -1L;

            Map<TopicPartition, OffsetSpec> query = new HashMap<>();
            descriptions.get(topic).partitions()
                        .forEach(p -> query.put(new TopicPartition(topic, p.partition()), OffsetSpec.latest()));

            ListOffsetsResult result = admin.listOffsets(query);
            return result.all().get().values().stream()
                         .mapToLong(ListOffsetsResult.ListOffsetsResultInfo::offset)
                         .sum();
        } catch (Exception e) {
            return -1L;
        }
    }
}
