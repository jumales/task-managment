package com.demo.taskmanager.data.api

import com.demo.taskmanager.data.dto.NotificationTemplateDto
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Retrofit interface for notification-service.
 * Device-token registration endpoints are added in task_17 together with FCM integration.
 */
interface NotificationApi {

    /** Returns notification templates configured for a given task's project. */
    @GET("api/v1/notifications/tasks/{taskId}")
    suspend fun getTaskNotifications(@Path("taskId") taskId: String): List<NotificationTemplateDto>
}
