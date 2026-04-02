import { describe, it, expect, beforeAll, afterEach, afterAll } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';

import { server } from '../test/mocks/server';
import { mockTaskSummary, mockProject, mockPhase, mockUser } from '../test/mocks/handlers';
import { TasksPage } from './TasksPage';

// ---------------------------------------------------------------------------
// MSW lifecycle
// ---------------------------------------------------------------------------
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

// ---------------------------------------------------------------------------
// Helper: render TasksPage inside a router
// ---------------------------------------------------------------------------
function renderTasksPage() {
  return render(
    <MemoryRouter>
      <TasksPage />
    </MemoryRouter>,
  );
}

/**
 * Opens an Ant Design Select dropdown and selects an option by its visible text.
 * Uses fireEvent (not userEvent) because rc-select's pointer handling does not
 * play well with userEvent's synthetic pointer model in jsdom.
 */
async function selectAntOption(labelText: string, optionText: string) {
  // Use waitFor so that async-loaded selects (e.g. Phase after project onChange) are retried
  const input = await waitFor(() => screen.getByLabelText(labelText), { timeout: 6000 });
  // mouseDown opens the dropdown
  fireEvent.mouseDown(input);
  // Wait for options to render in the portal
  const option = await waitFor(() => {
    const items = document.querySelectorAll('.ant-select-item-option');
    const match = Array.from(items).find(
      (el) => el.textContent?.trim() === optionText,
    );
    if (!match) throw new Error(`Option "${optionText}" not found for label "${labelText}"`);
    return match;
  });
  fireEvent.click(option);
  // Wait until the selected label appears in the Select's display area,
  // confirming the form value was updated before continuing
  await waitFor(() => {
    const selectEl = input.closest('.ant-select');
    const selectedItem = selectEl?.querySelector('.ant-select-selection-item');
    if (!selectedItem || selectedItem.textContent?.trim() !== optionText) {
      throw new Error(`Selection "${optionText}" not confirmed for "${labelText}"`);
    }
  });
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------
describe('TasksPage', () => {
  it('displays task rows returned by the API', async () => {
    renderTasksPage();

    await waitFor(() => {
      expect(screen.getByText(mockTaskSummary.title)).toBeInTheDocument();
    });

    expect(screen.getByText(mockProject.name)).toBeInTheDocument();
    expect(screen.getByText(mockUser.name)).toBeInTheDocument();
    // Status tag (rendered using i18n translation)
    expect(screen.getByText('To Do')).toBeInTheDocument();
  });

  it('shows an error alert when the tasks API call fails', async () => {
    server.use(
      http.get('*/api/v1/tasks', () => {
        return HttpResponse.json({ message: 'Service unavailable' }, { status: 503 });
      }),
    );

    renderTasksPage();

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });

    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('Failed to load tasks');
    expect(alert).toHaveTextContent('503');
  });

  it('opens the Create Task modal when "New Task" button is clicked', async () => {
    const user = userEvent.setup();
    renderTasksPage();

    // Wait for the page to finish loading
    await waitFor(() => screen.getByText(mockTaskSummary.title));

    await user.click(screen.getByRole('button', { name: /new task/i }));

    // Modal title appears
    await waitFor(() => {
      expect(screen.getByText('Create Task')).toBeInTheDocument();
    });
  });

  it('shows validation errors when the form is submitted empty', async () => {
    const user = userEvent.setup();

    // Override to return multiple projects so neither is auto-selected
    server.use(
      http.get('*/api/v1/projects', () =>
        HttpResponse.json([mockProject, { id: 'proj-2', name: 'Beta Project', description: '' }]),
      ),
    );

    renderTasksPage();

    await waitFor(() => screen.getByText(mockTaskSummary.title));

    // Open modal
    await user.click(screen.getByRole('button', { name: /new task/i }));
    await waitFor(() => screen.getByText('Create Task'));

    // Click Create without filling anything
    const createButton = screen.getByRole('button', { name: /^create$/i });
    await user.click(createButton);

    await waitFor(() => {
      expect(screen.getByText('Title is required')).toBeInTheDocument();
    });

    expect(screen.getByText('Project is required')).toBeInTheDocument();
    expect(screen.getByText('User is required')).toBeInTheDocument();
  }, 15000);

  it('renders all form fields inside the Create Task modal', async () => {
    const user = userEvent.setup();
    renderTasksPage();

    await waitFor(() => screen.getByText(mockTaskSummary.title));

    await user.click(screen.getByRole('button', { name: /new task/i }));

    await waitFor(() => {
      expect(screen.getByText('Create Task')).toBeInTheDocument();
    });

    expect(screen.getByLabelText('Title')).toBeInTheDocument();
    expect(screen.getByLabelText('Description')).toBeInTheDocument();
    expect(screen.getByLabelText('Status')).toBeInTheDocument();
    expect(screen.getByLabelText('Project')).toBeInTheDocument();
    expect(screen.getByLabelText('Assigned to')).toBeInTheDocument();
  });

  it('calls POST /api/v1/tasks with form values on submit', async () => {
    const user = userEvent.setup();

    // Use 2 projects so neither is auto-selected — test controls all inputs explicitly
    server.use(
      http.get('*/api/v1/projects', () =>
        HttpResponse.json([mockProject, { ...mockProject, id: 'proj-2', name: 'Beta Project' }]),
      ),
    );

    let postBody: Record<string, unknown> | null = null;
    server.use(
      http.post('*/api/v1/tasks', async ({ request }) => {
        postBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ ...mockTaskSummary, id: 'task-new', title: postBody.title as string }, { status: 201 });
      }),
    );

    renderTasksPage();
    await waitFor(() => screen.getByText(mockTaskSummary.title));

    await user.click(screen.getByRole('button', { name: /new task/i }));
    await waitFor(() => screen.getByLabelText('Title'));

    await user.type(screen.getByLabelText('Title'), 'My New Task');

    // Select project — triggers phase loading via onChange
    await selectAntOption('Project', mockProject.name);
    // Phase options load asynchronously after project selection; retry until available
    await selectAntOption('Phase', mockPhase.name);
    await selectAntOption('Assigned to', mockUser.name);

    fireEvent.click(screen.getByRole('button', { name: /^create$/i }));

    await waitFor(
      () => {
        expect(postBody).not.toBeNull();
        expect(postBody!.title).toBe('My New Task');
        expect(postBody!.projectId).toBe(mockProject.id);
        expect(postBody!.assignedUserId).toBe(mockUser.id);
        expect(postBody!.phaseId).toBe(mockPhase.id);
      },
      { timeout: 8000 },
    );
  }, 25000);

  it('renders the page heading "Tasks"', async () => {
    renderTasksPage();

    await waitFor(() => screen.getByText(mockTaskSummary.title));

    expect(screen.getByRole('heading', { name: /^tasks$/i })).toBeInTheDocument();
  });

  it('auto-selects project and user when only one option exists', async () => {
    const user = userEvent.setup();
    renderTasksPage();

    await waitFor(() => screen.getByText(mockTaskSummary.title));

    // Default handlers return exactly one project and one user
    await user.click(screen.getByRole('button', { name: /new task/i }));
    await waitFor(() => screen.getByText('Create Task'));

    // Both selects should be pre-filled
    await waitFor(() => {
      const projectSelect = screen.getByLabelText('Project').closest('.ant-select');
      expect(projectSelect?.querySelector('.ant-select-selection-item')?.textContent?.trim()).toBe(mockProject.name);
    });

    const userSelect = screen.getByLabelText('Assigned to').closest('.ant-select');
    // Two users in default handlers — should NOT be auto-selected
    expect(userSelect?.querySelector('.ant-select-selection-item')).toBeNull();
  });

  it('opens the Edit Task modal pre-filled when "Edit" is clicked', async () => {
    const user = userEvent.setup();
    renderTasksPage();

    await waitFor(() => screen.getByText(mockTaskSummary.title));

    await user.click(screen.getByRole('button', { name: /^edit$/i }));

    await waitFor(() => {
      expect(screen.getByText('Edit Task')).toBeInTheDocument();
    });

    // Title field should be pre-filled with the task's title
    expect((screen.getByLabelText('Title') as HTMLInputElement).value).toBe(mockTaskSummary.title);
  });

  it('calls PUT /api/v1/tasks/:id when edit form is saved', async () => {
    const user = userEvent.setup();

    let putBody: Record<string, unknown> | null = null;
    server.use(
      http.put('*/api/v1/tasks/:id', async ({ request }) => {
        putBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ ...mockTaskSummary, title: putBody.title as string });
      }),
    );

    renderTasksPage();
    await waitFor(() => screen.getByText(mockTaskSummary.title));

    await user.click(screen.getByRole('button', { name: /^edit$/i }));
    await waitFor(() => screen.getByText('Edit Task'));

    // Clear and retype the title
    const titleInput = screen.getByLabelText('Title');
    await user.clear(titleInput);
    await user.type(titleInput, 'Updated Title');

    fireEvent.click(screen.getByRole('button', { name: /^save$/i }));

    await waitFor(
      () => {
        expect(putBody).not.toBeNull();
        expect(putBody!.title).toBe('Updated Title');
      },
      { timeout: 8000 },
    );
  }, 15000);

  it('calls DELETE /api/v1/tasks/:id when delete is confirmed', async () => {
    let deletedId: string | null = null;
    server.use(
      http.delete('*/api/v1/tasks/:id', ({ params }) => {
        deletedId = params.id as string;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderTasksPage();
    await waitFor(() => screen.getByText(mockTaskSummary.title));

    // Click the trigger Delete button to open the popconfirm
    const [triggerButton] = screen.getAllByRole('button', { name: /^delete$/i });
    fireEvent.click(triggerButton);

    // After popconfirm opens a second Delete (confirm) button appears — click it
    const confirmButton = await waitFor(() => {
      const buttons = screen.getAllByRole('button', { name: /^delete$/i });
      if (buttons.length < 2) throw new Error('Popconfirm not yet visible');
      return buttons[buttons.length - 1];
    });
    fireEvent.click(confirmButton);

    await waitFor(() => {
      expect(deletedId).toBe(mockTaskSummary.id);
    });
  }, 15000);

  it('renders a "View" button for each task row', async () => {
    renderTasksPage();

    await waitFor(() => screen.getByText(mockTaskSummary.title));

    // Each task row has a View button that triggers navigation to the detail page
    expect(screen.getByRole('button', { name: /^view$/i })).toBeInTheDocument();
  });
});
