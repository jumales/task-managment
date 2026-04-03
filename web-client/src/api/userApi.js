import apiClient from './client';
const USERS_URL = '/api/v1/users';
const FILES_URL = '/api/v1/files';
/** Fetches a paginated list of users. */
export function getUsers(params) {
    return apiClient.get(USERS_URL, { params: { size: 20, ...params } }).then((r) => r.data);
}
/** Fetches the profile of the currently authenticated user. */
export function getMe() {
    return apiClient.get(`${USERS_URL}/me`).then((r) => r.data);
}
/** Fetches a single user by ID. */
export function getUser(id) {
    return apiClient.get(`${USERS_URL}/${id}`).then((r) => r.data);
}
/** Creates a new user. */
export function createUser(request) {
    return apiClient.post(USERS_URL, request).then((r) => r.data);
}
/** Updates name, email, and active flag of the given user. */
export function updateUser(id, request) {
    return apiClient.put(`${USERS_URL}/${id}`, request).then((r) => r.data);
}
/**
 * Uploads an avatar image to file-service and returns the file metadata.
 * Use the returned fileId with updateUserAvatar().
 */
export function uploadAvatar(file) {
    const form = new FormData();
    form.append('file', file);
    return apiClient
        .post(`${FILES_URL}/avatars`, form)
        .then((r) => r.data);
}
/** Updates the user's preferred UI language. */
export function updateUserLanguage(userId, language) {
    return apiClient
        .patch(`${USERS_URL}/${userId}/language`, { language })
        .then((r) => r.data);
}
/** Sets or clears the user's profile picture. Pass null to remove the avatar. */
export function updateUserAvatar(userId, fileId) {
    return apiClient
        .patch(`${USERS_URL}/${userId}/avatar`, { fileId })
        .then((r) => r.data);
}
/** Returns a short-lived presigned URL for the given fileId. */
export function getAvatarUrl(fileId) {
    return apiClient
        .get(`${FILES_URL}/${fileId}/url`)
        .then((r) => r.data.url);
}
/**
 * Downloads the raw file bytes through the API gateway (authenticated).
 * Returns a local blob URL suitable for use as an <img> src.
 * The caller is responsible for calling URL.revokeObjectURL() on cleanup.
 */
export function downloadFile(fileId) {
    return apiClient
        .get(`${FILES_URL}/${fileId}/download`, { responseType: 'blob' })
        .then((r) => URL.createObjectURL(r.data));
}
