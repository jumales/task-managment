import apiClient from './client';
import type { UserResponse, UserRequest } from './types';

const USERS_URL = '/api/v1/users';

/** Fetches all users. */
export function getUsers() {
  return apiClient.get<UserResponse[]>(USERS_URL).then((r) => r.data);
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
