/** Mirrors com.demo.common.dto.TaskStatus */
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';

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

export interface TaskResponse {
  id: string;
  title: string;
  description: string;
  status: TaskStatus;
  assignedUser: UserDto | null;
  project: TaskProjectResponse;
  phase: TaskPhaseResponse | null;
  comments: TaskCommentResponse[];
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

export interface AuditRecord {
  id: string;
  taskId: string;
  changedAt: string;
  oldStatus: TaskStatus;
  newStatus: TaskStatus;
}
