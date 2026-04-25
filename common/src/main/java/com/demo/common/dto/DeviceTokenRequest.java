package com.demo.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Request body for registering or rotating a device push token. */
@Data
public class DeviceTokenRequest {

    /** The FCM / APNs push token. */
    @NotBlank
    private String token;

    /** Mobile platform this token belongs to. */
    @NotNull
    private DevicePlatform platform;

    /** App version string, used for diagnostics (e.g. "1.4.2"). */
    private String appVersion;
}
