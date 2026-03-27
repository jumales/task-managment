import apiClient from './client';
import type { PageResponse, TaskResponse, TaskRequest, TaskCommentResponse, TaskProjectResponse, TaskProjectRequest } from './types';

const TASKS_URL    = '/api/v1/tasks';
const PROJECTS_URL = '/api/v1/projects';

/** Fetches a paginated list of tasks, with optional filters. */
export function getTasks(params?: { userId?: string; projectId?: string; status?: string; page?: number; size?: number }) {
  return apiClient.get<PageResponse<TaskResponse>>(TASKS_URL, { params: { size: 20, ...params } }).then((r) => r.data);
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
