/** Mirrors com.demo.common.dto.TaskStatus */
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';

/** Mirrors com.demo.common.dto.TaskType */
export type TaskType = 'FEATURE' | 'BUG_FIXING' | 'TESTING' | 'PLANNING' | 'TECHNICAL_DEBT' | 'DOCUMENTATION' | 'OTHER';

/** Mirrors com.demo.common.dto.WorkType */
export type WorkType = 'DEVELOPMENT' | 'TESTING' | 'CODE_REVIEW' | 'DESIGN' | 'PLANNING' | 'DOCUMENTATION' | 'DEPLOYMENT' | 'MEETING' | 'OTHER';

/** Mirrors com.demo.common.dto.TaskPlannedWorkRequest */
export interface TaskPlannedWorkRequest {
  workType: WorkType;
  plannedHours: number;
}

/** Mirrors com.demo.common.dto.TaskPlannedWorkResponse */
export interface TaskPlannedWorkResponse {
  id: string;
  userId: string;
  userName: string | null;
  workType: WorkType;
  plannedHours: number;
  createdAt: string;
}

/** Mirrors com.demo.common.dto.TaskBookedWorkRequest */
export interface TaskBookedWorkRequest {
  userId: string;
  workType: WorkType;
  bookedHours: number;
}

/** Mirrors com.demo.common.dto.TaskBookedWorkResponse */
export interface TaskBookedWorkResponse {
  id: string;
  userId: string;
  userName: string | null;
  workType: WorkType;
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

/** Mirrors com.demo.common.dto.TaskPhaseName */
export type TaskPhaseName = 'PLANNING' | 'BACKLOG' | 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'TESTING' | 'DONE' | 'RELEASED';

export interface TaskProjectResponse {
  id: string;
  name: string;
  description: string;
  taskCodePrefix: string;
  /** ID of the phase automatically assigned to new tasks. Null when no default is configured. */
  defaultPhaseId: string | null;
}

export interface TaskPhaseResponse {
  id: string;
  name: TaskPhaseName;
  description: string | null;
  /** User-defined display label. Null when not set — use resolvePhaseLabel() for display. */
  customName: string | null;
  projectId: string;
}

export interface TaskCommentResponse {
  id: string;
  userId: string | null;
  userName: string | null;
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
  phase: TaskPhaseResponse;
}

/** Mirrors com.demo.task.dto.TaskFullResponse — enriched single-task view with related data. */
export interface TaskFullResponse {
  id: string;
  title: string;
  description: string;
  status: TaskStatus;
  type: TaskType | null;
  progress: number;
  participants: TaskParticipantResponse[];
  project: TaskProjectResponse;
  phase: TaskPhaseResponse;
  assignedUser: UserDto | null;
  timelines: TaskTimelineResponse[];
  plannedWork: TaskPlannedWorkResponse[];
  bookedWork: TaskBookedWorkResponse[];
}

/** Mirrors com.demo.common.dto.TaskSummaryResponse — lightweight list view without participants. */
export interface TaskSummaryResponse {
  id: string;
  taskCode: string | null;
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
  phaseId: string;
  /** Required on create; ignored on update. ISO 8601 instant string. */
  plannedStart?: string;
  /** Required on create; ignored on update. ISO 8601 instant string. */
  plannedEnd?: string;
}

export interface TaskProjectRequest {
  name: string;
  description: string;
  taskCodePrefix?: string;
  /** ID of the phase to use as the default for new tasks. Must belong to this project. */
  defaultPhaseId?: string | null;
}

export interface TaskPhaseRequest {
  name: TaskPhaseName;
  description?: string;
  /** Optional user-defined display label. Pass null to clear an existing custom name. */
  customName?: string | null;
  projectId: string;
}

/** Mirrors com.demo.common.dto.TaskPhaseUpdateRequest */
export interface TaskPhaseUpdateRequest {
  phaseId: string;
}

export interface UserRequest {
  name: string;
  email: string;
  username?: string | null;
  active: boolean;
}

/** Assignable Keycloak realm roles (WEB_APP is always held and excluded from management). */
export type RealmRole = 'ADMIN' | 'DEVELOPER' | 'QA' | 'DEVOPS' | 'PM' | 'SUPERVISOR';

export interface UserResponse {
  id: string;
  name: string;
  email: string;
  username: string | null;
  active: boolean;
  avatarFileId: string | null;
  /** ISO 639-1 language code for the user's preferred UI language (e.g. "en", "hr"). */
  language: string;
  /** Manageable Keycloak realm roles. Empty for list endpoints; populated for single-user lookups. */
  roles: string[];
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

/** Mirrors com.demo.common.dto.TaskAttachmentRequest */
export interface TaskAttachmentRequest {
  fileId: string;
  fileName: string;
  contentType: string;
}

/** Mirrors com.demo.common.dto.TaskAttachmentResponse */
export interface TaskAttachmentResponse {
  id: string;
  fileId: string;
  fileName: string;
  contentType: string;
  uploadedByUserId: string;
  uploadedByUserName: string;
  /** ISO 8601 instant string. */
  uploadedAt: string;
}

/** Mirrors com.demo.common.dto.FileUploadResponse */
export interface FileUploadResponse {
  fileId: string;
  bucket: string;
  objectKey: string;
  contentType: string;
}

/** Mirrors com.demo.common.event.TaskChangeType */
export type TaskChangeType =
  | 'TASK_CREATED'
  | 'STATUS_CHANGED'
  | 'COMMENT_ADDED'
  | 'PHASE_CHANGED'
  | 'PLANNED_WORK_CREATED'
  | 'BOOKED_WORK_CREATED'
  | 'BOOKED_WORK_UPDATED'
  | 'BOOKED_WORK_DELETED'
  | 'ATTACHMENT_ADDED'
  | 'ATTACHMENT_DELETED';

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

// ─── Reporting ───────────────────────────────────────────────────────────────

/** Mirrors com.demo.reporting.dto.MyTaskResponse */
export interface MyTaskReport {
  id: string;
  taskCode: string | null;
  title: string;
  description: string | null;
  status: TaskStatus;
  plannedStart: string | null;
  plannedEnd: string | null;
  updatedAt: string;
}

/** Mirrors com.demo.reporting.dto.TaskHoursResponse */
export interface TaskHoursRow {
  taskId: string;
  taskCode: string | null;
  title: string | null;
  plannedHours: number;
  bookedHours: number;
}

/** Mirrors com.demo.reporting.dto.ProjectHoursResponse */
export interface ProjectHoursRow {
  projectId: string;
  projectName: string | null;
  plannedHours: number;
  bookedHours: number;
}

/** Mirrors com.demo.reporting.dto.DetailedHoursResponse */
export interface DetailedHoursRow {
  userId: string;
  workType: WorkType;
  plannedHours: number;
  bookedHours: number;
}
