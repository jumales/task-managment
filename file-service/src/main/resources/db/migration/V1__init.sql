CREATE TABLE file_metadata (
    id                UUID         NOT NULL PRIMARY KEY,
    bucket            VARCHAR(100) NOT NULL,
    object_key        VARCHAR(500) NOT NULL,
    content_type      VARCHAR(100),
    original_filename VARCHAR(500),
    uploaded_by       VARCHAR(255),
    uploaded_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_file_metadata_uploaded_by ON file_metadata (uploaded_by) WHERE deleted_at IS NULL;
CREATE INDEX idx_file_metadata_bucket      ON file_metadata (bucket)      WHERE deleted_at IS NULL;
