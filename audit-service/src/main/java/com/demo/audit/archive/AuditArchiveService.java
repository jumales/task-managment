package com.demo.audit.archive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Archives audit records for a task into month-partitioned tables in the {@code archive} schema.
 *
 * <p>All five audit tables ({@code audit_records}, {@code comment_audit_records},
 * {@code phase_audit_records}, {@code planned_work_audit_records}, {@code booked_work_audit_records})
 * are copied to {@code archive.{table}_{YYYYMM}} and then hard-deleted from the main tables.
 *
 * <p>The YYYYMM suffix is derived from the {@code archiveMonth} field in the incoming
 * {@link com.demo.common.event.TaskEvent}, which was set from the task's {@code created_at} in task-service.
 */
@Service
public class AuditArchiveService {

    private static final Logger log = LoggerFactory.getLogger(AuditArchiveService.class);

    private static final List<String> AUDIT_TABLES = List.of(
            "audit_records",
            "comment_audit_records",
            "phase_audit_records",
            "planned_work_audit_records",
            "booked_work_audit_records"
    );

    @Value("${ttl.audit.archive-retention-months:24}")
    private int archiveRetentionMonths;

    private final AuditArchiveTableManager tableManager;
    private final NamedParameterJdbcTemplate namedJdbc;

    public AuditArchiveService(AuditArchiveTableManager tableManager,
                               NamedParameterJdbcTemplate namedJdbc) {
        this.tableManager = tableManager;
        this.namedJdbc = namedJdbc;
    }

    /**
     * Archives all audit records for the given task into the specified month partition,
     * then hard-deletes them from the main tables.
     *
     * @param taskId       the task whose audit records should be archived
     * @param archiveMonth format {@code "YYYYMM"} (from task creation date)
     */
    @Transactional
    public void archiveTask(UUID taskId, String archiveMonth) {
        YearMonth month = YearMonth.parse(archiveMonth,
                java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        Map<String, Object> params = Map.of("taskId", taskId);

        for (String table : AUDIT_TABLES) {
            tableManager.ensureTableExists(table, month);
            String archiveName = "archive." + tableManager.toArchiveName(table, month);
            namedJdbc.update(
                    "INSERT INTO " + archiveName + " SELECT * FROM " + table + " WHERE task_id = :taskId",
                    params);
            namedJdbc.update("DELETE FROM " + table + " WHERE task_id = :taskId", params);
        }

        log.info("Archived audit records for task={} to month={}", taskId, archiveMonth);
    }

    /**
     * Drops audit archive tables that are older than the configured retention period.
     * Safe to call repeatedly — only expired tables are dropped.
     */
    public void dropExpiredArchiveTables() {
        for (String table : AUDIT_TABLES) {
            tableManager.dropExpiredTables(table, archiveRetentionMonths);
        }
    }
}
