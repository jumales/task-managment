package com.demo.file;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates file-size enforcement. Runs with a 10-byte avatar limit so that a
 * small test payload can reliably exceed the configured maximum without allocating
 * large arrays.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "minio.buckets.avatars.max-size-bytes=10",
                "minio.buckets.avatars.allowed-types=image/jpeg,image/png,image/gif,image/webp"
        }
)
class FileSizeValidationIT {

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

    @Autowired TestRestTemplate restTemplate;
    @Autowired FileMetadataRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void uploadAvatar_withinSizeLimit_returns201() {
        // 10 bytes — exactly at the limit
        ResponseEntity<String> response = uploadAvatar(new byte[10]);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void uploadAvatar_exceedingSizeLimit_returns400() {
        // 11 bytes — one byte over the 10-byte test limit
        ResponseEntity<String> response = uploadAvatar(new byte[11]);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("exceeds the maximum");
    }

    @Test
    void uploadAvatar_exceedingSizeLimit_doesNotPersistMetadata() {
        uploadAvatar(new byte[11]);

        assertThat(repository.count()).isEqualTo(0);
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private ResponseEntity<String> uploadAvatar(byte[] content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedByteArrayResource(content, "photo.jpg"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        return restTemplate.postForEntity(
                "/api/v1/files/avatars", new HttpEntity<>(body, headers), String.class);
    }

    private static class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(byte[] content, String filename) {
            super(content);
            this.filename = filename;
        }

        @Override
        public String getFilename() { return filename; }
    }
}
