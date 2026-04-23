package com.demo.taskmanager.data.dto

import com.demo.taskmanager.data.dto.enums.TaskChangeType
import com.demo.taskmanager.data.dto.enums.TaskCompletionStatus
import com.demo.taskmanager.data.dto.enums.TaskParticipantRole
import com.demo.taskmanager.data.dto.enums.TaskPhaseName
import com.demo.taskmanager.data.dto.enums.TaskStatus
import com.demo.taskmanager.data.dto.enums.TaskType
import com.demo.taskmanager.data.dto.enums.TimelineState
import com.demo.taskmanager.data.dto.enums.WorkType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Round-trip serialization smoke tests: encode DTO → JSON → decode and assert field equality. */
class DtoSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // ── Enum round-trips ───────────────────────────────────────────────────────

    @Test
    fun `TaskStatus round-trips all values`() {
        TaskStatus.entries.forEach { status ->
            assertEquals(status, json.decodeFromString<TaskStatus>(json.encodeToString(status)))
        }
    }

    @Test
    fun `TaskType round-trips all values`() {
        TaskType.entries.forEach { type ->
            assertEquals(type, json.decodeFromString<TaskType>(json.encodeToString(type)))
        }
    }

    @Test
    fun `WorkType round-trips all values`() {
        WorkType.entries.forEach { wt ->
            assertEquals(wt, json.decodeFromString<WorkType>(json.encodeToString(wt)))
        }
    }

    @Test
    fun `TaskPhaseName round-trips all values`() {
        TaskPhaseName.entries.forEach { phase ->
            assertEquals(phase, json.decodeFromString<TaskPhaseName>(json.encodeToString(phase)))
        }
    }

    @Test
    fun `TaskChangeType round-trips all values`() {
        TaskChangeType.entries.forEach { ct ->
            assertEquals(ct, json.decodeFromString<TaskChangeType>(json.encodeToString(ct)))
        }
    }

    @Test
    fun `TaskCompletionStatus round-trips all values`() {
        TaskCompletionStatus.entries.forEach { cs ->
            assertEquals(cs, json.decodeFromString<TaskCompletionStatus>(json.encodeToString(cs)))
        }
    }

    @Test
    fun `TaskParticipantRole round-trips all values`() {
        TaskParticipantRole.entries.forEach { role ->
            assertEquals(role, json.decodeFromString<TaskParticipantRole>(json.encodeToString(role)))
        }
    }

    @Test
    fun `TimelineState round-trips all values`() {
        TimelineState.entries.forEach { state ->
            assertEquals(state, json.decodeFromString<TimelineState>(json.encodeToString(state)))
        }
    }

    // ── DTO round-trips ────────────────────────────────────────────────────────

    @Test
    fun `TaskSummaryDto round-trips`() {
        val dto = TaskSummaryDto(
            id = "550e8400-e29b-41d4-a716-446655440000",
            taskCode = "PROJ_1",
            title = "Fix login bug",
            description = "Reproduces on Android 14",
            status = TaskStatus.IN_PROGRESS,
            type = TaskType.BUG_FIXING,
            progress = 40,
            assignedUserId = "user-uuid",
            assignedUserName = "Alice",
            projectId = "project-uuid",
            projectName = "Backend",
            phaseId = "phase-uuid",
            phaseName = "IN_PROGRESS",
        )
        val decoded = json.decodeFromString<TaskSummaryDto>(json.encodeToString(dto))
        assertEquals(dto, decoded)
    }

    @Test
    fun `ProjectDto round-trips`() {
        val dto = ProjectDto(
            id = "proj-uuid",
            name = "Task Manager",
            description = "Main project",
            taskCodePrefix = "TM_",
            defaultPhaseId = "phase-uuid",
        )
        val decoded = json.decodeFromString<ProjectDto>(json.encodeToString(dto))
        assertEquals(dto, decoded)
    }

    @Test
    fun `CommentDto round-trips`() {
        val dto = CommentDto(
            id = "comment-uuid",
            userId = "user-uuid",
            userName = "Bob",
            content = "Looks good to me.",
            createdAt = "2025-01-15T10:30:00Z",
        )
        val decoded = json.decodeFromString<CommentDto>(json.encodeToString(dto))
        assertEquals(dto, decoded)
    }

    @Test
    fun `CommentDto with null userId round-trips`() {
        val dto = CommentDto(
            id = "comment-uuid",
            userId = null,
            userName = null,
            content = "Legacy comment",
            createdAt = "2025-01-15T10:30:00Z",
        )
        val decoded = json.decodeFromString<CommentDto>(json.encodeToString(dto))
        assertEquals(dto, decoded)
    }

    @Test
    fun `PlannedWorkDto round-trips`() {
        val dto = PlannedWorkDto(
            id = "work-uuid",
            userId = "user-uuid",
            userName = "Alice",
            workType = WorkType.DEVELOPMENT,
            plannedHours = 8,
            createdAt = "2025-01-15T10:30:00Z",
        )
        val decoded = json.decodeFromString<PlannedWorkDto>(json.encodeToString(dto))
        assertEquals(dto, decoded)
    }

    @Test
    fun `BookedWorkDto round-trips`() {
        val dto = BookedWorkDto(
            id = "booked-uuid",
            userId = "user-uuid",
            userName = null,
            workType = WorkType.CODE_REVIEW,
            bookedHours = 2,
            createdAt = "2025-01-15T12:00:00Z",
        )
        val decoded = json.decodeFromString<BookedWorkDto>(json.encodeToString(dto))
        assertEquals(dto, decoded)
    }

    @Test
    fun `UserDto round-trips`() {
        val dto = UserDto(
            id = "user-uuid",
            name = "Alice Smith",
            email = "alice@example.com",
            username = "alice",
            active = true,
            avatarFileId = "avatar-uuid",
            language = "en",
            roles = listOf("ADMIN", "USER"),
        )
        val decoded = json.decodeFromString<UserDto>(json.encodeToString(dto))
        assertEquals(dto, decoded)
    }

    @Test
    fun `AttachmentDto round-trips`() {
        val dto = AttachmentDto(
            id = "att-uuid",
            fileId = "file-uuid",
            fileName = "design.png",
            contentType = "image/png",
            uploadedByUserId = "user-uuid",
            uploadedByUserName = "Bob",
            uploadedAt = "2025-01-15T09:00:00Z",
        )
        val decoded = json.decodeFromString<AttachmentDto>(json.encodeToString(dto))
        assertEquals(dto, decoded)
    }

    @Test
    fun `MyTaskReportDto round-trips`() {
        val dto = MyTaskReportDto(
            id = "task-uuid",
            taskCode = "PROJ_42",
            title = "Implement search",
            description = null,
            status = TaskStatus.TODO,
            phaseName = "BACKLOG",
            plannedStart = "2025-02-01T00:00:00Z",
            plannedEnd = "2025-02-15T00:00:00Z",
            updatedAt = "2025-01-20T08:00:00Z",
        )
        val decoded = json.decodeFromString<MyTaskReportDto>(json.encodeToString(dto))
        assertEquals(dto, decoded)
    }

    @Test
    fun `NotificationTemplateDto round-trips`() {
        val dto = NotificationTemplateDto(
            id = "tmpl-uuid",
            projectId = "proj-uuid",
            eventType = TaskChangeType.TASK_CREATED,
            subjectTemplate = "New task: {taskTitle}",
            bodyTemplate = "Task {taskId} was created.",
        )
        val decoded = json.decodeFromString<NotificationTemplateDto>(json.encodeToString(dto))
        assertEquals(dto, decoded)
    }

    @Test
    fun `PageDto round-trips with ProjectDto content`() {
        val dto = PageDto(
            content = listOf(
                ProjectDto("p1", "Alpha", null, "AL_", null),
                ProjectDto("p2", "Beta", "desc", null, "phase-uuid"),
            ),
            page = 0,
            size = 20,
            totalElements = 2,
            totalPages = 1,
            last = true,
        )
        val decoded = json.decodeFromString<PageDto<ProjectDto>>(json.encodeToString(dto))
        assertEquals(dto, decoded)
    }

    @Test
    fun `TaskCreateRequest round-trips`() {
        val req = TaskCreateRequest(
            title = "Add login screen",
            description = "OAuth flow",
            status = TaskStatus.TODO,
            type = TaskType.FEATURE,
            progress = 0,
            projectId = "proj-uuid",
            phaseId = null,
        )
        val decoded = json.decodeFromString<TaskCreateRequest>(json.encodeToString(req))
        assertEquals(req, decoded)
    }

    @Test
    fun `null optional fields deserialize to null without error`() {
        // Explicit null values in JSON map to nullable Kotlin fields.
        val jsonStr = """
            {"id":"u1","name":"Alice","email":"a@b.com","username":"alice",
             "active":true,"avatarFileId":null,"language":null,"roles":[]}
        """.trimIndent()
        val user = json.decodeFromString<UserDto>(jsonStr)
        assertEquals(null, user.avatarFileId)
        assertEquals(null, user.language)
        assertEquals(emptyList<String>(), user.roles)
    }
}
