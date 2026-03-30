import apiClient from './client';
import type { PageResponse, TaskParticipantRequest, TaskParticipantResponse, TaskResponse, TaskSummaryResponse, TaskRequest, TaskCommentResponse, TaskProjectResponse, TaskProjectRequest, TaskWorkLogRequest, TaskWorkLogResponse, TaskTimelineRequest, TaskTimelineResponse, TimelineState, ProjectNotificationTemplateResponse, ProjectNotificationTemplateRequest, TemplatePlaceholder, TaskChangeType } from './types';

const TASKS_URL    = '/api/v1/tasks';
const PROJECTS_URL = '/api/v1/projects';

/** Fetches a paginated list of tasks (summary view), with optional filters. */
export function getTasks(params?: { userId?: string; projectId?: string; status?: string; page?: number; size?: number }) {
  return apiClient.get<PageResponse<TaskSummaryResponse>>(TASKS_URL, { params: { size: 20, ...params } }).then((r) => r.data);
}

/** Fetches a single task by ID. */
export function getTask(id: string) {
  return apiClient.get<TaskResponse>(`${TASKS_URL}/${id}`).then((r) => r.data);
}

/** Creates a new task. */
export function createTask(request: TaskRequest) {
  return apiClient.post<TaskResponse>(TASKS_URL, request).then((r) => r.data);
}

/** Updates an existing task. */
export function updateTask(id: string, request: TaskRequest) {
  return apiClient.put<TaskResponse>(`${TASKS_URL}/${id}`, request).then((r) => r.data);
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

/** Adds a participant with a role to a task. */
export function addParticipant(taskId: string, request: TaskParticipantRequest) {
  return apiClient.post<TaskParticipantResponse>(`${TASKS_URL}/${taskId}/participants`, request).then((r) => r.data);
}

/** Removes a participant from a task. */
export function removeParticipant(taskId: string, participantId: string) {
  return apiClient.delete(`${TASKS_URL}/${taskId}/participants/${participantId}`);
}

/** Fetches all work log entries for a task. */
export function getWorkLogs(taskId: string) {
  return apiClient.get<TaskWorkLogResponse[]>(`${TASKS_URL}/${taskId}/work-logs`).then((r) => r.data);
}

/** Adds a work log entry to a task. */
export function createWorkLog(taskId: string, request: TaskWorkLogRequest) {
  return apiClient.post<TaskWorkLogResponse>(`${TASKS_URL}/${taskId}/work-logs`, request).then((r) => r.data);
}

/** Updates an existing work log entry. */
export function updateWorkLog(taskId: string, workLogId: string, request: TaskWorkLogRequest) {
  return apiClient.put<TaskWorkLogResponse>(`${TASKS_URL}/${taskId}/work-logs/${workLogId}`, request).then((r) => r.data);
}

/** Soft-deletes a work log entry. */
export function deleteWorkLog(taskId: string, workLogId: string) {
  return apiClient.delete(`${TASKS_URL}/${taskId}/work-logs/${workLogId}`);
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
