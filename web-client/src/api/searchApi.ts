import apiClient from './client';
import type { TaskDocument, UserDocument } from './types';

/** Full-text search across tasks using Elasticsearch. */
export function searchTasks(query: string): Promise<TaskDocument[]> {
  return apiClient.get<TaskDocument[]>('/api/v1/search/tasks', { params: { q: query } })
    .then((res) => res.data);
}

/** Full-text search across users using Elasticsearch. */
export function searchUsers(query: string): Promise<UserDocument[]> {
  return apiClient.get<UserDocument[]>('/api/v1/search/users', { params: { q: query } })
    .then((res) => res.data);
}
