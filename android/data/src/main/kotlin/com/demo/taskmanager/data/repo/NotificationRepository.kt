package com.demo.taskmanager.data.repo

import com.demo.taskmanager.data.api.NotificationApi
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.common.safeApiCall
import com.demo.taskmanager.data.dto.NotificationTemplateDto
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Delegates all notification-service calls to [NotificationApi]; FCM device-token endpoints added in task_17. */
@Singleton
class NotificationRepository @Inject constructor(
    private val api: NotificationApi,
    private val json: Json,
) {

    suspend fun getTaskNotifications(taskId: String): NetworkResult<List<NotificationTemplateDto>> =
        safeApiCall(json) { api.getTaskNotifications(taskId) }
}
