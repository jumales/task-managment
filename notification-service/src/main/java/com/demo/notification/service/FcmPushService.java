package com.demo.notification.service;

import com.demo.common.event.TaskChangeType;
import com.demo.notification.repository.DeviceTokenRepository;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.*;

/**
 * Delivers data-only FCM push notifications to registered mobile devices.
 * Disabled at startup when {@code fcm.enabled=false}; the service still boots without credentials in that case.
 * Invalid tokens ({@code UNREGISTERED} / {@code INVALID_ARGUMENT}) are soft-deleted on first failure.
 */
@Service
public class FcmPushService {

    private static final Logger log = LoggerFactory.getLogger(FcmPushService.class);

    /** FCM multicast batch limit. */
    private static final int FCM_BATCH_SIZE = 500;

    private final DeviceTokenRepository deviceTokenRepository;
    private final DeviceTokenService deviceTokenService;

    @Value("${fcm.enabled:false}")
    private boolean enabled;

    @Value("${fcm.credentials-json:}")
    private String credentialsJson;

    @Value("${fcm.dry-run:false}")
    private boolean dryRun;

    public FcmPushService(DeviceTokenRepository deviceTokenRepository,
                          DeviceTokenService deviceTokenService) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.deviceTokenService = deviceTokenService;
    }

    /**
     * Initializes the Firebase Admin SDK from the JSON credential string.
     * Skipped when {@code fcm.enabled=false} so the service starts without credentials.
     */
    @PostConstruct
    void init() {
        if (!enabled) {
            log.info("FCM disabled — skipping Firebase Admin SDK initialization");
            return;
        }
        if (credentialsJson == null || credentialsJson.isBlank()) {
            log.warn("fcm.enabled=true but fcm.credentials-json is empty — FCM will not work");
            return;
        }
        try {
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(credentialsJson.getBytes()))
                    .createScoped("https://www.googleapis.com/auth/firebase.messaging");
            FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
            log.info("Firebase Admin SDK initialized (dryRun={})", dryRun);
        } catch (Exception e) {
            log.error("Failed to initialize Firebase Admin SDK: {}", e.getMessage(), e);
        }
    }

    /**
     * Sends a data-only push to all active devices owned by the given users.
     * Runs asynchronously so the Kafka consumer thread is not blocked.
     * No-op when {@code fcm.enabled=false}.
     *
     * @param userIds    IDs of users to notify
     * @param changeType event type, included in the push payload for client routing
     * @param taskId     task the event is about
     * @param projectId  project the task belongs to (may be null)
     */
    @Async
    public void notifyUsers(Set<UUID> userIds, TaskChangeType changeType, UUID taskId, UUID projectId) {
        if (!enabled || userIds.isEmpty()) return;

        List<String> tokens = userIds.stream()
                .flatMap(uid -> deviceTokenRepository.findByUserIdAndDeletedAtIsNull(uid).stream())
                .map(t -> t.getToken())
                .distinct()
                .toList();

        if (tokens.isEmpty()) return;

        Map<String, String> data = buildPayload(changeType, taskId, projectId);
        log.debug("FCM notify: {} tokens, changeType={}, taskId={}", tokens.size(), changeType, taskId);

        // Batch into chunks of FCM_BATCH_SIZE (FCM multicast limit).
        for (int i = 0; i < tokens.size(); i += FCM_BATCH_SIZE) {
            List<String> batch = tokens.subList(i, Math.min(i + FCM_BATCH_SIZE, tokens.size()));
            sendBatch(batch, data);
        }
    }

    private void sendBatch(List<String> tokens, Map<String, String> data) {
        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .putAllData(data)
                .build();

        if (dryRun) {
            log.info("FCM dry-run: would send to {} tokens with data={}", tokens.size(), data);
            return;
        }

        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            handleBatchResponse(response, tokens);
        } catch (FirebaseMessagingException e) {
            log.error("FCM multicast failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Inspects each per-token result and soft-deletes tokens that FCM reports as invalid,
     * preventing future wasted sends.
     */
    private void handleBatchResponse(BatchResponse response, List<String> tokens) {
        List<SendResponse> results = response.getResponses();
        for (int i = 0; i < results.size(); i++) {
            SendResponse result = results.get(i);
            if (result.isSuccessful()) continue;
            MessagingErrorCode code = result.getException().getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                String badToken = tokens.get(i);
                log.info("FCM token invalid ({}), soft-deleting: {}", code, badToken);
                deviceTokenService.softDeleteByToken(badToken);
            } else {
                log.warn("FCM send failed for token index {}: {}", i, result.getException().getMessage());
            }
        }
        log.debug("FCM batch: {}/{} successful", response.getSuccessCount(), tokens.size());
    }

    private Map<String, String> buildPayload(TaskChangeType changeType, UUID taskId, UUID projectId) {
        Map<String, String> data = new HashMap<>();
        data.put("changeType", changeType.name());
        data.put("taskId", taskId != null ? taskId.toString() : "");
        data.put("projectId", projectId != null ? projectId.toString() : "");
        return data;
    }
}
