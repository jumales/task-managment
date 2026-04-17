package com.demo.file.repository;

import com.demo.file.model.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Persistence layer for {@link FileMetadata}. */
public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {

    /**
     * Returns soft-deleted file records whose {@code deleted_at} is older than the given cutoff.
     * Used by {@link com.demo.file.scheduler.FileCleanupScheduler} to find objects ready for
     * permanent removal from MinIO.
     *
     * <p>Bypasses {@code @SQLRestriction("deleted_at IS NULL")} via a native query so
     * soft-deleted rows are visible.
     */
    @Query(value = "SELECT * FROM file_metadata WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff",
            nativeQuery = true)
    List<FileMetadata> findExpiredDeletedFiles(@Param("cutoff") Instant cutoff);

    /**
     * Soft-deletes the file record for the given file ID if it is not already soft-deleted.
     * Used by the TASK_ARCHIVED consumer to mark attached files for deferred MinIO cleanup.
     */
    @Modifying
    @Query(value = "UPDATE file_metadata SET deleted_at = NOW() WHERE id = :fileId AND deleted_at IS NULL",
            nativeQuery = true)
    void softDeleteById(@Param("fileId") UUID fileId);
}
