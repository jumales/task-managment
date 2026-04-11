import apiClient from './client';
import type {
  PageResponse,
  TaskParticipantRequest, TaskParticipantResponse,
  TaskResponse, TaskFullResponse, TaskSummaryResponse, TaskRequest,
  TaskCommentResponse,
  TaskProjectResponse, TaskProjectRequest,
  TaskPhaseResponse, TaskPhaseRequest, TaskPhaseUpdateRequest,
  TaskPlannedWorkRequest, TaskPlannedWorkResponse,
  TaskBookedWorkRequest, TaskBookedWorkResponse,
  TaskTimelineRequest, TaskTimelineResponse, TimelineState,
  ProjectNotificationTemplateResponse, ProjectNotificationTemplateRequest,
  TemplatePlaceholder, TaskChangeType,
  TaskAttachmentRequest, TaskAttachmentResponse, FileUploadResponse,
} from './types';

const TASKS_URL    = '/api/v1/tasks';
const PROJECTS_URL = '/api/v1/projects';
const PHASES_URL   = '/api/v1/phases';

/** Fetches a paginated list of tasks (summary view), with optional filters. */
export function getTasks(params?: { userId?: string; projectId?: string; status?: string; completionStatus?: string; page?: number; size?: number }) {
  return apiClient.get<PageResponse<TaskSummaryResponse>>(TASKS_URL, { params: { size: 20, ...params } }).then((r) => r.data);
}

/** Fetches a single task by ID. */
export function getTask(id: string) {
  return apiClient.get<TaskResponse>(`${TASKS_URL}/${id}`).then((r) => r.data);
}

/** Fetches the full task view including participants, timelines, planned work, booked work, and assigned user. */
export function getTaskFull(id: string) {
  return apiClient.get<TaskFullResponse>(`${TASKS_URL}/${id}/full`).then((r) => r.data);
}

/** Creates a new task. */
export function createTask(request: TaskRequest) {
  return apiClient.post<TaskResponse>(TASKS_URL, request).then((r) => r.data);
}

/** Updates an existing task. */
export function updateTask(id: string, request: TaskRequest) {
  return apiClient.put<TaskResponse>(`${TASKS_URL}/${id}`, request).then((r) => r.data);
}

/** Changes the phase of a task. Enforces the one-way gate: cannot return to PLANNING once left. */
export function updateTaskPhase(id: string, request: TaskPhaseUpdateRequest) {
  return apiClient.patch<TaskResponse>(`${TASKS_URL}/${id}/phase`, request).then((r) => r.data);
}

/** Soft-deletes a task. */
export function deleteTask(id: string) {
  return apiClient.delete(`${TASKS_URL}/${id}`);
}

/** Fetches all comments for a task, ordered by creation time. */
export function getTaskComments(taskId: string) {
  return apiClient.get<TaskCommentResponse[]>(`${TASKS_URL}/${taskId}/comments`).then((r) => r.data);
}

/** Adds a comment to a task and returns the created comment. */
export function addComment(taskId: string, content: string) {
  return apiClient.post<TaskCommentResponse>(`${TASKS_URL}/${taskId}/comments`, { content }).then((r) => r.data);
}

/** Fetches all participants for a task. */
export function getParticipants(taskId: string) {
  return apiClient.get<TaskParticipantResponse[]>(`${TASKS_URL}/${taskId}/participants`).then((r) => r.data);
}

/** Adds the authenticated user as a WATCHER on a task. */
export function watchTask(taskId: string) {
  return apiClient.post<TaskParticipantResponse>(`${TASKS_URL}/${taskId}/participants/watch`).then((r) => r.data);
}

/** Removes own WATCHER entry from a task. */
export function removeParticipant(taskId: string, participantId: string) {
  return apiClient.delete(`${TASKS_URL}/${taskId}/participants/${participantId}`);
}

/** Fetches all planned work entries for a task. */
export function getPlannedWork(taskId: string) {
  return apiClient.get<TaskPlannedWorkResponse[]>(`${TASKS_URL}/${taskId}/planned-work`).then((r) => r.data);
}

/** Adds a planned work entry (only allowed when task status is TODO). */
export function createPlannedWork(taskId: string, request: TaskPlannedWorkRequest) {
  return apiClient.post<TaskPlannedWorkResponse>(`${TASKS_URL}/${taskId}/planned-work`, request).then((r) => r.data);
}

/** Fetches all booked work entries for a task. */
export function getBookedWork(taskId: string) {
  return apiClient.get<TaskBookedWorkResponse[]>(`${TASKS_URL}/${taskId}/booked-work`).then((r) => r.data);
}

/** Adds a booked work entry to a task. */
export function createBookedWork(taskId: string, request: TaskBookedWorkRequest) {
  return apiClient.post<TaskBookedWorkResponse>(`${TASKS_URL}/${taskId}/booked-work`, request).then((r) => r.data);
}

