package com.demo.e2e;

import com.demo.audit.model.CommentAuditRecord;
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
 * Verifies the full Kafka → audit-service path for comment additions.
 *
 * <p>Publishes a {@code COMMENT_ADDED} event and asserts that:
 * <ul>
 *   <li>The audit-service consumer persists a {@link CommentAuditRecord}.</li>
 *   <li>{@code GET /api/v1/audit/tasks/{id}/comments} returns the comment history.</li>
 * </ul>
 */
class TaskCommentAuditFlowIT extends BaseE2ETest {

    @Test
    void commentAddedEvent_persistsCommentAuditRecord() {
        UUID commentId = UUID.randomUUID();
        TaskChangedEvent event = TaskChangedEvent.commentAdded(
                taskId, userId, null, null, commentId, "First comment");

        publish(event);

        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(commentAuditRepository.findByTaskIdOrderByAddedAtAsc(taskId, Pageable.unpaged()).getContent()).hasSize(1));

        CommentAuditRecord record = commentAuditRepository.findByTaskIdOrderByAddedAtAsc(taskId, Pageable.unpaged()).getContent().get(0);
        assertThat(record.getTaskId()).isEqualTo(taskId);
        assertThat(record.getCommentCreatedByUserId()).isEqualTo(userId);
        assertThat(record.getCommentId()).isEqualTo(commentId);
        assertThat(record.getContent()).isEqualTo("First comment");
        assertThat(record.getAddedAt()).isNotNull();
        assertThat(record.getRecordedAt()).isNotNull();
    }

    @Test
    void multipleComments_allPersistedInOrder() {
        publish(TaskChangedEvent.commentAdded(taskId, userId, null, null, UUID.randomUUID(), "First"));
        publish(TaskChangedEvent.commentAdded(taskId, userId, null, null, UUID.randomUUID(), "Second"));
        publish(TaskChangedEvent.commentAdded(taskId, userId, null, null, UUID.randomUUID(), "Third"));

        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(commentAuditRepository.findByTaskIdOrderByAddedAtAsc(taskId, Pageable.unpaged()).getContent()).hasSize(3));

        List<CommentAuditRecord> records = commentAuditRepository.findByTaskIdOrderByAddedAtAsc(taskId, Pageable.unpaged()).getContent();
        assertThat(records).extracting(CommentAuditRecord::getContent)
                .containsExactly("First", "Second", "Third");
    }

    @Test
    void commentHistory_endpoint_returnsPersistedRecords() {
        UUID commentId = UUID.randomUUID();
        publish(TaskChangedEvent.commentAdded(taskId, userId, null, null, commentId, "Persisted comment"));

        await().atMost(15, SECONDS).untilAsserted(() ->
                assertThat(commentAuditRepository.findByTaskIdOrderByAddedAtAsc(taskId, Pageable.unpaged()).getContent()).hasSize(1));

        ResponseEntity<List<CommentAuditRecord>> response = restTemplate.exchange(
                url("/api/v1/audit/tasks/" + taskId + "/comments"),
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getContent()).isEqualTo("Persisted comment");
        assertThat(response.getBody().get(0).getCommentId()).isEqualTo(commentId);
    }
}
