package com.demo.audit.archive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Manages the lifecycle of dynamic archive tables in the {@code archive} schema of the audit database.
 * Archive tables mirror the source table's structure using {@code LIKE ... INCLUDING ALL}.
 *
 * <p>Table names are built from a hardcoded base-table string and a {@link YearMonth} object —
 * no user input ever reaches the DDL statements.
 */
@Component
public class AuditArchiveTableManager {

    private static final Logger log = LoggerFactory.getLogger(AuditArchiveTableManager.class);
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private final JdbcTemplate jdbcTemplate;

    public AuditArchiveTableManager(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Creates the archive table for the given base table and month if it does not already exist. */
    public void ensureTableExists(String baseTable, YearMonth month) {
        String archiveName = toArchiveName(baseTable, month);
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS archive." + archiveName +
                " (LIKE " + baseTable + " INCLUDING ALL)");
        log.debug("Ensured audit archive table exists: archive.{}", archiveName);
    }

    /**
     * Drops all archive tables for the given base table whose month is strictly older than
     * {@code retentionMonths} months ago.
     */
    public void dropExpiredTables(String baseTable, int retentionMonths) {
        YearMonth cutoff = YearMonth.now().minusMonths(retentionMonths);
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'archive' AND tablename LIKE ?",
                String.class, baseTable + "_%");

        for (String table : tables) {
            YearMonth tableMonth = parseMonth(table, baseTable);
            if (tableMonth != null && tableMonth.isBefore(cutoff)) {
                jdbcTemplate.execute("DROP TABLE IF EXISTS archive." + table);
                log.info("Dropped expired audit archive table: archive.{}", table);
            }
        }
    }

    /** Returns the archive table name, e.g. {@code audit_records_202401}. */
    public String toArchiveName(String baseTable, YearMonth month) {
        return baseTable + "_" + month.format(MONTH_FORMAT);
    }

    private YearMonth parseMonth(String tableName, String baseTable) {
        String prefix = baseTable + "_";
        if (!tableName.startsWith(prefix)) return null;
        String suffix = tableName.substring(prefix.length());
        try {
            return YearMonth.parse(suffix, MONTH_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }
}
