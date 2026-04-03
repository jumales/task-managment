import apiClient from './client';
const AUDIT_URL = '/api/v1/audit/tasks';
/** Fetches the status change history for a task. */
export function getStatusHistory(taskId) {
    return apiClient.get(`${AUDIT_URL}/${taskId}/statuses`).then((r) => r.data);
}
/** Fetches the comment history for a task. */
export function getCommentHistory(taskId) {
    return apiClient.get(`${AUDIT_URL}/${taskId}/comments`).then((r) => r.data);
}
/** Fetches the phase change history for a task. */
export function getPhaseHistory(taskId) {
    return apiClient.get(`${AUDIT_URL}/${taskId}/phases`).then((r) => r.data);
}
