import apiClient from './client';
/** Full-text search across tasks using Elasticsearch. */
export function searchTasks(query) {
    return apiClient.get('/api/v1/search/tasks', { params: { q: query } })
        .then((res) => res.data);
}
/** Full-text search across users using Elasticsearch. */
export function searchUsers(query) {
    return apiClient.get('/api/v1/search/users', { params: { q: query } })
        .then((res) => res.data);
}