/** Updates an existing booked work entry. */
export function updateBookedWork(taskId: string, bookedWorkId: string, request: TaskBookedWorkRequest) {
  return apiClient.put<TaskBookedWorkResponse>(`${TASKS_URL}/${taskId}/booked-work/${bookedWorkId}`, request).then((r) => r.data);
}

/** Soft-deletes a booked work entry. */
export function deleteBookedWork(taskId: string, bookedWorkId: string) {
  return apiClient.delete(`${TASKS_URL}/${taskId}/booked-work/${bookedWorkId}`);
}

/** Fetches all active timeline entries for a task. */
export function getTimelines(taskId: string) {
  return apiClient.get<TaskTimelineResponse[]>(`${TASKS_URL}/${taskId}/timelines`).then((r) => r.data);
}

/** Creates or updates the timeline entry for a specific state (upsert). */
export function setTimeline(taskId: string, state: TimelineState, request: TaskTimelineRequest) {
  return apiClient.put<TaskTimelineResponse>(`${TASKS_URL}/${taskId}/timelines/${state}`, request).then((r) => r.data);
}

/** Removes the timeline entry for a specific state. */
export function deleteTimeline(taskId: string, state: TimelineState) {
  return apiClient.delete(`${TASKS_URL}/${taskId}/timelines/${state}`);
}

/** Fetches all projects. */
export function getProjects() {
  return apiClient.get<TaskProjectResponse[]>(PROJECTS_URL).then((r) => r.data);
}

/** Creates a new project. */
export function createProject(request: TaskProjectRequest) {
  return apiClient.post<TaskProjectResponse>(PROJECTS_URL, request).then((r) => r.data);
}

/** Updates an existing project. */
export function updateProject(id: string, request: TaskProjectRequest) {
  return apiClient.put<TaskProjectResponse>(`${PROJECTS_URL}/${id}`, request).then((r) => r.data);
}

/** Soft-deletes a project. */
export function deleteProject(id: string) {
  return apiClient.delete(`${PROJECTS_URL}/${id}`);
}

/** Fetches all phases for a project. */
export function getPhases(projectId: string) {
  return apiClient.get<TaskPhaseResponse[]>(PHASES_URL, { params: { projectId } }).then((r) => r.data);
}

/** Creates a new phase for a project. */
export function createPhase(request: TaskPhaseRequest) {
  return apiClient.post<TaskPhaseResponse>(PHASES_URL, request).then((r) => r.data);
}

/** Updates an existing phase (e.g. sets or clears customName). */
export function updatePhase(id: string, request: TaskPhaseRequest) {
  return apiClient.put<TaskPhaseResponse>(`${PHASES_URL}/${id}`, request).then((r) => r.data);
}

/** Soft-deletes a phase. */
export function deletePhase(id: string) {
  return apiClient.delete(`${PHASES_URL}/${id}`);
}

/** Fetches all active notification templates for a project. */
export function getNotificationTemplates(projectId: string) {
  return apiClient.get<ProjectNotificationTemplateResponse[]>(`${PROJECTS_URL}/${projectId}/notification-templates`).then((r) => r.data);
}

/** Fetches the full placeholder catalogue (same for every project). */
export function getTemplatePlaceholders(projectId: string) {
  return apiClient.get<TemplatePlaceholder[]>(`${PROJECTS_URL}/${projectId}/notification-templates/placeholders`).then((r) => r.data);
}

/** Creates or replaces the notification template for a project + event type. */
export function upsertNotificationTemplate(projectId: string, eventType: TaskChangeType, request: ProjectNotificationTemplateRequest) {
  return apiClient.put<ProjectNotificationTemplateResponse>(`${PROJECTS_URL}/${projectId}/notification-templates/${eventType}`, request).then((r) => r.data);
}

/** Soft-deletes the notification template for a project + event type. */
export function deleteNotificationTemplate(projectId: string, eventType: TaskChangeType) {
  return apiClient.delete(`${PROJECTS_URL}/${projectId}/notification-templates/${eventType}`);
}

/** Uploads a file to the attachments bucket in file-service and returns its metadata. */
export function uploadFile(file: File) {
  const form = new FormData();
  form.append('file', file);
  return apiClient.post<FileUploadResponse>('/api/v1/files/attachments', form).then((r) => r.data);
}

/** Fetches all attachments for a task, ordered by upload time. */
export function getAttachments(taskId: string) {
  return apiClient.get<TaskAttachmentResponse[]>(`${TASKS_URL}/${taskId}/attachments`).then((r) => r.data);
}

/** Registers an already-uploaded file as a task attachment. */
export function addAttachment(taskId: string, request: TaskAttachmentRequest) {
  return apiClient.post<TaskAttachmentResponse>(`${TASKS_URL}/${taskId}/attachments`, request).then((r) => r.data);
}

/** Permanently deletes the attachment record and removes the file from storage. */
export function deleteAttachment(taskId: string, attachmentId: string) {
  return apiClient.delete(`${TASKS_URL}/${taskId}/attachments/${attachmentId}`);
}
