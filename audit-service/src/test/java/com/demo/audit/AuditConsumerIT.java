package com.demo.audit;

import com.demo.audit.model.AuditRecord;
import com.demo.audit.model.CommentAuditRecord;
import com.demo.audit.model.PhaseAuditRecord;
import com.demo.audit.model.WorkLogAuditRecord;
import com.demo.audit.repository.AuditRepository;
import com.demo.audit.repository.CommentAuditRepository;
import com.demo.audit.repository.PhaseAuditRepository;
import com.demo.audit.repository.WorkLogAuditRepository;
import com.demo.common.dto.TaskStatus;
import com.demo.common.dto.WorkType;
import com.demo.common.event.TaskChangeType;
import com.demo.common.event.TaskChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigInteger;
import java.util.List;
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
    WorkLogAuditRepository workLogAuditRepository;

    @Autowired
    CommentAuditRepository commentAuditRepository;

    @Autowired
    PhaseAuditRepository phaseAuditRepository;

    @BeforeEach
    void setUp() {
        auditRepository.deleteAll();
        commentAuditRepository.deleteAll();
        phaseAuditRepository.deleteAll();
        workLogAuditRepository.deleteAll();
    }

    // ── Status events ─────────────────────────────────────────────

    @Test
    void consumeStatusEvent_persistsAuditRecord() {
        UUID taskId = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.statusChanged(taskId, UUID.randomUUID(), null, null,
                        TaskStatus.TODO, TaskStatus.IN_PROGRESS));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            List<AuditRecord> records = auditRepository.findByTaskIdOrderByChangedAtAsc(taskId);
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
            List<AuditRecord> records = auditRepository.findByTaskIdOrderByChangedAtAsc(taskId);
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
            List<CommentAuditRecord> records = commentAuditRepository.findByTaskIdOrderByAddedAtAsc(taskId);
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
            List<CommentAuditRecord> records = commentAuditRepository.findByTaskIdOrderByAddedAtAsc(taskId);
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
            assertThat(auditRepository.findByTaskIdOrderByChangedAtAsc(taskId)).hasSize(1);
            assertThat(commentAuditRepository.findByTaskIdOrderByAddedAtAsc(taskId)).hasSize(1);
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
            List<PhaseAuditRecord> records = phaseAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId);
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
            List<PhaseAuditRecord> records = phaseAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId);
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
            assertThat(auditRepository.findByTaskIdOrderByChangedAtAsc(taskId)).hasSize(1);
            assertThat(commentAuditRepository.findByTaskIdOrderByAddedAtAsc(taskId)).hasSize(1);
            assertThat(phaseAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId)).hasSize(1);
        });
    }

    // ── Work log events ───────────────────────────────────────────

    @Test
    void consumeWorkLogCreatedEvent_persistsAuditRecord() {
        UUID taskId    = UUID.randomUUID();
        UUID workLogId = UUID.randomUUID();
        UUID userId    = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.workLogCreated(taskId, null, null, workLogId, userId,
                        WorkType.DEVELOPMENT, BigInteger.valueOf(8), BigInteger.valueOf(3)));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            List<WorkLogAuditRecord> records = workLogAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId);
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getWorkLogId()).isEqualTo(workLogId);
            assertThat(records.get(0).getChangeType()).isEqualTo(TaskChangeType.WORK_LOG_CREATED);
            assertThat(records.get(0).getWorkLogUserId()).isEqualTo(userId);
            assertThat(records.get(0).getWorkType()).isEqualTo(WorkType.DEVELOPMENT);
            assertThat(records.get(0).getPlannedHours()).isEqualTo(8);
            assertThat(records.get(0).getBookedHours()).isEqualTo(3);
            assertThat(records.get(0).getRecordedAt()).isNotNull();
        });
    }

    @Test
    void consumeWorkLogUpdatedEvent_persistsAuditRecord() {
        UUID taskId    = UUID.randomUUID();
        UUID workLogId = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.workLogUpdated(taskId, null, null, workLogId, UUID.randomUUID(),
                        WorkType.TESTING, BigInteger.valueOf(4), BigInteger.valueOf(4)));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            List<WorkLogAuditRecord> records = workLogAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId);
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getChangeType()).isEqualTo(TaskChangeType.WORK_LOG_UPDATED);
            assertThat(records.get(0).getWorkType()).isEqualTo(WorkType.TESTING);
        });
    }

    @Test
    void consumeWorkLogDeletedEvent_persistsAuditRecord() {
        UUID taskId    = UUID.randomUUID();
        UUID workLogId = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.workLogDeleted(taskId, null, null, workLogId));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            List<WorkLogAuditRecord> records = workLogAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId);
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getChangeType()).isEqualTo(TaskChangeType.WORK_LOG_DELETED);
            assertThat(records.get(0).getWorkLogId()).isEqualTo(workLogId);
            assertThat(records.get(0).getPlannedHours()).isNull();
            assertThat(records.get(0).getBookedHours()).isNull();
        });
    }

    @Test
    void multipleWorkLogEvents_allPersistedInOrder() {
        UUID taskId    = UUID.randomUUID();
        UUID workLogId = UUID.randomUUID();

        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.workLogCreated(taskId, null, null, workLogId, UUID.randomUUID(),
                        WorkType.DEVELOPMENT, BigInteger.valueOf(8), BigInteger.valueOf(0)));
        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.workLogUpdated(taskId, null, null, workLogId, UUID.randomUUID(),
                        WorkType.DEVELOPMENT, BigInteger.valueOf(8), BigInteger.valueOf(5)));
        kafkaTemplate.send("task-changed", taskId.toString(),
                TaskChangedEvent.workLogDeleted(taskId, null, null, workLogId));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            List<WorkLogAuditRecord> records = workLogAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId);
            assertThat(records).hasSize(3);
            assertThat(records.get(0).getChangeType()).isEqualTo(TaskChangeType.WORK_LOG_CREATED);
            assertThat(records.get(1).getChangeType()).isEqualTo(TaskChangeType.WORK_LOG_UPDATED);
            assertThat(records.get(2).getChangeType()).isEqualTo(TaskChangeType.WORK_LOG_DELETED);
        });
    }
}
