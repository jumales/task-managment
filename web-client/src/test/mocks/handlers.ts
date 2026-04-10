import { http, HttpResponse } from 'msw';
import type {
  TaskResponse,
  TaskSummaryResponse,
  TaskProjectResponse,
  TaskPhaseResponse,
  PageResponse,
  UserResponse,
  AuditRecord,
} from '../../api/types';

// ---------------------------------------------------------------------------
// Fixture data
// ---------------------------------------------------------------------------

export const mockProject: TaskProjectResponse = {
  id: 'proj-1',
  name: 'Alpha Project',
  description: 'First project',
  taskCodePrefix: 'ALPHA',
  defaultPhaseId: 'phase-1',
};

export const mockPhase: TaskPhaseResponse = {
  id: 'phase-1',
  name: 'TODO',
  description: null,
  customName: null,
  projectId: 'proj-1',
};

export const mockUser: UserResponse = {
  id: 'user-1',
  name: 'Alice Smith',
  email: 'alice@example.com',
  username: 'alice',
  active: true,
  avatarFileId: null,
  language: 'en',
  roles: [],
};

export const mockUser2: UserResponse = {
  id: 'user-2',
  name: 'Bob Jones',
  email: 'bob@example.com',
  username: 'bob',
  active: true,
  avatarFileId: null,
  language: 'en',
  roles: [],
};

export const mockTask: TaskResponse = {
  id: 'task-1',
  taskCode: 'ALPHA-1',
  title: 'Fix login bug',
  description: 'Users cannot log in',
  status: 'TODO',
  type: null,
  progress: 0,
  participants: [{ id: 'part-1', userId: 'user-1', userName: 'Alice Smith', userEmail: 'alice@example.com', role: 'ASSIGNEE' }],
  project: mockProject,
  phase: mockPhase,
};

export const mockTaskSummary: TaskSummaryResponse = {
  id: 'task-1',
  title: 'Fix login bug',
  description: 'Users cannot log in',
  status: 'TODO',
  type: null,
  progress: 0,
  assignedUserId: 'user-1',
  assignedUserName: 'Alice Smith',
  projectId: 'proj-1',
  projectName: 'Alpha Project',
  phaseId: 'phase-1',
  phaseName: 'TODO',
  taskCode: 'ALPHA-1',
};

export const mockAuditRecord: AuditRecord = {
  id: 'audit-1',
  taskId: 'task-1',
  changedAt: '2026-03-20T10:00:00Z',
  oldStatus: 'TODO',
  newStatus: 'IN_PROGRESS',
};

// ---------------------------------------------------------------------------
// Default (success) handlers
// ---------------------------------------------------------------------------

