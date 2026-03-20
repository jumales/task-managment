import apiClient from './client';
import type { UserResponse } from './types';

const USERS_URL = '/api/v1/users';

/** Fetches all users. */
export function getUsers() {
  return apiClient.get<UserResponse[]>(USERS_URL).then((r) => r.data);
}

/** Fetches a single user by ID. */
export function getUser(id: string) {
  return apiClient.get<UserResponse>(`${USERS_URL}/${id}`).then((r) => r.data);
}
