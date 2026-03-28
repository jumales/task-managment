package com.demo.e2e;

import com.demo.audit.model.PhaseAuditRecord;
import com.demo.common.event.TaskChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies the full Kafka → audit-service path for phase changes.
 *
 * <p>Publishes a {@code PHASE_CHANGED} event and asserts that:
 * <ul>
 *   <li>The audit-service consumer persists a {@link PhaseAuditRecord}.</li>
 *   <li>{@code GET /api/v1/audit/tasks/{id}/phases} returns the phase history.</li>
 * </ul>
 */
class TaskPhaseAuditFlowIT extends BaseE2ETest {

    @Test
    void phaseChangedEvent_persistsPhaseAuditRecord() {
        UUID fromPhaseId = UUID.randomUUID();
        UUID toPhaseId   = UUID.randomUUID();
        TaskChangedEvent event = TaskChangedEvent.phaseChanged(
                taskId, userId, null, null,
                fromPhaseId, "Backlog", toPhaseId, "In Review");

        publish(event);

        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(phaseAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId, Pageable.unpaged()).getContent()).hasSize(1));

        PhaseAuditRecord record = phaseAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId, Pageable.unpaged()).getContent().get(0);
        assertThat(record.getTaskId()).isEqualTo(taskId);
        assertThat(record.getAssignedUserId()).isEqualTo(userId);
        assertThat(record.getFromPhaseId()).isEqualTo(fromPhaseId);
        assertThat(record.getFromPhaseName()).isEqualTo("Backlog");
        assertThat(record.getToPhaseId()).isEqualTo(toPhaseId);
        assertThat(record.getToPhaseName()).isEqualTo("In Review");
        assertThat(record.getChangedAt()).isNotNull();
        assertThat(record.getRecordedAt()).isNotNull();
    }

    @Test
    void multiplePhaseChanges_persistedInOrder() {
        UUID phaseA = UUID.randomUUID();
        UUID phaseB = UUID.randomUUID();
        UUID phaseC = UUID.randomUUID();

        publish(TaskChangedEvent.phaseChanged(taskId, userId, null, null,
                phaseA, "Backlog", phaseB, "In Progress"));
        publish(TaskChangedEvent.phaseChanged(taskId, userId, null, null,
                phaseB, "In Progress", phaseC, "Done"));

        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(phaseAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId, Pageable.unpaged()).getContent()).hasSize(2));

        List<PhaseAuditRecord> records = phaseAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId, Pageable.unpaged()).getContent();
        assertThat(records.get(0).getToPhaseName()).isEqualTo("In Progress");
        assertThat(records.get(1).getToPhaseName()).isEqualTo("Done");
    }

    @Test
    void phaseHistory_endpoint_returnsPersistedRecords() {
        UUID fromPhaseId = UUID.randomUUID();
        UUID toPhaseId   = UUID.randomUUID();
        publish(TaskChangedEvent.phaseChanged(taskId, userId, null, null,
                fromPhaseId, "Todo", toPhaseId, "Done"));

        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(phaseAuditRepository.findByTaskIdOrderByChangedAtAsc(taskId, Pageable.unpaged()).getContent()).hasSize(1));

        ResponseEntity<List<PhaseAuditRecord>> response = restTemplate.exchange(
                url("/api/v1/audit/tasks/" + taskId + "/phases"),
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getFromPhaseName()).isEqualTo("Todo");
        assertThat(response.getBody().get(0).getToPhaseName()).isEqualTo("Done");
    }
}
