/** Mirrors com.demo.common.dto.TaskStatus */
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';

/** Mirrors com.demo.common.dto.TaskType */
export type TaskType = 'FEATURE' | 'BUG_FIXING' | 'TESTING' | 'PLANNING' | 'TECHNICAL_DEBT' | 'DOCUMENTATION' | 'OTHER';

/** Mirrors com.demo.common.dto.WorkType */
export type WorkType = 'DEVELOPMENT' | 'TESTING' | 'CODE_REVIEW' | 'DESIGN' | 'PLANNING' | 'DOCUMENTATION' | 'DEPLOYMENT' | 'MEETING' | 'OTHER';

/** Mirrors com.demo.common.dto.TaskWorkLogRequest */
export interface TaskWorkLogRequest {
  userId: string;
  workType: WorkType;
  plannedHours: number;
  bookedHours: number;
}

/** Mirrors com.demo.common.dto.TaskWorkLogResponse */
export interface TaskWorkLogResponse {
  id: string;
  userId: string;
  userName: string | null;
  workType: WorkType;
  plannedHours: number;
  bookedHours: number;
  createdAt: string;
}

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
  type: TaskType | null;
  progress: number;
  participants: TaskParticipantResponse[];
  project: TaskProjectResponse;
  phase: TaskPhaseResponse | null;
}

/** Mirrors com.demo.common.dto.TaskSummaryResponse — lightweight list view without participants. */
export interface TaskSummaryResponse {
  id: string;
  title: string;
  description: string;
  status: TaskStatus;
  type: TaskType | null;
  progress: number;
  assignedUserId: string | null;
  assignedUserName: string | null;
  projectId: string | null;
  projectName: string | null;
  phaseId: string | null;
  phaseName: string | null;
}

export interface TaskRequest {
  title: string;
  description: string;
  status: TaskStatus;
  type: TaskType | null;
  progress: number;
  assignedUserId: string;
  projectId: string;
  phaseId: string | null;
  /** Required on create; ignored on update. ISO 8601 instant string. */
  plannedStart?: string;
  /** Required on create; ignored on update. ISO 8601 instant string. */
  plannedEnd?: string;
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
  /** ISO 639-1 language code for the user's preferred UI language (e.g. "en", "hr"). */
  language: string;
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

/** Mirrors com.demo.common.dto.TimelineState */
export type TimelineState = 'PLANNED_START' | 'PLANNED_END' | 'REAL_START' | 'REAL_END';

/** Mirrors com.demo.common.dto.TaskTimelineResponse */
export interface TaskTimelineResponse {
  id: string;
  taskId: string;
  state: TimelineState;
  timestamp: string;
  setByUserId: string;
  setByUserName: string | null;
  createdAt: string;
}

/** Mirrors com.demo.common.dto.TaskTimelineRequest */
export interface TaskTimelineRequest {
  setByUserId: string;
  /** ISO 8601 instant string. */
  timestamp: string;
}

/** Mirrors com.demo.common.event.TaskChangeType */
export type TaskChangeType =
  | 'TASK_CREATED'
  | 'STATUS_CHANGED'
  | 'COMMENT_ADDED'
  | 'PHASE_CHANGED'
  | 'WORK_LOG_CREATED'
  | 'WORK_LOG_UPDATED'
  | 'WORK_LOG_DELETED';

/** Mirrors com.demo.common.dto.TemplatePlaceholder */
export interface TemplatePlaceholder {
  key: string;
  description: string;
}

/** Mirrors com.demo.common.dto.ProjectNotificationTemplateResponse */
export interface ProjectNotificationTemplateResponse {
  id: string;
  projectId: string;
  eventType: TaskChangeType;
  subjectTemplate: string;
  bodyTemplate: string;
}

/** Mirrors com.demo.common.dto.ProjectNotificationTemplateRequest */
export interface ProjectNotificationTemplateRequest {
  subjectTemplate: string;
  bodyTemplate: string;
}
