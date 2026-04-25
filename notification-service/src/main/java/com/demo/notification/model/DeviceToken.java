package com.demo.notification.model;

import com.demo.common.dto.DevicePlatform;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** Stores a user's registered mobile push token for FCM/APNs delivery. Soft-deleted when invalid or removed. */
@Entity
@Table(name = "device_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceToken {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DevicePlatform platform;

    @Column(length = 64)
    private String appVersion;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    /** Null while active; set to soft-delete the token. */
    private Instant deletedAt;
}
