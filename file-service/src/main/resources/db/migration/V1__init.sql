CREATE TABLE file_metadata (
    id                UUID        NOT NULL PRIMARY KEY,
    bucket            VARCHAR(100) NOT NULL,
    object_key        VARCHAR(500) NOT NULL,
    content_type      VARCHAR(100),
    original_filename VARCHAR(500),
    uploaded_by       VARCHAR(255),
    uploaded_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMP WITH TIME ZONE
);
