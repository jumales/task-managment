import apiClient from './client';
const TASKS_URL = '/api/v1/tasks';
const PROJECTS_URL = '/api/v1/projects';
const PHASES_URL = '/api/v1/phases';
/** Fetches a paginated list of tasks (summary view), with optional filters. */
export function getTasks(params) {
    return apiClient.get(TASKS_URL, { params: { size: 20, ...params } }).then((r) => r.data);
}
/** Fetches a single task by ID. */
export function getTask(id) {
    return apiClient.get(`${TASKS_URL}/${id}`).then((r) => r.data);
}
/** Fetches the full task view including participants, timelines, planned work, booked work, and assigned user. */
export function getTaskFull(id) {
    return apiClient.get(`${TASKS_URL}/${id}/full`).then((r) => r.data);
}
/** Creates a new task. */
export function createTask(request) {
    return apiClient.post(TASKS_URL, request).then((r) => r.data);
}
/** Updates an existing task. */
export function updateTask(id, request) {
    return apiClient.put(`${TASKS_URL}/${id}`, request).then((r) => r.data);
}
/** Soft-deletes a task. */
export function deleteTask(id) {
    return apiClient.delete(`${TASKS_URL}/${id}`);
}
/** Fetches all comments for a task, ordered by creation time. */
export function getTaskComments(taskId) {
    return apiClient.get(`${TASKS_URL}/${taskId}/comments`).then((r) => r.data);
}
/** Adds a comment to a task and returns the created comment. */
export function addComment(taskId, content) {
    return apiClient.post(`${TASKS_URL}/${taskId}/comments`, { content }).then((r) => r.data);
}
/** Fetches all participants for a task. */
export function getParticipants(taskId) {
    return apiClient.get(`${TASKS_URL}/${taskId}/participants`).then((r) => r.data);
}
/** Adds a participant with a role to a task. */
export function addParticipant(taskId, request) {
    return apiClient.post(`${TASKS_URL}/${taskId}/participants`, request).then((r) => r.data);
}
/** Removes a participant from a task. */
export function removeParticipant(taskId, participantId) {
    return apiClient.delete(`${TASKS_URL}/${taskId}/participants/${participantId}`);
}
/** Fetches all planned work entries for a task. */
export function getPlannedWork(taskId) {
    return apiClient.get(`${TASKS_URL}/${taskId}/planned-work`).then((r) => r.data);
}
/** Adds a planned work entry (only allowed when task status is TODO). */
export function createPlannedWork(taskId, request) {
    return apiClient.post(`${TASKS_URL}/${taskId}/planned-work`, request).then((r) => r.data);
}
/** Fetches all booked work entries for a task. */
export function getBookedWork(taskId) {
    return apiClient.get(`${TASKS_URL}/${taskId}/booked-work`).then((r) => r.data);
}
/** Adds a booked work entry to a task. */
export function createBookedWork(taskId, request) {
    return apiClient.post(`${TASKS_URL}/${taskId}/booked-work`, request).then((r) => r.data);
}
/** Updates an existing booked work entry. */
export function updateBookedWork(taskId, bookedWorkId, request) {
    return apiClient.put(`${TASKS_URL}/${taskId}/booked-work/${bookedWorkId}`, request).then((r) => r.data);
}
/** Soft-deletes a booked work entry. */
export function deleteBookedWork(taskId, bookedWorkId) {
    return apiClient.delete(`${TASKS_URL}/${taskId}/booked-work/${bookedWorkId}`);
}
/** Atomically updates PLANNED_START and PLANNED_END for a task (only allowed in PLANNING phase). */
export function updatePlannedDates(taskId, request) {
    return apiClient.put(`${TASKS_URL}/${taskId}/planned-dates`, request).then((r) => r.data);
}
/** Fetches all active timeline entries for a task. */
export function getTimelines(taskId) {
    return apiClient.get(`${TASKS_URL}/${taskId}/timelines`).then((r) => r.data);
}
/** Creates or updates the timeline entry for a specific state (upsert). */
export function setTimeline(taskId, state, request) {
    return apiClient.put(`${TASKS_URL}/${taskId}/timelines/${state}`, request).then((r) => r.data);
}
/** Removes the timeline entry for a specific state. */
export function deleteTimeline(taskId, state) {
    return apiClient.delete(`${TASKS_URL}/${taskId}/timelines/${state}`);
}
/** Fetches all projects. */
export function getProjects() {
    return apiClient.get(PROJECTS_URL).then((r) => r.data);
}
/** Creates a new project. */
export function createProject(request) {
    return apiClient.post(PROJECTS_URL, request).then((r) => r.data);
}
/** Updates an existing project. */
export function updateProject(id, request) {
    return apiClient.put(`${PROJECTS_URL}/${id}`, request).then((r) => r.data);
}
/** Soft-deletes a project. */
export function deleteProject(id) {
    return apiClient.delete(`${PROJECTS_URL}/${id}`);
}
/** Fetches all phases for a project. */
export function getPhases(projectId) {
    return apiClient.get(PHASES_URL, { params: { projectId } }).then((r) => r.data);
}
/** Creates a new phase for a project. */
export function createPhase(request) {
    return apiClient.post(PHASES_URL, request).then((r) => r.data);
}
/** Updates an existing phase (e.g. sets or clears customName). */
export function updatePhase(id, request) {
    return apiClient.put(`${PHASES_URL}/${id}`, request).then((r) => r.data);
}
/** Soft-deletes a phase. */
export function deletePhase(id) {
    return apiClient.delete(`${PHASES_URL}/${id}`);
}
/** Fetches all active notification templates for a project. */
export function getNotificationTemplates(projectId) {
    return apiClient.get(`${PROJECTS_URL}/${projectId}/notification-templates`).then((r) => r.data);
}
/** Fetches the full placeholder catalogue (same for every project). */
export function getTemplatePlaceholders(projectId) {
    return apiClient.get(`${PROJECTS_URL}/${projectId}/notification-templates/placeholders`).then((r) => r.data);
}
/** Creates or replaces the notification template for a project + event type. */
export function upsertNotificationTemplate(projectId, eventType, request) {
    return apiClient.put(`${PROJECTS_URL}/${projectId}/notification-templates/${eventType}`, request).then((r) => r.data);
}
/** Soft-deletes the notification template for a project + event type. */
export function deleteNotificationTemplate(projectId, eventType) {
    return apiClient.delete(`${PROJECTS_URL}/${projectId}/notification-templates/${eventType}`);
}
