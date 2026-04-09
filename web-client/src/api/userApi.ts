import apiClient from './client';
import type { FileUploadResponse, PageResponse, PresignedUrlResponse, UserResponse, UserRequest } from './types';

const USERS_URL = '/api/v1/users';
const FILES_URL = '/api/v1/files';

/** Fetches a paginated list of users. */
export function getUsers(params?: { page?: number; size?: number }) {
  return apiClient.get<PageResponse<UserResponse>>(USERS_URL, { params: { size: 20, ...params } }).then((r) => r.data);
}

/** Fetches the profile of the currently authenticated user. */
export function getMe() {
  return apiClient.get<UserResponse>(`${USERS_URL}/me`).then((r) => r.data);
}

/** Fetches a single user by ID. */
export function getUser(id: string) {
  return apiClient.get<UserResponse>(`${USERS_URL}/${id}`).then((r) => r.data);
}

/** Creates a new user. */
export function createUser(request: UserRequest) {
  return apiClient.post<UserResponse>(USERS_URL, request).then((r) => r.data);
}

/** Updates name, email, and active flag of the given user. */
export function updateUser(id: string, request: UserRequest) {
  return apiClient.put<UserResponse>(`${USERS_URL}/${id}`, request).then((r) => r.data);
}

/**
 * Uploads an avatar image to file-service and returns the file metadata.
 * Use the returned fileId with updateUserAvatar().
 */
export function uploadAvatar(file: File) {
  const form = new FormData();
  form.append('file', file);
  return apiClient
    .post<FileUploadResponse>(`${FILES_URL}/avatars`, form)
    .then((r) => r.data);
}

/** Updates the user's preferred UI language. */
export function updateUserLanguage(userId: string, language: string) {
  return apiClient
    .patch<UserResponse>(`${USERS_URL}/${userId}/language`, { language })
    .then((r) => r.data);
}

/** Sets or clears the user's profile picture. Pass null to remove the avatar. */
export function updateUserAvatar(userId: string, fileId: string | null) {
  return apiClient
    .patch<UserResponse>(`${USERS_URL}/${userId}/avatar`, { fileId })
    .then((r) => r.data);
}

/** Fetches the manageable realm roles currently held by the user. */
export function getUserRoles(userId: string) {
  return apiClient.get<string[]>(`${USERS_URL}/${userId}/roles`).then((r) => r.data);
}

/** Replaces all manageable realm roles for the user. Returns the updated role list. */
export function setUserRoles(userId: string, roles: string[]) {
  return apiClient.put<string[]>(`${USERS_URL}/${userId}/roles`, roles).then((r) => r.data);
}

/** Returns a short-lived presigned URL for the given fileId. */
export function getAvatarUrl(fileId: string) {
  return apiClient
    .get<PresignedUrlResponse>(`${FILES_URL}/${fileId}/url`)
    .then((r) => r.data.url);
}

/**
 * Downloads the raw file bytes through the API gateway (authenticated).
 * Returns a local blob URL suitable for use as an <img> src.
 * The caller is responsible for calling URL.revokeObjectURL() on cleanup.
 */
export function downloadFile(fileId: string): Promise<string> {
  return apiClient
    .get(`${FILES_URL}/${fileId}/download`, { responseType: 'blob' })
    .then((r) => URL.createObjectURL(r.data));
}
