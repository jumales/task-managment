package com.demo.file;

import com.demo.common.dto.FileUploadResponse;
import com.demo.file.repository.FileMetadataRepository;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
class FileControllerIT {

    /**
     * Overrides production security — permits all requests and injects an ADMIN authentication
     * so that @PreAuthorize checks and the IDOR ownership check in FileController pass.
     */
    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        @Order(1)
        public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .securityMatcher("/**")
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .addFilterBefore(new OncePerRequestFilter() {
                        @Override
                        protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                                        jakarta.servlet.http.HttpServletResponse response,
                                                        jakarta.servlet.FilterChain chain)
                                throws java.io.IOException, jakarta.servlet.ServletException {
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken("test-admin", null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                            );
                            chain.doFilter(request, response);
                        }
                    }, UsernamePasswordAuthenticationFilter.class)
                    .build();
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> minio = new GenericContainer<>("minio/minio:RELEASE.2024-01-18T22-51-28Z")
            .withCommand("server /data")
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", "minio")
            .withEnv("MINIO_ROOT_PASSWORD", "minio123")
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    @DynamicPropertySource
    static void configureMinioProperties(DynamicPropertyRegistry registry) {
        String minioUrl = "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);
        registry.add("minio.url",        () -> minioUrl);
        registry.add("minio.access-key", () -> "minio");
        registry.add("minio.secret-key", () -> "minio123");
    }

    @BeforeAll
    static void createBuckets() throws Exception {
        String minioUrl = "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);
        MinioClient client = MinioClient.builder()
                .endpoint(minioUrl)
                .credentials("minio", "minio123")
                .build();
        for (String bucket : List.of("avatars", "attachments")) {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        }
    }

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    FileMetadataRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    // ── POST /api/v1/files/avatars ────────────────────────────────────────────

    @Test
    void uploadAvatar_returns201WithFileMetadata() {
        ResponseEntity<FileUploadResponse> response = uploadAvatar("test.jpg", "image/jpeg");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getFileId()).isNotNull();
        assertThat(response.getBody().getBucket()).isEqualTo("avatars");
        assertThat(response.getBody().getContentType()).isEqualTo("image/jpeg");
    }

    @Test
    void uploadAvatar_persistsMetadataToDatabase() {
        uploadAvatar("photo.png", "image/png");

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void uploadAvatar_originalFilenameIsPersisted() {
        uploadAvatar("my-photo.jpg", "image/jpeg");

        assertThat(repository.findAll().get(0).getOriginalFilename()).isEqualTo("my-photo.jpg");
    }

    // ── POST /api/v1/files/attachments ────────────────────────────────────────

    @Test
    void uploadAttachment_returns201WithAttachmentsBucket() {
        ResponseEntity<FileUploadResponse> response = uploadAttachment("report.pdf", "application/pdf");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getBucket()).isEqualTo("attachments");
    }

    // ── GET /api/v1/files/{fileId}/url ────────────────────────────────────────

    @Test
    void getPresignedUrl_afterUpload_returnsNonNullUrl() {
        FileUploadResponse uploaded = uploadAvatar("test.jpg", "image/jpeg").getBody();

        ResponseEntity<UrlResponse> response = restTemplate.getForEntity(
                "/api/v1/files/" + uploaded.getFileId() + "/url", UrlResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().url()).isNotBlank();
    }

    @Test
    void getPresignedUrl_whenFileNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/files/" + UUID.randomUUID() + "/url", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Validation ───────────────────────────────────────────────────────────

    @Test
    void uploadAvatar_withDisallowedContentType_returns400() {
        ResponseEntity<String> response = uploadFile("/api/v1/files/avatars", "file.txt", "text/plain", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("not allowed");
    }

    @Test
    void uploadAttachment_withDisallowedContentType_returns400() {
        ResponseEntity<String> response = uploadFile("/api/v1/files/attachments", "script.js", "application/javascript", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("not allowed");
    }

    // ── DELETE /api/v1/files/{fileId} ─────────────────────────────────────────

    @Test
    void deleteFile_softDeletesMetadataRecord() {
        FileUploadResponse uploaded = uploadAvatar("test.jpg", "image/jpeg").getBody();

        restTemplate.delete("/api/v1/files/" + uploaded.getFileId());

        // Soft-deleted records are hidden by @SQLRestriction — count drops to 0
        assertThat(repository.count()).isEqualTo(0);
    }

    @Test
    void deleteFile_thenGetPresignedUrl_returns404() {
        FileUploadResponse uploaded = uploadAvatar("test.jpg", "image/jpeg").getBody();

        restTemplate.delete("/api/v1/files/" + uploaded.getFileId());

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/files/" + uploaded.getFileId() + "/url", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteFile_whenNotFound_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/files/" + UUID.randomUUID(),
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<FileUploadResponse> uploadAvatar(String filename, String contentType) {
        return uploadFile("/api/v1/files/avatars", filename, contentType, FileUploadResponse.class);
    }

    private ResponseEntity<FileUploadResponse> uploadAttachment(String filename, String contentType) {
        return uploadFile("/api/v1/files/attachments", filename, contentType, FileUploadResponse.class);
    }

    private <T> ResponseEntity<T> uploadFile(String url, String filename, String contentType, Class<T> responseType) {
        return uploadFile(url, filename, contentType, new byte[]{1, 2, 3}, responseType);
    }

    private <T> ResponseEntity<T> uploadFile(String url, String filename, String contentType, byte[] content, Class<T> responseType) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedByteArrayResource(content, filename, contentType));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        return restTemplate.postForEntity(url, new HttpEntity<>(body, headers), responseType);
    }

    /**
     * ByteArrayResource with a fixed filename and content type — required for multipart uploads
     * where the content type of the part must be controlled explicitly.
     */
    private static class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;
        private final org.springframework.http.MediaType mediaType;

        NamedByteArrayResource(byte[] content, String filename, String contentType) {
            super(content);
            this.filename = filename;
            this.mediaType = org.springframework.http.MediaType.parseMediaType(contentType);
        }

        @Override
        public String getFilename() { return filename; }

        /** Used by Spring's multipart encoder to set the Content-Type of this part. */
        public org.springframework.http.MediaType getMediaType() { return mediaType; }
    }

    /** Maps the {@code PresignedUrlResponse} record from FileController. */
    private record UrlResponse(String url) {}
}
