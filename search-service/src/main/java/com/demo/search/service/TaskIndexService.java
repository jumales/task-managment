package com.demo.search.service;

import com.demo.common.dto.TaskParticipantResponse;
import com.demo.common.dto.TaskParticipantRole;
import com.demo.common.dto.TaskResponse;
import com.demo.common.event.TaskEvent;
import com.demo.search.document.TaskDocument;
import com.demo.search.repository.TaskSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Manages the Elasticsearch task index: indexes task documents on CREATED/UPDATED events
 * and removes them on DELETED events.
 */
@Service
public class TaskIndexService {

    private static final Logger log = LoggerFactory.getLogger(TaskIndexService.class);

    private final TaskSearchRepository repository;

    public TaskIndexService(TaskSearchRepository repository) {
        this.repository = repository;
    }

    /** Indexes or re-indexes a task document from a CREATED or UPDATED event. */
    public void index(TaskEvent event) {
        TaskDocument doc = TaskDocument.builder()
                .id(event.getTaskId().toString())
                .title(event.getTitle())
                .description(event.getDescription())
                .status(event.getStatus() != null ? event.getStatus().name() : null)
                .projectId(event.getProjectId() != null ? event.getProjectId().toString() : null)
                .projectName(event.getProjectName())
                .phaseId(event.getPhaseId() != null ? event.getPhaseId().toString() : null)
                .phaseName(event.getPhaseName())
                .assignedUserId(event.getAssignedUserId() != null ? event.getAssignedUserId().toString() : null)
                .assignedUserName(event.getAssignedUserName())
                .build();
        repository.save(doc);
        log.info("Indexed task {} ({})", event.getTaskId(), event.getEventType());
    }

    /** Removes a task document from the index on a DELETED event. */
    public void delete(TaskEvent event) {
        repository.deleteById(event.getTaskId().toString());
        log.info("Removed task {} from index", event.getTaskId());
    }

    /** Bulk-indexes a list of tasks fetched from task-service during re-indexing. */
    public void indexAll(List<TaskResponse> tasks) {
        List<TaskDocument> docs = tasks.stream()
                .map(t -> TaskDocument.builder()
                        .id(t.getId().toString())
                        .title(t.getTitle())
                        .description(t.getDescription())
                        .status(t.getStatus() != null ? t.getStatus().name() : null)
                        .projectId(t.getProject() != null ? t.getProject().getId().toString() : null)
                        .projectName(t.getProject() != null ? t.getProject().getName() : null)
                        .phaseId(t.getPhase() != null ? t.getPhase().getId().toString() : null)
                        .phaseName(t.getPhase() != null ? t.getPhase().getName() : null)
                        .assignedUserId(findAssignee(t).map(p -> p.getUserId().toString()).orElse(null))
                        .assignedUserName(findAssignee(t).map(TaskParticipantResponse::getUserName).orElse(null))
                        .build())
                .toList();
        repository.saveAll(docs);
        log.info("Bulk-indexed {} tasks", docs.size());
    }

    /** Returns the ASSIGNEE participant from the task's participant list, if present. */
    private Optional<TaskParticipantResponse> findAssignee(TaskResponse task) {
        if (task.getParticipants() == null) return Optional.empty();
        return task.getParticipants().stream()
                .filter(p -> p.getRole() == TaskParticipantRole.ASSIGNEE)
                .findFirst();
    }

    /**
     * Searches tasks by full-text query across title and description.
     *
     * @param query free-text search string
     * @return matching task documents
     */
    public List<TaskDocument> search(String query) {
        return repository.search(query);
    }
}
