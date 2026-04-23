package com.demo.taskmanager.data.api

import com.demo.taskmanager.data.dto.AttachmentDto
import com.demo.taskmanager.data.dto.AttachmentCreateRequest
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
import com.demo.taskmanager.data.dto.TaskPhaseUpdateRequest
import com.demo.taskmanager.data.dto.ProjectCreateRequest
import com.demo.taskmanager.data.dto.ProjectDto
import com.demo.taskmanager.data.dto.TaskCreateRequest
import com.demo.taskmanager.data.dto.TaskDto
import com.demo.taskmanager.data.dto.TaskFullDto
import com.demo.taskmanager.data.dto.TaskSummaryDto
import com.demo.taskmanager.data.dto.TaskUpdateRequest
import com.demo.taskmanager.data.dto.TimelineDto
import com.demo.taskmanager.data.dto.TimelineCreateRequest
import com.demo.taskmanager.data.dto.WorkCreateRequest
import com.demo.taskmanager.data.dto.enums.TaskChangeType
import com.demo.taskmanager.data.dto.enums.TaskCompletionStatus
import com.demo.taskmanager.data.dto.enums.TaskStatus
import com.demo.taskmanager.data.dto.enums.TimelineState
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit interface for task-service: projects, phases, tasks, and all task sub-resources. */
interface TaskApi {

    // ── Projects ──────────────────────────────────────────────────────────────

    @GET("api/v1/projects")
    suspend fun getProjects(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50,
        @Query("sort") sort: String = "name",
    ): PageDto<ProjectDto>

    @GET("api/v1/projects/{id}")
    suspend fun getProject(@Path("id") id: String): ProjectDto

    @POST("api/v1/projects")
    suspend fun createProject(@Body request: ProjectCreateRequest): ProjectDto

    @PUT("api/v1/projects/{id}")
    suspend fun updateProject(
        @Path("id") id: String,
        @Body request: ProjectCreateRequest,
    ): ProjectDto

    @DELETE("api/v1/projects/{id}")
    suspend fun deleteProject(@Path("id") id: String)

    // ── Phases ────────────────────────────────────────────────────────────────

    @GET("api/v1/phases")
    suspend fun getPhases(@Query("projectId") projectId: String): List<PhaseDto>

    @GET("api/v1/phases/{id}")
    suspend fun getPhase(@Path("id") id: String): PhaseDto

    @POST("api/v1/phases")
    suspend fun createPhase(@Body request: PhaseCreateRequest): PhaseDto

    @PUT("api/v1/phases/{id}")
    suspend fun updatePhase(
        @Path("id") id: String,
        @Body request: PhaseUpdateRequest,
    ): PhaseDto

    @DELETE("api/v1/phases/{id}")
    suspend fun deletePhase(@Path("id") id: String)

    // ── Tasks ─────────────────────────────────────────────────────────────────

    @GET("api/v1/tasks")
    suspend fun getTasks(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("projectId") projectId: String? = null,
        @Query("userId") userId: String? = null,
        @Query("status") status: TaskStatus? = null,
        @Query("completionStatus") completionStatus: TaskCompletionStatus? = null,
    ): PageDto<TaskSummaryDto>

    @GET("api/v1/tasks/{id}")
    suspend fun getTask(@Path("id") id: String): TaskDto

    @GET("api/v1/tasks/{id}/full")
    suspend fun getTaskFull(@Path("id") id: String): TaskFullDto

    @POST("api/v1/tasks")
    suspend fun createTask(@Body request: TaskCreateRequest): TaskDto

    @PUT("api/v1/tasks/{id}")
    suspend fun updateTask(
        @Path("id") id: String,
        @Body request: TaskUpdateRequest,
    ): TaskDto

    @PATCH("api/v1/tasks/{id}/phase")
    suspend fun changePhase(
        @Path("id") id: String,
        @Body request: TaskPhaseUpdateRequest,
    ): TaskDto

    @PUT("api/v1/tasks/{id}/planned-dates")
    suspend fun setPlannedDates(
        @Path("id") id: String,
        @Body request: PlannedDatesRequest,
    ): TaskDto

    @DELETE("api/v1/tasks/{id}")
    suspend fun deleteTask(@Path("id") id: String)

    // ── Comments ──────────────────────────────────────────────────────────────

    @GET("api/v1/tasks/{taskId}/comments")
    suspend fun getComments(@Path("taskId") taskId: String): List<CommentDto>

    @POST("api/v1/tasks/{taskId}/comments")
    suspend fun addComment(
        @Path("taskId") taskId: String,
        @Body request: CommentCreateRequest,
    ): CommentDto

