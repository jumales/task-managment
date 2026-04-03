import { http, HttpResponse } from 'msw';
// ---------------------------------------------------------------------------
// Fixture data
// ---------------------------------------------------------------------------
export const mockProject = {
    id: 'proj-1',
    name: 'Alpha Project',
    description: 'First project',
    taskCodePrefix: 'ALPHA',
    defaultPhaseId: 'phase-1',
};
export const mockPhase = {
    id: 'phase-1',
    name: 'TODO',
    description: null,
    customName: null,
    projectId: 'proj-1',
};
export const mockUser = {
    id: 'user-1',
    name: 'Alice Smith',
    email: 'alice@example.com',
    username: 'alice',
    active: true,
    avatarFileId: null,
    language: 'en',
    roles: [
        { id: 'role-1', name: 'ADMIN', description: 'Administrator' },
    ],
};
export const mockUser2 = {
    id: 'user-2',
    name: 'Bob Jones',
    email: 'bob@example.com',
    username: 'bob',
    active: true,
    avatarFileId: null,
    language: 'en',
    roles: [
        { id: 'role-2', name: 'DEVELOPER', description: 'Developer' },
    ],
};
export const mockTask = {
    id: 'task-1',
    title: 'Fix login bug',
    description: 'Users cannot log in',
    status: 'TODO',
    type: null,
    progress: 0,
    participants: [{ id: 'part-1', userId: 'user-1', userName: 'Alice Smith', userEmail: 'alice@example.com', role: 'ASSIGNEE' }],
    project: mockProject,
    phase: mockPhase,
};
export const mockTaskSummary = {
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
};
export const mockAuditRecord = {
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
        const page = {
            content: [mockTaskSummary],
            page: 0, size: 20, totalElements: 1, totalPages: 1, last: true,
        };
        return HttpResponse.json(page);
    }),
    http.get('*/api/v1/tasks/:id', ({ params }) => {
        return HttpResponse.json({ ...mockTask, id: params.id });
    }),
    http.post('*/api/v1/tasks', async ({ request }) => {
        const body = await request.json();
        const created = {
            id: 'task-new',
            title: body.title,
            description: body.description ?? '',
            status: body.status ?? 'TODO',
            type: null,
            progress: 0,
            participants: [{ id: 'part-new', userId: 'user-1', userName: 'Alice Smith', userEmail: 'alice@example.com', role: 'ASSIGNEE' }],
            project: mockProject,
            phase: mockPhase,
        };
        return HttpResponse.json(created, { status: 201 });
    }),
    http.put('*/api/v1/tasks/:id', async ({ params, request }) => {
        const body = await request.json();
        return HttpResponse.json({ ...mockTask, id: params.id, ...body });
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
        const body = await request.json();
        return HttpResponse.json({ id: 'part-new', userId: body.userId, userName: null, userEmail: null, role: body.role }, { status: 201 });
    }),
    http.delete('*/api/v1/tasks/:taskId/participants/:participantId', () => {
        return new HttpResponse(null, { status: 204 });
    }),
    http.post('*/api/v1/tasks/:taskId/comments', async ({ request }) => {
        const body = await request.json();
        return HttpResponse.json({ id: 'comment-1', content: body.content, createdAt: '2026-03-20T10:00:00Z' }, { status: 201 });
    }),
    // Projects
    http.get('*/api/v1/projects', () => {
        return HttpResponse.json([mockProject]);
    }),
    http.post('*/api/v1/projects', async ({ request }) => {
        const body = await request.json();
        return HttpResponse.json({ id: 'proj-new', name: body.name, description: body.description ?? '' }, { status: 201 });
    }),
    http.put('*/api/v1/projects/:id', async ({ params, request }) => {
        const body = await request.json();
        return HttpResponse.json({
            id: params.id,
            name: body.name,
            description: body.description ?? '',
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
        const page = {
            content: [mockUser, mockUser2],
            page: 0, size: 20, totalElements: 2, totalPages: 1, last: true,
        };
        return HttpResponse.json(page);
    }),
    http.get('*/api/v1/users/:id', ({ params }) => {
        return HttpResponse.json({ ...mockUser, id: params.id });
    }),
    http.post('*/api/v1/users', async ({ request }) => {
        const body = await request.json();
        return HttpResponse.json({ ...mockUser, id: 'user-new', name: body.name, email: body.email, username: body.username ?? null }, { status: 201 });
    }),
    http.put('*/api/v1/users/:id', async ({ params, request }) => {
        const body = await request.json();
        return HttpResponse.json({ ...mockUser, id: params.id, name: body.name, email: body.email, active: body.active });
    }),
    http.patch('*/api/v1/users/:id/avatar', async ({ params, request }) => {
        const body = await request.json();
        return HttpResponse.json({ ...mockUser, id: params.id, avatarFileId: body.fileId });
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
