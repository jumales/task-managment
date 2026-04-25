package com.demo.taskmanager.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceTokenRequest(
    val token: String,
    val platform: String = "ANDROID",
    val appVersion: String? = null,
)

@Serializable
data class DeviceTokenResponse(
    val id: String,
    val userId: String,
    val token: String,
    val platform: String,
    val appVersion: String? = null,
    val createdAt: String,
    val lastSeenAt: String,
)
