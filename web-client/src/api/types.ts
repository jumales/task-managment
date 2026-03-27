/** Mirrors com.demo.common.dto.TaskStatus */
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';

/** Mirrors com.demo.common.dto.PageResponse */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface UserDto {
  id: string;
  name: string;
  email: string;
  username: string | null;
  active: boolean;
}

export interface TaskProjectResponse {
  id: string;
  name: string;
  description: string;
}

export interface TaskPhaseResponse {
  id: string;
  name: string;
  isDefault: boolean;
}

export interface TaskCommentResponse {
  id: string;
  content: string;
  createdAt: string;
}

/** Mirrors com.demo.common.dto.TaskParticipantRole */
export type TaskParticipantRole = 'CREATOR' | 'ASSIGNEE' | 'VIEWER' | 'REVIEWER';

/** Mirrors com.demo.common.dto.TaskParticipantResponse */
export interface TaskParticipantResponse {
  id: string;
  userId: string;
  userName: string | null;
  userEmail: string | null;
  role: TaskParticipantRole;
}

/** Mirrors com.demo.common.dto.TaskParticipantRequest */
export interface TaskParticipantRequest {
  userId: string;
  role: TaskParticipantRole;
}

export interface TaskResponse {
  id: string;
  title: string;
  description: string;
  status: TaskStatus;
  participants: TaskParticipantResponse[];
  project: TaskProjectResponse;
  phase: TaskPhaseResponse | null;
}

export interface TaskRequest {
  title: string;
  description: string;
  status: TaskStatus;
  assignedUserId: string;
  projectId: string;
  phaseId: string | null;
}

export interface TaskProjectRequest {
  name: string;
  description: string;
}

export interface UserRequest {
  name: string;
  email: string;
  username?: string | null;
  active: boolean;
}

export interface RoleDto {
  id: string;
  name: string;
  description: string;
}

export interface UserResponse {
  id: string;
  name: string;
  email: string;
  username: string | null;
  active: boolean;
  roles: RoleDto[];
  avatarFileId: string | null;
}

export interface FileUploadResponse {
  fileId: string;
  bucket: string;
  objectKey: string;
  contentType: string;
}

export interface PresignedUrlResponse {
  url: string;
}

/** Mirrors com.demo.search.document.TaskDocument */
export interface TaskDocument {
  id: string;
  title: string;
  description: string | null;
  status: TaskStatus | null;
  projectId: string | null;
  projectName: string | null;
  phaseId: string | null;
  phaseName: string | null;
  assignedUserId: string | null;
  assignedUserName: string | null;
}

/** Mirrors com.demo.search.document.UserDocument */
export interface UserDocument {
  id: string;
  name: string;
  email: string;
  username: string;
  active: boolean;
}

export interface AuditRecord {
  id: string;
  taskId: string;
  changedAt: string;
  oldStatus: TaskStatus;
  newStatus: TaskStatus;
}
