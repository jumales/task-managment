package com.demo.file.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed configuration for MinIO connection and per-bucket upload rules.
 *
 * <p>Each entry in {@code buckets} maps a bucket name (e.g. "avatars") to its
 * {@link BucketProperties}. Adding a new bucket type requires only a new YAML
 * entry — no Java changes needed.
 *
 * <pre>
 * minio:
 *   url: http://localhost:9000
 *   access-key: minio
 *   secret-key: minio123
 *   buckets:
 *     avatars:
 *       allowed-types: [image/jpeg, image/png]
 *       max-size-bytes: 5242880
 *     attachments:
 *       allowed-types: [application/pdf, text/plain]
 *       max-size-bytes: 20971520
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    private String url;
    private String accessKey;
    private String secretKey;

    /** Per-bucket upload rules; key is the bucket name used in MinIO. */
    private Map<String, BucketProperties> buckets = new HashMap<>();

    @Getter
    @Setter
    public static class BucketProperties {

        /**
         * Accepted MIME types (e.g. "image/jpeg").
         * An empty list means all content types are accepted.
         */
        private List<String> allowedTypes = new ArrayList<>();

        /**
         * Maximum permitted file size in bytes.
         * Defaults to 10 MB if not configured.
         */
        private long maxSizeBytes = 10L * 1024 * 1024;
    }
}
