package com.demo.audit;

import com.demo.audit.dedup.ProcessedEventRepository;
import com.demo.audit.model.StatusAuditRecord;
import com.demo.audit.model.BookedWorkAuditRecord;
import com.demo.audit.model.CommentAuditRecord;
import com.demo.audit.model.PhaseAuditRecord;
import com.demo.audit.model.PlannedWorkAuditRecord;
import com.demo.audit.repository.AuditRepository;
import com.demo.audit.repository.BookedWorkAuditRepository;
import com.demo.audit.repository.CommentAuditRepository;
import com.demo.audit.repository.PhaseAuditRepository;
import com.demo.audit.repository.PlannedWorkAuditRepository;
import com.demo.common.dto.TaskStatus;
import com.demo.common.dto.WorkType;
import com.demo.common.event.TaskChangeType;
import com.demo.common.event.TaskChangedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.RecordInterceptor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
class AuditConsumerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Autowired
    KafkaTemplate<String, TaskChangedEvent> kafkaTemplate;

    @Autowired
    AuditRepository auditRepository;

    @Autowired
    PlannedWorkAuditRepository plannedWorkAuditRepository;

    @Autowired
    BookedWorkAuditRepository bookedWorkAuditRepository;

    @Autowired
    CommentAuditRepository commentAuditRepository;

    @Autowired
    PhaseAuditRepository phaseAuditRepository;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @Autowired
    RecordInterceptor<Object, Object> mdcRecordInterceptor;

    @BeforeEach
    void setUp() {
        auditRepository.deleteAll();
        commentAuditRepository.deleteAll();
        phaseAuditRepository.deleteAll();
        plannedWorkAuditRepository.deleteAll();
        bookedWorkAuditRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

    @AfterEach
    void tearDownMdc() {
        MDC.clear();
    }

    // ── Status events ─────────────────────────────────────────────

    @Test
    void consumeStatusEvent_persistsAuditRecord() {
        UUID taskId = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.statusChanged(taskId, UUID.randomUUID(), null, null,
                        TaskStatus.TODO, TaskStatus.IN_PROGRESS));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            List<StatusAuditRecord> records = auditRepository.findByTaskIdOrderByChangedAtAsc(taskId, Pageable.unpaged()).getContent();
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getFromStatus()).isEqualTo(TaskStatus.TODO);
            assertThat(records.get(0).getToStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
            assertThat(records.get(0).getRecordedAt()).isNotNull();
        });
    }

    @Test
    void consumeMultipleStatusEvents_persistsAllInChronologicalOrder() {
        UUID taskId = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.statusChanged(taskId, UUID.randomUUID(), null, null,
                        TaskStatus.TODO, TaskStatus.IN_PROGRESS));
        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.statusChanged(taskId, UUID.randomUUID(), null, null,
                        TaskStatus.IN_PROGRESS, TaskStatus.DONE));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            List<StatusAuditRecord> records = auditRepository.findByTaskIdOrderByChangedAtAsc(taskId, Pageable.unpaged()).getContent();
            assertThat(records).hasSize(2);
            assertThat(records.get(0).getToStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
            assertThat(records.get(1).getToStatus()).isEqualTo(TaskStatus.DONE);
        });
    }

    // ── Comment events ────────────────────────────────────────────

    @Test
    void consumeCommentEvent_persistsCommentAuditRecord() {
        UUID taskId    = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.commentAdded(taskId, UUID.randomUUID(), null, null,
                        commentId, "First comment"));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            List<CommentAuditRecord> records = commentAuditRepository.findByTaskIdOrderByAddedAtAsc(taskId, Pageable.unpaged()).getContent();
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getCommentId()).isEqualTo(commentId);
            assertThat(records.get(0).getContent()).isEqualTo("First comment");
            assertThat(records.get(0).getAddedAt()).isNotNull();
            assertThat(records.get(0).getRecordedAt()).isNotNull();
        });
    }

    @Test
    void consumeMultipleCommentEvents_persistsAll() {
        UUID taskId = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.commentAdded(taskId, UUID.randomUUID(), null, null,
                        UUID.randomUUID(), "First comment"));
        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.commentAdded(taskId, UUID.randomUUID(), null, null,
                        UUID.randomUUID(), "Second comment"));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            List<CommentAuditRecord> records = commentAuditRepository.findByTaskIdOrderByAddedAtAsc(taskId, Pageable.unpaged()).getContent();
            assertThat(records).hasSize(2);
            assertThat(records).extracting("content")
                    .containsExactlyInAnyOrder("First comment", "Second comment");
        });
    }

    // ── Isolation ─────────────────────────────────────────────────

    @Test
    void statusAndCommentEvents_routedToCorrectAuditStore() {
        UUID taskId = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.statusChanged(taskId, UUID.randomUUID(), null, null,
                        TaskStatus.TODO, TaskStatus.DONE));
        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.commentAdded(taskId, UUID.randomUUID(), null, null,
                        UUID.randomUUID(), "A comment"));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            assertThat(auditRepository.findByTaskIdOrderByChangedAtAsc(taskId, Pageable.unpaged()).getContent()).hasSize(1);
            assertThat(commentAuditRepository.findByTaskIdOrderByAddedAtAsc(taskId, Pageable.unpaged()).getContent()).hasSize(1);
        });
    }

    // ── Phase events ──────────────────────────────────────────────

    @Test
    void consumePhaseEvent_persistsPhaseAuditRecord() {
        UUID taskId    = UUID.randomUUID();
        UUID fromPhase = UUID.randomUUID();
        UUID toPhase   = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.phaseChanged(taskId, UUID.randomUUID(), null, null,
                        fromPhase, "Backlog", toPhase, "In Review"));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            List<PhaseAuditRecord> records = phaseAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId, Pageable.unpaged()).getContent();
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getFromPhaseId()).isEqualTo(fromPhase);
            assertThat(records.get(0).getFromPhaseName()).isEqualTo("Backlog");
            assertThat(records.get(0).getToPhaseId()).isEqualTo(toPhase);
            assertThat(records.get(0).getToPhaseName()).isEqualTo("In Review");
            assertThat(records.get(0).getChangedAt()).isNotNull();
            assertThat(records.get(0).getRecordedAt()).isNotNull();
        });
    }

    @Test
    void consumeMultiplePhaseEvents_persistsAllInChronologicalOrder() {
        UUID taskId = UUID.randomUUID();
        UUID phaseA = UUID.randomUUID();
        UUID phaseB = UUID.randomUUID();
        UUID phaseC = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.phaseChanged(taskId, UUID.randomUUID(), null, null,
                        phaseA, "Backlog", phaseB, "In Review"));
        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.phaseChanged(taskId, UUID.randomUUID(), null, null,
                        phaseB, "In Review", phaseC, "Released"));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            List<PhaseAuditRecord> records = phaseAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId, Pageable.unpaged()).getContent();
            assertThat(records).hasSize(2);
            assertThat(records.get(0).getToPhaseName()).isEqualTo("In Review");
            assertThat(records.get(1).getToPhaseName()).isEqualTo("Released");
        });
    }

    @Test
    void allEventTypes_routedToCorrectAuditStore() {
        UUID taskId = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.statusChanged(taskId, UUID.randomUUID(), null, null,
                        TaskStatus.TODO, TaskStatus.DONE));
        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.commentAdded(taskId, UUID.randomUUID(), null, null,
                        UUID.randomUUID(), "A comment"));
        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.phaseChanged(taskId, UUID.randomUUID(), null, null,
                        UUID.randomUUID(), "Backlog", UUID.randomUUID(), "Done"));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            assertThat(auditRepository.findByTaskIdOrderByChangedAtAsc(taskId, Pageable.unpaged()).getContent()).hasSize(1);
            assertThat(commentAuditRepository.findByTaskIdOrderByAddedAtAsc(taskId, Pageable.unpaged()).getContent()).hasSize(1);
            assertThat(phaseAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId, Pageable.unpaged())).hasSize(1);
        });
    }

    // ── Planned work events ───────────────────────────────────────

    @Test
    void consumePlannedWorkCreatedEvent_persistsAuditRecord() {
        UUID taskId       = UUID.randomUUID();
        UUID plannedWorkId = UUID.randomUUID();
        UUID userId       = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.plannedWorkCreated(taskId, null, null, plannedWorkId, userId,
                        WorkType.DEVELOPMENT, BigInteger.valueOf(8)));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            List<PlannedWorkAuditRecord> records = plannedWorkAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId, Pageable.unpaged()).getContent();
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getPlannedWorkId()).isEqualTo(plannedWorkId);
            assertThat(records.get(0).getChangeType()).isEqualTo(TaskChangeType.PLANNED_WORK_CREATED);
            assertThat(records.get(0).getPlannedWorkUserId()).isEqualTo(userId);
            assertThat(records.get(0).getWorkType()).isEqualTo(WorkType.DEVELOPMENT);
            assertThat(records.get(0).getPlannedHours()).isEqualTo(8);
            assertThat(records.get(0).getRecordedAt()).isNotNull();
        });
    }

    // ── Booked work events ────────────────────────────────────────

    @Test
    void consumeBookedWorkCreatedEvent_persistsAuditRecord() {
        UUID taskId      = UUID.randomUUID();
        UUID bookedWorkId = UUID.randomUUID();
        UUID userId      = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.bookedWorkCreated(taskId, null, null, bookedWorkId, userId,
                        WorkType.DEVELOPMENT, BigInteger.valueOf(3)));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            List<BookedWorkAuditRecord> records = bookedWorkAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId, Pageable.unpaged()).getContent();
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getBookedWorkId()).isEqualTo(bookedWorkId);
            assertThat(records.get(0).getChangeType()).isEqualTo(TaskChangeType.BOOKED_WORK_CREATED);
            assertThat(records.get(0).getBookedWorkUserId()).isEqualTo(userId);
            assertThat(records.get(0).getWorkType()).isEqualTo(WorkType.DEVELOPMENT);
            assertThat(records.get(0).getBookedHours()).isEqualTo(3);
            assertThat(records.get(0).getRecordedAt()).isNotNull();
        });
    }

    @Test
    void consumeBookedWorkUpdatedEvent_persistsAuditRecord() {
        UUID taskId      = UUID.randomUUID();
        UUID bookedWorkId = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.bookedWorkUpdated(taskId, null, null, bookedWorkId, UUID.randomUUID(),
                        WorkType.TESTING, BigInteger.valueOf(4)));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            List<BookedWorkAuditRecord> records = bookedWorkAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId, Pageable.unpaged()).getContent();
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getChangeType()).isEqualTo(TaskChangeType.BOOKED_WORK_UPDATED);
            assertThat(records.get(0).getWorkType()).isEqualTo(WorkType.TESTING);
        });
    }

    @Test
    void consumeBookedWorkDeletedEvent_persistsAuditRecord() {
        UUID taskId      = UUID.randomUUID();
        UUID bookedWorkId = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.bookedWorkDeleted(taskId, null, null, bookedWorkId));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            List<BookedWorkAuditRecord> records = bookedWorkAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId, Pageable.unpaged()).getContent();
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getChangeType()).isEqualTo(TaskChangeType.BOOKED_WORK_DELETED);
            assertThat(records.get(0).getBookedWorkId()).isEqualTo(bookedWorkId);
            assertThat(records.get(0).getBookedHours()).isNull();
        });
    }

    @Test
    void multipleBookedWorkEvents_allPersistedInOrder() {
        UUID taskId      = UUID.randomUUID();
        UUID bookedWorkId = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.bookedWorkCreated(taskId, null, null, bookedWorkId, UUID.randomUUID(),
                        WorkType.DEVELOPMENT, BigInteger.valueOf(3)));
        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.bookedWorkUpdated(taskId, null, null, bookedWorkId, UUID.randomUUID(),
                        WorkType.DEVELOPMENT, BigInteger.valueOf(5)));
        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.bookedWorkDeleted(taskId, null, null, bookedWorkId));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            List<BookedWorkAuditRecord> records = bookedWorkAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId, Pageable.unpaged()).getContent();
            assertThat(records).hasSize(3);
            assertThat(records.get(0).getChangeType()).isEqualTo(TaskChangeType.BOOKED_WORK_CREATED);
            assertThat(records.get(1).getChangeType()).isEqualTo(TaskChangeType.BOOKED_WORK_UPDATED);
            assertThat(records.get(2).getChangeType()).isEqualTo(TaskChangeType.BOOKED_WORK_DELETED);
        });
    }

    // ── Idempotency ───────────────────────────────────────────────

    @Test
    void duplicateEvent_persistsOnlyOneAuditRecord() {
        UUID taskId = UUID.randomUUID();
        // Same object = same eventId → second delivery must be discarded
        TaskChangedEvent event = TaskChangedEvent.statusChanged(taskId, UUID.randomUUID(), null, null,
                TaskStatus.TODO, TaskStatus.IN_PROGRESS);

        kafkaTemplate.send("task-changed", taskId.toString(), event);
        kafkaTemplate.send("task-changed", taskId.toString(), event);

        await().atMost(15, SECONDS).untilAsserted(() -> {
            assertThat(auditRepository.findByTaskIdOrderByChangedAtAsc(taskId, Pageable.unpaged()).getContent()).hasSize(1);
            assertThat(processedEventRepository.count()).isEqualTo(1);
        });
    }

    // ── MDC record interceptor ────────────────────────────────────

    @Test
    void mdcRecordInterceptor_setsRequestIdFromCorrelationIdHeader() {
        String correlationId = "trace-kafka-xyz-123";
        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader("correlationId", correlationId.getBytes(StandardCharsets.UTF_8)));
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(
                "task-changed", 0, 0L,
                ConsumerRecord.NO_TIMESTAMP, TimestampType.NO_TIMESTAMP_TYPE,
                -1, -1, "key", "value", headers, Optional.empty());

        mdcRecordInterceptor.intercept(record, null);

        assertThat(MDC.get("requestId")).isEqualTo(correlationId);
        assertThat(MDC.get("kafkaTopic")).isEqualTo("task-changed");
        assertThat(MDC.get("kafkaPartition")).isEqualTo("0");
    }

    @Test
    void mdcRecordInterceptor_generatesFallbackRequestIdWhenNoHeader() {
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(
                "task-changed", 0, 0L,
                ConsumerRecord.NO_TIMESTAMP, TimestampType.NO_TIMESTAMP_TYPE,
                -1, -1, "key", "value", new RecordHeaders(), Optional.empty());

        mdcRecordInterceptor.intercept(record, null);

        assertThat(MDC.get("requestId"))
                .isNotNull()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void mdcRecordInterceptor_clearsMdcAfterRecord() {
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(
                "task-changed", 0, 0L,
                ConsumerRecord.NO_TIMESTAMP, TimestampType.NO_TIMESTAMP_TYPE,
                -1, -1, "key", "value", new RecordHeaders(), Optional.empty());

        mdcRecordInterceptor.intercept(record, null);
        assertThat(MDC.get("requestId")).isNotNull();

        mdcRecordInterceptor.afterRecord(record, null);
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("kafkaTopic")).isNull();
    }
}
