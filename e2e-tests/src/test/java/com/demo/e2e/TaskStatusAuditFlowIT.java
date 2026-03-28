package com.demo.e2e;

import com.demo.audit.model.AuditRecord;
import com.demo.common.dto.TaskStatus;
import com.demo.common.event.TaskChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.data.domain.Pageable;

import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies the full Kafka → audit-service path for task status changes.
 *
 * <p>Publishes a {@code STATUS_CHANGED} event and asserts that:
 * <ul>
 *   <li>The audit-service consumer persists an {@link AuditRecord} in the database.</li>
 *   <li>{@code GET /api/v1/audit/tasks/{id}/statuses} returns the correct history.</li>
 * </ul>
 */
class TaskStatusAuditFlowIT extends BaseE2ETest {

    @Test
    void statusChangedEvent_persistsAuditRecord() {
        TaskChangedEvent event = TaskChangedEvent.statusChanged(
                taskId, userId, null, null, TaskStatus.TODO, TaskStatus.IN_PROGRESS);

        publish(event);

        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(auditRepository.findByTaskIdOrderByChangedAtAsc(taskId, Pageable.unpaged()).getContent()).hasSize(1));

        AuditRecord record = auditRepository.findByTaskIdOrderByChangedAtAsc(taskId, Pageable.unpaged()).getContent().get(0);
        assertThat(record.getTaskId()).isEqualTo(taskId);
        assertThat(record.getAssignedUserId()).isEqualTo(userId);
        assertThat(record.getFromStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(record.getToStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(record.getChangedAt()).isNotNull();
        assertThat(record.getRecordedAt()).isNotNull();
    }

    @Test
    void multipleStatusChanges_persistedInOrder() {
        publish(TaskChangedEvent.statusChanged(taskId, userId, null, null,
                TaskStatus.TODO, TaskStatus.IN_PROGRESS));
        publish(TaskChangedEvent.statusChanged(taskId, userId, null, null,
                TaskStatus.IN_PROGRESS, TaskStatus.DONE));

        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(auditRepository.findByTaskIdOrderByChangedAtAsc(taskId, Pageable.unpaged()).getContent()).hasSize(2));

        List<AuditRecord> records = auditRepository.findByTaskIdOrderByChangedAtAsc(taskId, Pageable.unpaged()).getContent();
        assertThat(records.get(0).getToStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(records.get(1).getToStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void statusHistory_endpoint_returnsPersistedRecords() {
        publish(TaskChangedEvent.statusChanged(taskId, userId, null, null,
                TaskStatus.TODO, TaskStatus.IN_PROGRESS));

        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(auditRepository.findByTaskIdOrderByChangedAtAsc(taskId, Pageable.unpaged()).getContent()).hasSize(1));

        ResponseEntity<List<AuditRecord>> response = restTemplate.exchange(
                url("/api/v1/audit/tasks/" + taskId + "/statuses"),
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getFromStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(response.getBody().get(0).getToStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }
}
