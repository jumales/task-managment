package com.demo.task.archive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Manages the lifecycle of dynamic archive tables in the {@code archive} PostgreSQL schema.
 *
 * <p>Archive tables mirror their source table's structure (columns, constraints, indexes)
 * using {@code LIKE ... INCLUDING ALL}. FK constraints are intentionally excluded — FK targets
 * do not exist in the archive schema.
 *
 * <p>Table names are built exclusively from a hardcoded base-table allowlist and a
 * {@link YearMonth} object, so no user input ever reaches the DDL statements.
 */
@Component
public class ArchiveTableManager {

    private static final Logger log = LoggerFactory.getLogger(ArchiveTableManager.class);
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private final JdbcTemplate jdbcTemplate;

    public ArchiveTableManager(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Creates the archive table for the given base table and month if it does not already exist.
     * Uses {@code LIKE INCLUDING ALL} to mirror columns, NOT NULL constraints, check constraints,
     * indexes, and defaults — but excludes FK constraints.
     */
    public void ensureTableExists(String baseTable, YearMonth month) {
        String archiveName = toArchiveName(baseTable, month);
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS archive." + archiveName +
                " (LIKE " + baseTable + " INCLUDING ALL)");
        log.debug("Ensured archive table exists: archive.{}", archiveName);
    }

    /**
     * Drops all archive tables for the given base table whose month is strictly older than
     * {@code retentionMonths} months ago (exclusive of the cutoff month itself).
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
                log.info("Dropped expired archive table: archive.{}", table);
            }
        }
    }

    /** Returns the archive table name for the given base table and month, e.g. {@code tasks_202401}. */
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
