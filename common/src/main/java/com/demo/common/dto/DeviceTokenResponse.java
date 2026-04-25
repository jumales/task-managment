package com.demo.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/** Response DTO for a registered device token. */
@Getter
@AllArgsConstructor
public class DeviceTokenResponse {
    private UUID id;
    private UUID userId;
    private String token;
    private DevicePlatform platform;
    private String appVersion;
    private Instant createdAt;
    private Instant lastSeenAt;
}