    // ── Participants ──────────────────────────────────────────────────────────

    @GET("api/v1/tasks/{taskId}/participants")
    suspend fun getParticipants(@Path("taskId") taskId: String): List<ParticipantDto>

    @POST("api/v1/tasks/{taskId}/participants/watch")
    suspend fun watchTask(@Path("taskId") taskId: String): ParticipantDto

    @POST("api/v1/tasks/{taskId}/participants/join")
    suspend fun joinTask(@Path("taskId") taskId: String): ParticipantDto

    @DELETE("api/v1/tasks/{taskId}/participants/{participantId}")
    suspend fun removeParticipant(
        @Path("taskId") taskId: String,
        @Path("participantId") participantId: String,
    )

    // ── Planned work ──────────────────────────────────────────────────────────

    @GET("api/v1/tasks/{taskId}/planned-work")
    suspend fun getPlannedWork(@Path("taskId") taskId: String): List<PlannedWorkDto>

    @POST("api/v1/tasks/{taskId}/planned-work")
    suspend fun addPlannedWork(
        @Path("taskId") taskId: String,
        @Body request: WorkCreateRequest,
    ): PlannedWorkDto

    // ── Booked work ───────────────────────────────────────────────────────────

    @GET("api/v1/tasks/{taskId}/booked-work")
    suspend fun getBookedWork(@Path("taskId") taskId: String): List<BookedWorkDto>

    @POST("api/v1/tasks/{taskId}/booked-work")
    suspend fun addBookedWork(
        @Path("taskId") taskId: String,
        @Body request: BookedWorkCreateRequest,
    ): BookedWorkDto

    @PUT("api/v1/tasks/{taskId}/booked-work/{id}")
    suspend fun updateBookedWork(
        @Path("taskId") taskId: String,
        @Path("id") id: String,
        @Body request: BookedWorkCreateRequest,
    ): BookedWorkDto

    @DELETE("api/v1/tasks/{taskId}/booked-work/{id}")
    suspend fun deleteBookedWork(
        @Path("taskId") taskId: String,
        @Path("id") id: String,
    )

    // ── Timelines ─────────────────────────────────────────────────────────────

    @GET("api/v1/tasks/{taskId}/timelines")
    suspend fun getTimelines(@Path("taskId") taskId: String): List<TimelineDto>

    @PUT("api/v1/tasks/{taskId}/timelines/{state}")
    suspend fun setTimeline(
        @Path("taskId") taskId: String,
        @Path("state") state: TimelineState,
        @Body request: TimelineCreateRequest,
    ): TimelineDto

    @DELETE("api/v1/tasks/{taskId}/timelines/{state}")
    suspend fun deleteTimeline(
        @Path("taskId") taskId: String,
        @Path("state") state: TimelineState,
    )

    // ── Attachments ───────────────────────────────────────────────────────────

    @GET("api/v1/tasks/{taskId}/attachments")
    suspend fun getAttachments(@Path("taskId") taskId: String): List<AttachmentDto>

    @POST("api/v1/tasks/{taskId}/attachments")
    suspend fun addAttachment(
        @Path("taskId") taskId: String,
        @Body request: AttachmentCreateRequest,
    ): AttachmentDto

    @DELETE("api/v1/tasks/{taskId}/attachments/{attachmentId}")
    suspend fun deleteAttachment(
        @Path("taskId") taskId: String,
        @Path("attachmentId") attachmentId: String,
    )

    // ── Notification templates ─────────────────────────────────────────────────

    @GET("api/v1/projects/{projectId}/notification-templates/placeholders")
    suspend fun getTemplatePlaceholders(@Path("projectId") projectId: String): List<Map<String, String>>

    @GET("api/v1/projects/{projectId}/notification-templates")
    suspend fun getNotificationTemplates(@Path("projectId") projectId: String): List<NotificationTemplateDto>

    @GET("api/v1/projects/{projectId}/notification-templates/{eventType}")
    suspend fun getNotificationTemplate(
        @Path("projectId") projectId: String,
        @Path("eventType") eventType: TaskChangeType,
    ): NotificationTemplateDto

    @PUT("api/v1/projects/{projectId}/notification-templates/{eventType}")
    suspend fun upsertNotificationTemplate(
        @Path("projectId") projectId: String,
        @Path("eventType") eventType: TaskChangeType,
        @Body request: NotificationTemplateRequest,
    ): NotificationTemplateDto

    @DELETE("api/v1/projects/{projectId}/notification-templates/{eventType}")
    suspend fun deleteNotificationTemplate(
        @Path("projectId") projectId: String,
        @Path("eventType") eventType: TaskChangeType,
    )
}
