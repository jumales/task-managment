package com.demo.taskmanager.data.repo

import com.demo.taskmanager.data.api.TaskApi
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.common.safeApiCall
import com.demo.taskmanager.data.dto.AttachmentCreateRequest
import com.demo.taskmanager.data.dto.AttachmentDto
import com.demo.taskmanager.data.dto.BookedWorkCreateRequest
import com.demo.taskmanager.data.dto.BookedWorkDto
import com.demo.taskmanager.data.dto.CommentCreateRequest
import com.demo.taskmanager.data.dto.CommentDto
import com.demo.taskmanager.data.dto.NotificationTemplateDto
import com.demo.taskmanager.data.dto.NotificationTemplateRequest
import com.demo.taskmanager.data.dto.PageDto
import com.demo.taskmanager.data.dto.ParticipantDto
import com.demo.taskmanager.data.dto.PhaseCreateRequest
import com.demo.taskmanager.data.dto.PhaseDto
import com.demo.taskmanager.data.dto.PhaseUpdateRequest
import com.demo.taskmanager.data.dto.PlannedDatesRequest
import com.demo.taskmanager.data.dto.PlannedWorkDto
import com.demo.taskmanager.data.dto.ProjectCreateRequest
import com.demo.taskmanager.data.dto.ProjectDto
import com.demo.taskmanager.data.dto.TaskCreateRequest
import com.demo.taskmanager.data.dto.TaskDto
import com.demo.taskmanager.data.dto.TaskFullDto
import com.demo.taskmanager.data.dto.TaskPhaseUpdateRequest
import com.demo.taskmanager.data.dto.TaskSummaryDto
import com.demo.taskmanager.data.dto.TaskUpdateRequest
import com.demo.taskmanager.data.dto.TimelineCreateRequest
import com.demo.taskmanager.data.dto.TimelineDto
import com.demo.taskmanager.data.dto.WorkCreateRequest
import com.demo.taskmanager.data.dto.enums.TaskChangeType
import com.demo.taskmanager.data.dto.enums.TaskCompletionStatus
import com.demo.taskmanager.data.dto.enums.TaskStatus
import com.demo.taskmanager.data.dto.enums.TimelineState
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Delegates all task-service calls to [TaskApi]; caching and mapping added per feature. */
@Singleton
class TaskRepository @Inject constructor(
    private val api: TaskApi,
    private val json: Json,
) {

    suspend fun getProjects(page: Int = 0, size: Int = 50): NetworkResult<PageDto<ProjectDto>> =
        safeApiCall(json) { api.getProjects(page, size) }

    suspend fun getProject(id: String): NetworkResult<ProjectDto> =
        safeApiCall(json) { api.getProject(id) }

    suspend fun createProject(request: ProjectCreateRequest): NetworkResult<ProjectDto> =
        safeApiCall(json) { api.createProject(request) }

    suspend fun updateProject(id: String, request: ProjectCreateRequest): NetworkResult<ProjectDto> =
        safeApiCall(json) { api.updateProject(id, request) }

    suspend fun deleteProject(id: String): NetworkResult<Unit> =
        safeApiCall(json) { api.deleteProject(id) }

    suspend fun getPhases(projectId: String): NetworkResult<List<PhaseDto>> =
        safeApiCall(json) { api.getPhases(projectId) }

    suspend fun getPhase(id: String): NetworkResult<PhaseDto> =
        safeApiCall(json) { api.getPhase(id) }

    suspend fun createPhase(request: PhaseCreateRequest): NetworkResult<PhaseDto> =
        safeApiCall(json) { api.createPhase(request) }

    suspend fun updatePhase(id: String, request: PhaseUpdateRequest): NetworkResult<PhaseDto> =
        safeApiCall(json) { api.updatePhase(id, request) }

    suspend fun deletePhase(id: String): NetworkResult<Unit> =
        safeApiCall(json) { api.deletePhase(id) }

    suspend fun getTasks(
        page: Int = 0,
        size: Int = 20,
        projectId: String? = null,
        userId: String? = null,
        status: TaskStatus? = null,
        completionStatus: TaskCompletionStatus? = null,
    ): NetworkResult<PageDto<TaskSummaryDto>> =
        safeApiCall(json) { api.getTasks(page, size, projectId, userId, status, completionStatus) }

    suspend fun getTask(id: String): NetworkResult<TaskDto> =
        safeApiCall(json) { api.getTask(id) }

    suspend fun getTaskFull(id: String): NetworkResult<TaskFullDto> =
        safeApiCall(json) { api.getTaskFull(id) }

    suspend fun createTask(request: TaskCreateRequest): NetworkResult<TaskDto> =
        safeApiCall(json) { api.createTask(request) }

    suspend fun updateTask(id: String, request: TaskUpdateRequest): NetworkResult<TaskDto> =
        safeApiCall(json) { api.updateTask(id, request) }

    suspend fun changePhase(id: String, request: TaskPhaseUpdateRequest): NetworkResult<TaskDto> =
        safeApiCall(json) { api.changePhase(id, request) }

    suspend fun setPlannedDates(id: String, request: PlannedDatesRequest): NetworkResult<TaskDto> =
        safeApiCall(json) { api.setPlannedDates(id, request) }

    suspend fun deleteTask(id: String): NetworkResult<Unit> =
        safeApiCall(json) { api.deleteTask(id) }

    suspend fun getComments(taskId: String): NetworkResult<List<CommentDto>> =
        safeApiCall(json) { api.getComments(taskId) }

    suspend fun addComment(taskId: String, request: CommentCreateRequest): NetworkResult<CommentDto> =
        safeApiCall(json) { api.addComment(taskId, request) }

    suspend fun getParticipants(taskId: String): NetworkResult<List<ParticipantDto>> =
        safeApiCall(json) { api.getParticipants(taskId) }

    suspend fun watchTask(taskId: String): NetworkResult<ParticipantDto> =
        safeApiCall(json) { api.watchTask(taskId) }

    suspend fun joinTask(taskId: String): NetworkResult<ParticipantDto> =
        safeApiCall(json) { api.joinTask(taskId) }

    suspend fun removeParticipant(taskId: String, participantId: String): NetworkResult<Unit> =
        safeApiCall(json) { api.removeParticipant(taskId, participantId) }

    suspend fun getPlannedWork(taskId: String): NetworkResult<List<PlannedWorkDto>> =
        safeApiCall(json) { api.getPlannedWork(taskId) }

    suspend fun addPlannedWork(taskId: String, request: WorkCreateRequest): NetworkResult<PlannedWorkDto> =
        safeApiCall(json) { api.addPlannedWork(taskId, request) }

    suspend fun getBookedWork(taskId: String): NetworkResult<List<BookedWorkDto>> =
        safeApiCall(json) { api.getBookedWork(taskId) }

    suspend fun addBookedWork(taskId: String, request: BookedWorkCreateRequest): NetworkResult<BookedWorkDto> =
        safeApiCall(json) { api.addBookedWork(taskId, request) }

    suspend fun updateBookedWork(
        taskId: String,
        id: String,
        request: BookedWorkCreateRequest,
    ): NetworkResult<BookedWorkDto> =
        safeApiCall(json) { api.updateBookedWork(taskId, id, request) }

    suspend fun deleteBookedWork(taskId: String, id: String): NetworkResult<Unit> =
        safeApiCall(json) { api.deleteBookedWork(taskId, id) }

    suspend fun getTimelines(taskId: String): NetworkResult<List<TimelineDto>> =
        safeApiCall(json) { api.getTimelines(taskId) }

    suspend fun setTimeline(
        taskId: String,
        state: TimelineState,
        request: TimelineCreateRequest,
    ): NetworkResult<TimelineDto> =
        safeApiCall(json) { api.setTimeline(taskId, state, request) }

    suspend fun deleteTimeline(taskId: String, state: TimelineState): NetworkResult<Unit> =
        safeApiCall(json) { api.deleteTimeline(taskId, state) }

    suspend fun getAttachments(taskId: String): NetworkResult<List<AttachmentDto>> =
        safeApiCall(json) { api.getAttachments(taskId) }

    suspend fun addAttachment(taskId: String, request: AttachmentCreateRequest): NetworkResult<AttachmentDto> =
        safeApiCall(json) { api.addAttachment(taskId, request) }

    suspend fun deleteAttachment(taskId: String, attachmentId: String): NetworkResult<Unit> =
        safeApiCall(json) { api.deleteAttachment(taskId, attachmentId) }

    suspend fun getTemplatePlaceholders(projectId: String): NetworkResult<List<Map<String, String>>> =
        safeApiCall(json) { api.getTemplatePlaceholders(projectId) }

    suspend fun getNotificationTemplates(projectId: String): NetworkResult<List<NotificationTemplateDto>> =
        safeApiCall(json) { api.getNotificationTemplates(projectId) }

    suspend fun getNotificationTemplate(
        projectId: String,
        eventType: TaskChangeType,
    ): NetworkResult<NotificationTemplateDto> =
        safeApiCall(json) { api.getNotificationTemplate(projectId, eventType) }

    suspend fun upsertNotificationTemplate(
        projectId: String,
        eventType: TaskChangeType,
        request: NotificationTemplateRequest,
    ): NetworkResult<NotificationTemplateDto> =
        safeApiCall(json) { api.upsertNotificationTemplate(projectId, eventType, request) }

    suspend fun deleteNotificationTemplate(projectId: String, eventType: TaskChangeType): NetworkResult<Unit> =
        safeApiCall(json) { api.deleteNotificationTemplate(projectId, eventType) }
}