export const handlers = [
  // Tasks
  http.get('*/api/v1/tasks', () => {
    const page: PageResponse<TaskSummaryResponse> = {
      content: [mockTaskSummary],
      page: 0, size: 20, totalElements: 1, totalPages: 1, last: true,
    };
    return HttpResponse.json(page);
  }),

  http.get('*/api/v1/tasks/:id', ({ params }) => {
    return HttpResponse.json({ ...mockTask, id: params.id as string });
  }),

  http.post('*/api/v1/tasks', async ({ request }) => {
    const body = await request.json() as Record<string, unknown>;
    const created: TaskResponse = {
      id: 'task-new',
      taskCode: null,
      title: body.title as string,
      description: (body.description as string) ?? '',
      status: (body.status as TaskResponse['status']) ?? 'TODO',
      type: null,
      progress: 0,
      participants: [{ id: 'part-new', userId: 'user-1', userName: 'Alice Smith', userEmail: 'alice@example.com', role: 'ASSIGNEE' as const }],
      project: mockProject,
      phase: mockPhase,
    };
    return HttpResponse.json(created, { status: 201 });
  }),

  http.put('*/api/v1/tasks/:id', async ({ params, request }) => {
    const body = await request.json() as Record<string, unknown>;
    return HttpResponse.json({ ...mockTask, id: params.id as string, ...body });
  }),

  http.delete('*/api/v1/tasks/:id', () => {
    return new HttpResponse(null, { status: 204 });
  }),

  http.get('*/api/v1/tasks/:taskId/comments', () => {
    return HttpResponse.json([]);
  }),

  http.get('*/api/v1/tasks/:taskId/participants', () => {
    return HttpResponse.json([]);
  }),

  http.post('*/api/v1/tasks/:taskId/participants', async ({ request }) => {
    const body = await request.json() as Record<string, unknown>;
    return HttpResponse.json(
      { id: 'part-new', userId: body.userId as string, userName: null, userEmail: null, role: body.role as string },
      { status: 201 },
    );
  }),

  http.delete('*/api/v1/tasks/:taskId/participants/:participantId', () => {
    return new HttpResponse(null, { status: 204 });
  }),

  http.post('*/api/v1/tasks/:taskId/comments', async ({ request }) => {
    const body = await request.json() as Record<string, unknown>;
    return HttpResponse.json(
      { id: 'comment-1', content: body.content as string, createdAt: '2026-03-20T10:00:00Z' },
      { status: 201 },
    );
  }),

  // Projects
  http.get('*/api/v1/projects', () => {
    return HttpResponse.json([mockProject]);
  }),

  http.post('*/api/v1/projects', async ({ request }) => {
    const body = await request.json() as Record<string, unknown>;
    return HttpResponse.json(
      { id: 'proj-new', name: body.name as string, description: (body.description as string) ?? '' },
      { status: 201 },
    );
  }),

  http.put('*/api/v1/projects/:id', async ({ params, request }) => {
    const body = await request.json() as Record<string, unknown>;
    return HttpResponse.json({
      id: params.id as string,
      name: body.name as string,
      description: (body.description as string) ?? '',
    });
  }),

  http.delete('*/api/v1/projects/:id', () => {
    return new HttpResponse(null, { status: 204 });
  }),

  // Phases
  http.get('*/api/v1/phases', () => {
    return HttpResponse.json([mockPhase]);
  }),

  // Users
  http.get('*/api/v1/users', () => {
    const page: PageResponse<UserResponse> = {
      content: [mockUser, mockUser2],
      page: 0, size: 20, totalElements: 2, totalPages: 1, last: true,
    };
    return HttpResponse.json(page);
  }),

  http.get('*/api/v1/users/:id', ({ params }) => {
    return HttpResponse.json({ ...mockUser, id: params.id as string });
  }),

  http.post('*/api/v1/users', async ({ request }) => {
    const body = await request.json() as Record<string, unknown>;
    return HttpResponse.json(
      { ...mockUser, id: 'user-new', name: body.name as string, email: body.email as string, username: body.username ?? null },
      { status: 201 },
    );
  }),

  http.put('*/api/v1/users/:id', async ({ params, request }) => {
    const body = await request.json() as Record<string, unknown>;
    return HttpResponse.json({ ...mockUser, id: params.id as string, name: body.name as string, email: body.email as string, active: body.active as boolean });
  }),

  http.patch('*/api/v1/users/:id/avatar', async ({ params, request }) => {
    const body = await request.json() as Record<string, unknown>;
    return HttpResponse.json({ ...mockUser, id: params.id as string, avatarFileId: body.fileId as string | null });
  }),

  // Files
  http.post('*/api/v1/files/avatars', () => {
    return HttpResponse.json({ fileId: 'file-1', bucket: 'avatars', objectKey: 'file-1.jpg', contentType: 'image/jpeg' }, { status: 201 });
  }),

  http.get('*/api/v1/files/:fileId/url', () => {
    return HttpResponse.json({ url: 'http://localhost:9000/avatars/file-1.jpg' });
  }),

  // Audit
  http.get('*/api/v1/audit/tasks/:taskId/statuses', () => {
    return HttpResponse.json([mockAuditRecord]);
  }),

  http.get('*/api/v1/audit/tasks/:taskId/comments', () => {
    return HttpResponse.json([]);
  }),

  http.get('*/api/v1/audit/tasks/:taskId/phases', () => {
    return HttpResponse.json([]);
  }),
];
