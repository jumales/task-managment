package com.demo.notification;

import com.demo.common.event.TaskChangeType;
import com.demo.notification.model.DeviceToken;
import com.demo.notification.repository.DeviceTokenRepository;
import com.demo.notification.service.DeviceTokenService;
import com.demo.notification.service.FcmPushService;
import com.google.firebase.messaging.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.demo.common.dto.DevicePlatform.ANDROID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FcmPushServiceTest {

    @Mock
    DeviceTokenRepository deviceTokenRepository;

    @Mock
    DeviceTokenService deviceTokenService;

    FcmPushService fcmPushService;

    private final UUID userId = UUID.randomUUID();
    private final UUID taskId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        fcmPushService = new FcmPushService(deviceTokenRepository, deviceTokenService);
        // Enable FCM but use dry-run so no actual Firebase call is made for the basic path tests.
        ReflectionTestUtils.setField(fcmPushService, "enabled", true);
        ReflectionTestUtils.setField(fcmPushService, "dryRun", true);
        ReflectionTestUtils.setField(fcmPushService, "credentialsJson", "");
    }

    @Test
    void notifyUsers_dryRun_doesNotCallFirebase() {
        DeviceToken token = buildToken(userId, "token-abc");
        when(deviceTokenRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(List.of(token));

        fcmPushService.notifyUsers(Set.of(userId), TaskChangeType.STATUS_CHANGED, taskId, projectId);

        verifyNoInteractions(deviceTokenService);
    }

    @Test
    void notifyUsers_noTokensForUser_skipsWithoutError() {
        when(deviceTokenRepository.findByUserIdAndDeletedAtIsNull(any())).thenReturn(List.of());

        fcmPushService.notifyUsers(Set.of(userId), TaskChangeType.TASK_CREATED, taskId, projectId);

        verifyNoInteractions(deviceTokenService);
    }

    @Test
    void notifyUsers_disabled_skipsCompletely() {
        ReflectionTestUtils.setField(fcmPushService, "enabled", false);

        fcmPushService.notifyUsers(Set.of(userId), TaskChangeType.STATUS_CHANGED, taskId, projectId);

        verifyNoInteractions(deviceTokenRepository);
        verifyNoInteractions(deviceTokenService);
    }

    @Test
    void notifyUsers_emptyUserSet_skips() {
        fcmPushService.notifyUsers(Set.of(), TaskChangeType.STATUS_CHANGED, taskId, projectId);
        verifyNoInteractions(deviceTokenRepository);
    }

    @Test
    void handleBatchResponse_unregisteredToken_softDeletes() throws FirebaseMessagingException {
        // Switch off dry-run for this test to exercise handleBatchResponse.
        ReflectionTestUtils.setField(fcmPushService, "dryRun", false);

        String badToken = "bad-token";
        DeviceToken token = buildToken(userId, badToken);
        when(deviceTokenRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(List.of(token));

        // Build a batch response where the single token is UNREGISTERED.
        SendResponse failedResponse = mock(SendResponse.class);
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(failedResponse.isSuccessful()).thenReturn(false);
        when(failedResponse.getException()).thenReturn(exception);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);

        BatchResponse batchResponse = mock(BatchResponse.class);
        when(batchResponse.getResponses()).thenReturn(List.of(failedResponse));
        when(batchResponse.getSuccessCount()).thenReturn(0);

        try (MockedStatic<FirebaseMessaging> firebaseMessagingMock = mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging mockMessaging = mock(FirebaseMessaging.class);
            firebaseMessagingMock.when(FirebaseMessaging::getInstance).thenReturn(mockMessaging);
            when(mockMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(batchResponse);

            fcmPushService.notifyUsers(Set.of(userId), TaskChangeType.STATUS_CHANGED, taskId, projectId);
        }

        verify(deviceTokenService).softDeleteByToken(badToken);
    }

    private DeviceToken buildToken(UUID owner, String tokenValue) {
        return DeviceToken.builder()
                .id(UUID.randomUUID())
                .userId(owner)
                .token(tokenValue)
                .platform(ANDROID)
                .createdAt(Instant.now())
                .lastSeenAt(Instant.now())
                .build();
    }
}
