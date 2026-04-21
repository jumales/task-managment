package com.demo.file.scheduler;

import com.demo.file.model.FileMetadata;
import com.demo.file.repository.FileMetadataRepository;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Permanently removes MinIO objects whose file_metadata records have been soft-deleted
 * for longer than the configured retention period. After each successful MinIO deletion
 * the corresponding metadata row is hard-deleted from the database.
 */
@Component
public class FileCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(FileCleanupScheduler.class);

    @Value("${ttl.file.deleted-object-retention-days:30}")
    private int deletedObjectRetentionDays;

    private final FileMetadataRepository fileMetadataRepository;
    private final MinioClient minioClient;

    public FileCleanupScheduler(FileMetadataRepository fileMetadataRepository, MinioClient minioClient) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.minioClient = minioClient;
    }

    private static final int CLEANUP_BATCH_SIZE = 100;

    /** Runs nightly at 06:00; purges MinIO objects and metadata rows past their retention window in batches. */
    @Scheduled(cron = "0 0 6 * * *")
    public void cleanup() {
        Instant cutoff = Instant.now().minus(deletedObjectRetentionDays, ChronoUnit.DAYS);
        int totalPurged = 0;
        List<FileMetadata> batch;

        // Always query offset 0 — processed rows are hard-deleted so they disappear from subsequent batches
        do {
            batch = fileMetadataRepository.findExpiredDeletedFiles(cutoff, PageRequest.of(0, CLEANUP_BATCH_SIZE));
            for (FileMetadata file : batch) {
                purge(file);
            }
            totalPurged += batch.size();
        } while (batch.size() == CLEANUP_BATCH_SIZE);

        log.info("Cleanup complete: purged {} expired file(s) deleted before {}", totalPurged, cutoff);
    }

    /** Removes the MinIO object then hard-deletes the metadata row; logs and skips on error. */
    private void purge(FileMetadata file) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(file.getBucket())
                            .object(file.getObjectKey())
                            .build());
            fileMetadataRepository.deleteById(file.getId());
            log.info("Purged file id={} bucket={} key={}", file.getId(), file.getBucket(), file.getObjectKey());
        } catch (Exception ex) {
            log.error("Failed to purge file id={}: {}", file.getId(), ex.getMessage(), ex);
        }
    }
}
