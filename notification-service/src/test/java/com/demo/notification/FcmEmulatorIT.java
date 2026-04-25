package com.demo.notification;

import com.demo.common.event.TaskChangeType;
import com.demo.notification.model.DeviceToken;
import com.demo.notification.repository.DeviceTokenRepository;
import com.demo.notification.service.DeviceTokenService;
import com.demo.notification.service.FcmPushService;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.demo.common.dto.DevicePlatform.ANDROID;
import static com.demo.common.event.TaskChangeType.COMMENT_ADDED;
import static com.demo.common.event.TaskChangeType.STATUS_CHANGED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration test that verifies {@link FcmPushService} sends correctly-shaped requests
 * to the FCM v1 API and handles error responses (UNREGISTERED token → soft-delete).
 *
 * WireMock runs in Docker and acts as the FCM emulator endpoint.
 * The Firebase Admin SDK is redirected to it via the {@code FIREBASE_MESSAGING_EMULATOR_HOST}
 * environment variable, which the Maven Surefire plugin sets to {@code localhost:28085}
 * so no reflection tricks are needed to inject it at runtime.
 *
 * Fixed port 28085 is reserved exclusively for this test; it is mapped to WireMock's
 * internal port 8080 via {@code withFixedExposedPort}.
 */
@Testcontainers
@ExtendWith(MockitoExtension.class)
class FcmEmulatorIT {

    /** WireMock container wired to fixed host port 28085 (matches surefire env var). */
    @Container
    static final GenericContainer<?> wireMock =
            new GenericContainer<>("wiremock/wiremock:3.5.4")
                    .withExposedPorts(8080)
                    .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
                            new PortBinding(Ports.Binding.bindPort(28085), new ExposedPort(8080))))
                    .waitingFor(Wait.forHttp("/__admin/health").forPort(8080));

    private static final String ADMIN_BASE = "http://localhost:28085";
    private static final String FCM_URL_PATTERN = "/v1/projects/.*/messages:send";
    private static final RestTemplate http = new RestTemplate();

    @Mock DeviceTokenRepository tokenRepository;
    @Mock DeviceTokenService     tokenService;

    private FcmPushService fcmPushService;

    @BeforeAll
    static void initFirebase() {
        if (FirebaseApp.getApps().isEmpty()) {
            // Emulator mode bypasses OAuth — any non-null token is accepted.
            GoogleCredentials credentials = GoogleCredentials.create(
                    new AccessToken("stub-token", Date.from(Instant.now().plusSeconds(3600))));
            FirebaseApp.initializeApp(FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId("demo-project")
                    .build());
        }
    }

    @BeforeEach
    void setUp() {
        // Clear WireMock stubs and recorded requests between tests.
        http.postForEntity(ADMIN_BASE + "/__admin/reset", null, String.class);

        fcmPushService = new FcmPushService(tokenRepository, tokenService);
        ReflectionTestUtils.setField(fcmPushService, "enabled", true);
        ReflectionTestUtils.setField(fcmPushService, "dryRun", false);
        ReflectionTestUtils.setField(fcmPushService, "credentialsJson", "");
        // Firebase already initialized in @BeforeAll — skip @PostConstruct init().
    }

    @Test
    void notifyUsers_sendsOneRequestPerToken() {
        stubSuccess();
        UUID uid = UUID.randomUUID();
        when(tokenRepository.findByUserIdAndDeletedAtIsNull(uid))
                .thenReturn(List.of(buildToken(uid, "token-a"), buildToken(uid, "token-b")));

        fcmPushService.notifyUsers(Set.of(uid), STATUS_CHANGED, UUID.randomUUID(), UUID.randomUUID());

        // sendEachForMulticast sends one POST per token via the FCM v1 API.
        assertThat(recordedSends()).hasSize(2);
    }

    @Test
    void notifyUsers_payloadContainsChangeTypeAndTaskId() {
        stubSuccess();
        UUID uid       = UUID.randomUUID();
        UUID taskId    = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(tokenRepository.findByUserIdAndDeletedAtIsNull(uid))
                .thenReturn(List.of(buildToken(uid, "payload-token")));

        fcmPushService.notifyUsers(Set.of(uid), COMMENT_ADDED, taskId, projectId);

        String body = bodyOf(recordedSends().get(0));
        assertThat(body).contains("COMMENT_ADDED");
        assertThat(body).contains(taskId.toString());
        assertThat(body).contains(projectId.toString());
    }

    @Test
    void notifyUsers_unregisteredResponse_softDeletesToken() {
        stubUnregistered();
        UUID uid = UUID.randomUUID();
        when(tokenRepository.findByUserIdAndDeletedAtIsNull(uid))
                .thenReturn(List.of(buildToken(uid, "stale-token")));

        fcmPushService.notifyUsers(Set.of(uid), STATUS_CHANGED, UUID.randomUUID(), UUID.randomUUID());

        verify(tokenService).softDeleteByToken("stale-token");
    }

    @Test
    void notifyUsers_noTokensForUser_makesNoHttpCall() {
        UUID uid = UUID.randomUUID();
        when(tokenRepository.findByUserIdAndDeletedAtIsNull(uid)).thenReturn(List.of());

        fcmPushService.notifyUsers(Set.of(uid), STATUS_CHANGED, UUID.randomUUID(), UUID.randomUUID());

        assertThat(recordedSends()).isEmpty();
    }

    // ── WireMock helpers ──────────────────────────────────────────────────────

    private void stubSuccess() {
        stub(200, """
                {"name":"projects/demo-project/messages/msg-1"}""");
    }

    private void stubUnregistered() {
        stub(404, """
                {"error":{"code":404,"message":"Requested entity was not found.","status":"NOT_FOUND",\
                "details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError",\
                "errorCode":"UNREGISTERED"}]}}""");
    }

    private void stub(int status, String body) {
        http.postForEntity(ADMIN_BASE + "/__admin/mappings",
                Map.of(
                        "request",  Map.of("method", "POST", "urlPattern", FCM_URL_PATTERN),
                        "response", Map.of(
                                "status",  status,
                                "headers", Map.of("Content-Type", "application/json"),
                                "body",    body)
                ), String.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> recordedSends() {
        ResponseEntity<Map> resp = http.postForEntity(
                ADMIN_BASE + "/__admin/requests/find",
                Map.of("method", "POST", "urlPattern", FCM_URL_PATTERN),
                Map.class);
        return (List<Map<String, Object>>) resp.getBody().get("requests");
    }

    private String bodyOf(Map<String, Object> request) {
        return (String) request.get("body");
    }

    private DeviceToken buildToken(UUID userId, String tokenValue) {
        return DeviceToken.builder()
                .id(UUID.randomUUID()).userId(userId).token(tokenValue)
                .platform(ANDROID).createdAt(Instant.now()).lastSeenAt(Instant.now())
                .build();
    }
}
