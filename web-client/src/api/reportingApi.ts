import apiClient from './client';
import type { MyTaskReport, TaskHoursRow, ProjectHoursRow, DetailedHoursRow } from './types';

/** Fetch all tasks assigned to the current user. */
export function getMyTasks(): Promise<MyTaskReport[]> {
  return apiClient.get<MyTaskReport[]>('/api/v1/reports/my-tasks').then((r) => r.data);
}

/** Fetch tasks assigned to the current user updated within the last {@code days} days. */
export function getMyTasksFiltered(days: number): Promise<MyTaskReport[]> {
  return apiClient.get<MyTaskReport[]>(`/api/v1/reports/my-tasks?days=${days}`).then((r) => r.data);
}

/** Fetch planned vs booked hours per task, optionally filtered by project. */
export function getHoursByTask(projectId?: string): Promise<TaskHoursRow[]> {
  const params = projectId ? `?projectId=${projectId}` : '';
  return apiClient.get<TaskHoursRow[]>(`/api/v1/reports/hours/by-task${params}`).then((r) => r.data);
}

/** Fetch planned vs booked hours totalled per project. */
export function getHoursByProject(): Promise<ProjectHoursRow[]> {
  return apiClient.get<ProjectHoursRow[]>('/api/v1/reports/hours/by-project').then((r) => r.data);
}

/** Fetch planned vs booked hours for a task broken down by user and work type. */
export function getHoursDetailed(taskId: string): Promise<DetailedHoursRow[]> {
  return apiClient.get<DetailedHoursRow[]>(`/api/v1/reports/hours/detailed?taskId=${taskId}`).then((r) => r.data);
}
