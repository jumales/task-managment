-- Partial index to support queries filtering file metadata by uploader.
-- Partial so soft-deleted rows are excluded from index scans.
CREATE INDEX idx_file_metadata_uploaded_by
    ON file_metadata (uploaded_by)
    WHERE deleted_at IS NULL;

-- Partial index to support queries filtering by bucket (e.g. avatars vs attachments).
CREATE INDEX idx_file_metadata_bucket
    ON file_metadata (bucket)
    WHERE deleted_at IS NULL;
