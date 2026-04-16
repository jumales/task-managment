package com.demo.file.service;

import com.demo.common.dto.FileUploadResponse;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.file.config.MinioProperties;
import com.demo.file.config.MinioProperties.BucketProperties;
import com.demo.file.model.FileMetadata;
import com.demo.file.repository.FileMetadataRepository;
import io.minio.*;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Handles file upload, URL resolution, and soft-deletion backed by MinIO object storage.
 * File metadata is persisted to PostgreSQL so files can be looked up by a stable UUID
 * regardless of the underlying object key or bucket.
 *
 * <p>Each bucket has configurable allowed MIME types and a maximum file size defined in
 * {@link MinioProperties}. Adding a new bucket type requires only a YAML entry — no code change.
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final int PRESIGNED_URL_EXPIRY_HOURS = 1;

    private final MinioClient minioClient;
    private final FileMetadataRepository repository;
    private final MinioProperties properties;

    public FileService(MinioClient minioClient,
                       FileMetadataRepository repository,
                       MinioProperties properties) {
        this.minioClient = minioClient;
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * Validates and uploads a file to the given bucket, persists metadata, and returns the file record.
     *
     * @param file     the multipart file from the HTTP request
     * @param bucket   target MinIO bucket name — must match a key in {@code minio.buckets}
     * @param uploader JWT subject of the caller
     * @throws IllegalArgumentException if the content type or file size violates the bucket's rules
     */
    public FileUploadResponse upload(MultipartFile file, String bucket, String uploader) {
        BucketProperties bucketConfig = resolveBucketConfig(bucket);
        validateFile(file, bucketConfig);

        UUID fileId = generateUuidV7();
        // Strip directory components (e.g. "../") to prevent path-traversal in the MinIO object key.
        String safeFilename = Paths.get(file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload")
                .getFileName()
                .toString();
        String objectKey = fileId + "_" + safeFilename;
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        putObject(file, bucket, objectKey, contentType);

        FileMetadata metadata = FileMetadata.builder()
                .id(fileId)
                .bucket(bucket)
                .objectKey(objectKey)
                .contentType(contentType)
                .originalFilename(safeFilename)
                .uploadedBy(uploader)
                .uploadedAt(Instant.now())
                .build();

        repository.save(metadata);
        return new FileUploadResponse(fileId, bucket, objectKey, contentType);
    }

    /**
     * Returns a time-limited presigned GET URL for the file identified by {@code fileId}.
     * The URL expires after {@value PRESIGNED_URL_EXPIRY_HOURS} hour(s).
     *
     * @throws ResourceNotFoundException if no active file record exists for the given ID
     */
    public String getPresignedUrl(UUID fileId) {
        FileMetadata metadata = repository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File", fileId));
        return generatePresignedUrl(metadata.getBucket(), metadata.getObjectKey());
    }

    /**
     * Streams the raw file bytes from MinIO for the given file ID.
     * Returns both the {@link Resource} and the original content type so the
     * controller can set the correct {@code Content-Type} response header.
     *
     * @throws ResourceNotFoundException if no active file record exists for the given ID
     */
    public DownloadResult download(UUID fileId) {
        FileMetadata metadata = repository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File", fileId));
        try {
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(metadata.getBucket())
                            .object(metadata.getObjectKey())
                            .build());
            return new DownloadResult(new InputStreamResource(stream), metadata.getContentType());
        } catch (Exception e) {
            throw new RuntimeException("Failed to download file from MinIO: " + e.getMessage(), e);
        }
    }

    /** Carries the file bytes and content type from a MinIO download. */
    public record DownloadResult(Resource resource, String contentType) {}

    /**
     * Deletes the file record and removes the object from MinIO.
     * Only the original uploader or an admin caller may delete a file.
     *
     * @param fileId        ID of the file to delete
     * @param callerSubject JWT subject of the requesting user
     * @param isAdmin       true if the caller has the ADMIN role
     * @throws ResourceNotFoundException if no active file record exists for the given ID
     * @throws AccessDeniedException     if the caller is not the uploader and not an admin
     */
    public void delete(UUID fileId, String callerSubject, boolean isAdmin) {
        FileMetadata metadata = repository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File", fileId));
        if (!isAdmin && !metadata.getUploadedBy().equals(callerSubject)) {
            throw new AccessDeniedException("You are not allowed to delete this file");
        }
        repository.deleteById(metadata.getId());
        removeObjectFromMinio(metadata.getBucket(), metadata.getObjectKey());
    }

    /**
     * Removes the physical object from MinIO.
     * Logs a warning if removal fails — the DB record is already deleted at this point
     * so the logical deletion succeeded regardless.
     */
    private void removeObjectFromMinio(String bucket, String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to remove object from MinIO after DB delete: bucket={} key={}", bucket, objectKey, e);
        }
    }

    /**
     * Looks up per-bucket rules from configuration.
     *
     * @throws IllegalArgumentException if the bucket name is not registered in {@code minio.buckets}
     */
    private BucketProperties resolveBucketConfig(String bucket) {
        BucketProperties config = properties.getBuckets().get(bucket);
        if (config == null) {
            throw new IllegalArgumentException("Unknown bucket: " + bucket);
        }
        return config;
    }

    /**
     * Validates file size and content type against the bucket's rules.
     *
     * @throws IllegalArgumentException if either check fails
     */
    private void validateFile(MultipartFile file, BucketProperties config) {
        if (file.getSize() > config.getMaxSizeBytes()) {
            throw new IllegalArgumentException(String.format(
                    "File size %d bytes exceeds the maximum allowed %d bytes for this bucket",
                    file.getSize(), config.getMaxSizeBytes()));
        }

        validateContentType(file, config.getAllowedTypes());
    }

    /** Throws if the file's content type is not in the allowed list; no-op when the list is empty (all types accepted). */
    private void validateContentType(MultipartFile file, List<String> allowedTypes) {
        if (allowedTypes.isEmpty()) return;
        String contentType = file.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType)) {
            throw new IllegalArgumentException(String.format(
                    "Content type '%s' is not allowed. Accepted types: %s",
                    contentType, allowedTypes));
        }
    }

    /** Streams the file bytes to MinIO. */
    private void putObject(MultipartFile file, String bucket, String objectKey, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to MinIO: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a UUID v7 (time-ordered) for use as a file identifier.
     * UUID v7 embeds the current millisecond timestamp in the most-significant bits,
     * which keeps file records naturally sortable by creation time.
     */
    private static UUID generateUuidV7() {
        long timestamp = Instant.now().toEpochMilli();
        long msb = (timestamp << 16) | 0x7000L | (ThreadLocalRandom.current().nextLong(0x1000));
        long lsb = 0x8000000000000000L | (ThreadLocalRandom.current().nextLong() & 0x3FFFFFFFFFFFFFFFL);
        return new UUID(msb, lsb);
    }

    /** Generates a presigned GET URL valid for {@value PRESIGNED_URL_EXPIRY_HOURS} hour(s). */
    private String generatePresignedUrl(String bucket, String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(PRESIGNED_URL_EXPIRY_HOURS, TimeUnit.HOURS)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate presigned URL: " + e.getMessage(), e);
        }
    }
}
