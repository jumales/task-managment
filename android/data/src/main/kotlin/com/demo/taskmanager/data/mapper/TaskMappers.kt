package com.demo.taskmanager.data.mapper

import com.demo.taskmanager.data.dto.AttachmentDto
import com.demo.taskmanager.data.dto.BookedWorkDto
import com.demo.taskmanager.data.dto.CommentDto
import com.demo.taskmanager.data.dto.PhaseDto
import com.demo.taskmanager.data.dto.PlannedWorkDto
import com.demo.taskmanager.data.dto.ProjectDto
import com.demo.taskmanager.data.dto.TaskDto
import com.demo.taskmanager.data.dto.TaskSummaryDto
import com.demo.taskmanager.data.dto.UserDto
import com.demo.taskmanager.data.dto.enums.TaskPhaseName as DtoPhaseName
import com.demo.taskmanager.data.dto.enums.TaskStatus as DtoStatus
import com.demo.taskmanager.data.dto.enums.TaskType as DtoType
import com.demo.taskmanager.data.dto.enums.WorkType as DtoWorkType
import com.demo.taskmanager.domain.model.Attachment
import com.demo.taskmanager.domain.model.BookedWork
import com.demo.taskmanager.domain.model.Comment
import com.demo.taskmanager.domain.model.Phase
import com.demo.taskmanager.domain.model.PlannedWork
import com.demo.taskmanager.domain.model.Project
import com.demo.taskmanager.domain.model.Task
import com.demo.taskmanager.domain.model.TaskPhaseName
import com.demo.taskmanager.domain.model.TaskStatus
import com.demo.taskmanager.domain.model.TaskSummary
import com.demo.taskmanager.domain.model.TaskType
import com.demo.taskmanager.domain.model.User
import com.demo.taskmanager.domain.model.WorkType

fun TaskSummaryDto.toDomain() = TaskSummary(
    id = id,
    taskCode = taskCode,
    title = title,
    description = description,
    status = status.toDomain(),
    type = type?.toDomain(),
    progress = progress,
    assignedUserId = assignedUserId,
    assignedUserName = assignedUserName,
    projectId = projectId,
    projectName = projectName,
    phaseId = phaseId,
    phaseName = phaseName,
)

fun TaskDto.toDomain() = Task(
    id = id,
    taskCode = taskCode,
    title = title,
    description = description,
    status = status.toDomain(),
    type = type?.toDomain(),
    progress = progress,
    project = project?.toDomain(),
    phase = phase?.toDomain(),
    version = version,
)

fun ProjectDto.toDomain() = Project(
    id = id,
    name = name,
    description = description,
    taskCodePrefix = taskCodePrefix,
    defaultPhaseId = defaultPhaseId,
)

fun PhaseDto.toDomain() = Phase(
    id = id,
    name = name.toDomain(),
    description = description,
    customName = customName,
    projectId = projectId,
)

fun UserDto.toDomain() = User(
    id = id,
    name = name,
    email = email,
    username = username,
    active = active,
    avatarFileId = avatarFileId,
    language = language,
    roles = roles,
)

fun CommentDto.toDomain() = Comment(
    id = id,
    userId = userId,
    userName = userName,
    content = content,
    createdAt = createdAt,
)

fun AttachmentDto.toDomain() = Attachment(
    id = id,
    fileId = fileId,
    fileName = fileName,
    contentType = contentType,
    uploadedByUserId = uploadedByUserId,
    uploadedByUserName = uploadedByUserName,
    uploadedAt = uploadedAt,
)

fun BookedWorkDto.toDomain() = BookedWork(
    id = id,
    userId = userId,
    userName = userName,
    workType = workType.toDomain(),
    bookedHours = bookedHours,
    createdAt = createdAt,
)

fun PlannedWorkDto.toDomain() = PlannedWork(
    id = id,
    userId = userId,
    userName = userName,
    workType = workType.toDomain(),
    plannedHours = plannedHours,
    createdAt = createdAt,
)

private fun DtoStatus.toDomain() = when (this) {
    DtoStatus.TODO -> TaskStatus.TODO
    DtoStatus.IN_PROGRESS -> TaskStatus.IN_PROGRESS
    DtoStatus.DONE -> TaskStatus.DONE
}

private fun DtoType.toDomain() = when (this) {
    DtoType.FEATURE -> TaskType.FEATURE
    DtoType.BUG_FIXING -> TaskType.BUG_FIXING
    DtoType.TESTING -> TaskType.TESTING
    DtoType.PLANNING -> TaskType.PLANNING
    DtoType.TECHNICAL_DEBT -> TaskType.TECHNICAL_DEBT
    DtoType.DOCUMENTATION -> TaskType.DOCUMENTATION
    DtoType.OTHER -> TaskType.OTHER
}

private fun DtoPhaseName.toDomain() = when (this) {
    DtoPhaseName.PLANNING -> TaskPhaseName.PLANNING
    DtoPhaseName.BACKLOG -> TaskPhaseName.BACKLOG
    DtoPhaseName.TODO -> TaskPhaseName.TODO
    DtoPhaseName.IN_PROGRESS -> TaskPhaseName.IN_PROGRESS
    DtoPhaseName.IN_REVIEW -> TaskPhaseName.IN_REVIEW
    DtoPhaseName.TESTING -> TaskPhaseName.TESTING
    DtoPhaseName.DONE -> TaskPhaseName.DONE
    DtoPhaseName.RELEASED -> TaskPhaseName.RELEASED
    DtoPhaseName.REJECTED -> TaskPhaseName.REJECTED
}

private fun DtoWorkType.toDomain() = when (this) {
    DtoWorkType.DEVELOPMENT -> WorkType.DEVELOPMENT
    DtoWorkType.TESTING -> WorkType.TESTING
    DtoWorkType.CODE_REVIEW -> WorkType.CODE_REVIEW
    DtoWorkType.DESIGN -> WorkType.DESIGN
    DtoWorkType.PLANNING -> WorkType.PLANNING
    DtoWorkType.DOCUMENTATION -> WorkType.DOCUMENTATION
    DtoWorkType.DEPLOYMENT -> WorkType.DEPLOYMENT
    DtoWorkType.MEETING -> WorkType.MEETING
    DtoWorkType.OTHER -> WorkType.OTHER
}
