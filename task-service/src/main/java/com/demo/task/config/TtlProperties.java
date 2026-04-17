package com.demo.task.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configurable TTL and archiving thresholds, bound from {@code ttl.*} in application.yml.
 * All values have safe defaults that preserve data aggressively; tighten in production.
 */
@Component
@ConfigurationProperties(prefix = "ttl")
public class TtlProperties {

    private Task task = new Task();
    private Outbox outbox = new Outbox();
    private TaskCodeJob taskCodeJob = new TaskCodeJob();
    private Kafka kafka = new Kafka();

    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task; }

    public Outbox getOutbox() { return outbox; }
    public void setOutbox(Outbox outbox) { this.outbox = outbox; }

    public TaskCodeJob getTaskCodeJob() { return taskCodeJob; }
    public void setTaskCodeJob(TaskCodeJob taskCodeJob) { this.taskCodeJob = taskCodeJob; }

    public Kafka getKafka() { return kafka; }
    public void setKafka(Kafka kafka) { this.kafka = kafka; }

    /** Archive trigger and retention settings for task rows. */
    public static class Task {
        /** Days after close (RELEASED/REJECTED) before a task is moved to the archive schema. */
        private int archiveAfterClosedDays = 90;
        /** Months to keep archive tables before dropping them entirely. */
        private int archiveRetentionMonths = 12;
        /** Maximum tasks to archive per scheduled run (limits transaction duration). */
        private int batchSize = 100;

        public int getArchiveAfterClosedDays() { return archiveAfterClosedDays; }
        public void setArchiveAfterClosedDays(int archiveAfterClosedDays) { this.archiveAfterClosedDays = archiveAfterClosedDays; }

        public int getArchiveRetentionMonths() { return archiveRetentionMonths; }
        public void setArchiveRetentionMonths(int archiveRetentionMonths) { this.archiveRetentionMonths = archiveRetentionMonths; }

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }

    /** Retention settings for published outbox events. */
    public static class Outbox {
        /** Days to keep published outbox_events rows before deleting them. */
        private int retentionDays = 30;

        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
    }

    /** Retention settings for processed task_code_jobs. */
    public static class TaskCodeJob {
        /** Days to keep processed task_code_jobs rows before deleting them. */
        private int retentionDays = 30;

        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
    }

    /** Kafka topic-level retention settings applied via AdminClient on startup. */
    public static class Kafka {
        /** Hours to retain messages on the task-events topic (default 7 days). */
        private int taskEventsRetentionHours = 168;
        /** Hours to retain messages on the task-changed topic (default 7 days). */
        private int taskChangedRetentionHours = 168;

        public int getTaskEventsRetentionHours() { return taskEventsRetentionHours; }
        public void setTaskEventsRetentionHours(int taskEventsRetentionHours) { this.taskEventsRetentionHours = taskEventsRetentionHours; }

        public int getTaskChangedRetentionHours() { return taskChangedRetentionHours; }
        public void setTaskChangedRetentionHours(int taskChangedRetentionHours) { this.taskChangedRetentionHours = taskChangedRetentionHours; }
    }
}
