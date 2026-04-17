package com.demo.task.archive;

import com.demo.common.event.TaskEvent;
import com.demo.task.config.TtlProperties;
import com.demo.task.model.Task;
import com.demo.task.outbox.OutboxWriter;
import com.demo.task.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Archives closed tasks (RELEASED or REJECTED) that have exceeded the configured TTL window.
 *
 * <p>For each eligible task the service:
 * <ol>
 *   <li>Copies the task and all its related rows to {@code archive.{table}_{YYYYMM}} tables
 *       (YYYYMM is derived from the task's {@code created_at}).</li>
 *   <li>Hard-deletes the rows from the main tables (bypassing the soft-delete hook).</li>
 *   <li>Publishes a {@link com.demo.common.event.TaskEventType#ARCHIVED} event via the transactional outbox
 *       so audit-service, reporting-service, search-service, and file-service can clean up their projections.</li>
 * </ol>
 *
 * <p>All archive operations for a single batch run in one transaction. A failure rolls back
 * the entire batch; the scheduler retries on the next run.
 */
@Service
public class TaskArchiveService {

    private static final Logger log = LoggerFactory.getLogger(TaskArchiveService.class);
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    /** Base tables archived per task, in deletion order (children before parent). */
    private static final List<String> CHILD_TABLES = List.of(
            "task_code_jobs",
            "task_comments",
            "task_participants",
            "task_planned_works",
            "task_booked_works",
            "task_timelines",
            "task_attachments"
    );

    /** Tables that are archived (copied to archive schema) before deletion. task_code_jobs is deleted only. */
    private static final List<String> ARCHIVED_TABLES = List.of(
            "task_comments",
            "task_participants",
            "task_planned_works",
            "task_booked_works",
            "task_timelines",
            "task_attachments"
    );

    private final TaskRepository taskRepository;
    private final ArchiveTableManager archiveTableManager;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final OutboxWriter outboxWriter;
    private final TtlProperties ttlProperties;

    public TaskArchiveService(TaskRepository taskRepository,
                              ArchiveTableManager archiveTableManager,
                              NamedParameterJdbcTemplate namedJdbc,
                              OutboxWriter outboxWriter,
                              TtlProperties ttlProperties) {
        this.taskRepository = taskRepository;
        this.archiveTableManager = archiveTableManager;
        this.namedJdbc = namedJdbc;
        this.outboxWriter = outboxWriter;
        this.ttlProperties = ttlProperties;
    }

    /**
     * Finds tasks eligible for archiving (in RELEASED or REJECTED phase, closed more than
     * {@code ttl.task.archive-after-closed-days} ago) and archives one batch atomically.
     *
     * <p>All archive table creation and data movement happen inside a single transaction.
     * A failure rolls back the entire batch; the scheduler retries on the next nightly run.
     */
    @Transactional
    public void archiveExpiredTasks() {
        int days = ttlProperties.getTask().getArchiveAfterClosedDays();
        int batchSize = ttlProperties.getTask().getBatchSize();
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);

        List<Task> tasks = taskRepository.findExpiredClosedTasks(cutoff, batchSize);
        if (tasks.isEmpty()) {
            log.debug("No tasks eligible for archiving (cutoff={})", cutoff);
            return;
        }

        log.info("Archiving {} expired task(s) (closed before {})", tasks.size(), cutoff);

        // Group tasks by the year-month of their creation date to determine archive table suffix.
        Map<YearMonth, List<Task>> byMonth = tasks.stream()
                .collect(Collectors.groupingBy(t -> toYearMonth(t.getCreatedAt())));

        for (Map.Entry<YearMonth, List<Task>> entry : byMonth.entrySet()) {
            archiveBatch(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Archives all tasks in the given month group: copies related data to archive tables,
     * collects file IDs, hard-deletes from main tables, and publishes outbox events.
     */
    private void archiveBatch(YearMonth month, List<Task> tasks) {
        List<UUID> taskIds = tasks.stream().map(Task::getId).toList();
        Map<String, Object> params = Map.of("ids", taskIds);

        // Ensure all 6 archive tables exist for this month before inserting.
        for (String table : ARCHIVED_TABLES) {
            archiveTableManager.ensureTableExists(table, month);
        }
        // Also ensure the tasks archive table exists.
        archiveTableManager.ensureTableExists("tasks", month);

        // Collect file IDs from task_attachments before any deletion.
        List<UUID> fileIds = namedJdbc.queryForList(
                "SELECT file_id FROM task_attachments WHERE task_id IN (:ids)", params, UUID.class);

        // Copy related rows to archive tables (includes soft-deleted rows — complete history).
        for (String table : ARCHIVED_TABLES) {
            String archiveName = "archive." + archiveTableManager.toArchiveName(table, month);
            namedJdbc.update(
                    "INSERT INTO " + archiveName + " SELECT * FROM " + table + " WHERE task_id IN (:ids)",
                    params);
        }

        // Copy task rows themselves to the archive.
        String archiveTasks = "archive." + archiveTableManager.toArchiveName("tasks", month);
        namedJdbc.update("INSERT INTO " + archiveTasks + " SELECT * FROM tasks WHERE id IN (:ids)", params);

        // Hard-delete child rows in FK-safe order (children before parent).
        for (String table : CHILD_TABLES) {
            String fkColumn = "task_id";
            namedJdbc.update("DELETE FROM " + table + " WHERE " + fkColumn + " IN (:ids)", params);
        }

        // Hard-delete task rows last (main table).
        namedJdbc.update("DELETE FROM tasks WHERE id IN (:ids)", params);

        // Publish one TASK_ARCHIVED outbox event per task so downstream services can clean up.
        String archiveMonth = month.format(MONTH_FORMAT);
        for (Task task : tasks) {
            outboxWriter.writeArchivedEvent(
                    TaskEvent.archived(task.getId(), archiveMonth, fileIds));
        }

        log.info("Archived {} task(s) to {}", tasks.size(), archiveTasks);
    }

    /**
     * Drops archive tables (for all task-related base tables) that are older than the configured
     * {@code ttl.task.archive-retention-months}. Safe to call repeatedly — only expired tables are dropped.
     */
    public void dropExpiredArchiveTables() {
        int retentionMonths = ttlProperties.getTask().getArchiveRetentionMonths();
        List<String> allTables = List.of("tasks", "task_comments", "task_participants",
                "task_planned_works", "task_booked_works", "task_timelines",
                "task_attachments", "task_code_jobs");
        for (String table : allTables) {
            archiveTableManager.dropExpiredTables(table, retentionMonths);
        }
    }

    private YearMonth toYearMonth(Instant instant) {
        return YearMonth.from(instant.atZone(ZoneOffset.UTC));
    }
}
