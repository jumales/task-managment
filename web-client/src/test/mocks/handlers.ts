import { http, HttpResponse } from 'msw';
import type {
  TaskResponse,
  TaskProjectResponse,
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
};

export const mockUser: UserResponse = {
  id: 'user-1',
  name: 'Alice Smith',
  email: 'alice@example.com',
  roles: [
    { id: 'role-1', name: 'ADMIN', description: 'Administrator' },
  ],
};

export const mockUser2: UserResponse = {
  id: 'user-2',
  name: 'Bob Jones',
  email: 'bob@example.com',
  roles: [
    { id: 'role-2', name: 'DEVELOPER', description: 'Developer' },
  ],
};

export const mockTask: TaskResponse = {
  id: 'task-1',
  title: 'Fix login bug',
  description: 'Users cannot log in',
  status: 'TODO',
  assignedUser: { id: 'user-1', name: 'Alice Smith', email: 'alice@example.com' },
  project: mockProject,
  phase: null,
  comments: [],
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
    return HttpResponse.json([mockTask]);
  }),

  http.get('*/api/v1/tasks/:id', ({ params }) => {
    return HttpResponse.json({ ...mockTask, id: params.id as string });
  }),

  http.post('*/api/v1/tasks', async ({ request }) => {
    const body = await request.json() as Record<string, unknown>;
    const created: TaskResponse = {
      id: 'task-new',
      title: body.title as string,
      description: (body.description as string) ?? '',
      status: (body.status as TaskResponse['status']) ?? 'TODO',
      assignedUser: { id: 'user-1', name: 'Alice Smith', email: 'alice@example.com' },
      project: mockProject,
      phase: null,
      comments: [],
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

  http.post('*/api/v1/tasks/:taskId/comments', async ({ params, request }) => {
    const body = await request.json() as Record<string, unknown>;
    return HttpResponse.json({
      ...mockTask,
      id: params.taskId as string,
      comments: [{ id: 'comment-1', content: body.content as string, createdAt: '2026-03-20T10:00:00Z' }],
    });
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

  // Users
  http.get('*/api/v1/users', () => {
    return HttpResponse.json([mockUser, mockUser2]);
  }),

  http.get('*/api/v1/users/:id', ({ params }) => {
    return HttpResponse.json({ ...mockUser, id: params.id as string });
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
