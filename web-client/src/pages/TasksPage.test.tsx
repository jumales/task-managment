import { describe, it, expect, beforeAll, afterEach, afterAll } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';

import { server } from '../test/mocks/server';
import { mockTask, mockProject, mockUser } from '../test/mocks/handlers';
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
  const input = screen.getByLabelText(labelText);
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
      expect(screen.getByText(mockTask.title)).toBeInTheDocument();
    });

    expect(screen.getByText(mockProject.name)).toBeInTheDocument();
    expect(screen.getByText(mockUser.name)).toBeInTheDocument();
    // Status tag
    expect(screen.getByText('TODO')).toBeInTheDocument();
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
    await waitFor(() => screen.getByText(mockTask.title));

    await user.click(screen.getByRole('button', { name: /new task/i }));

    // Modal title appears
    await waitFor(() => {
      expect(screen.getByText('Create Task')).toBeInTheDocument();
    });
  });

  it('shows validation errors when the form is submitted empty', async () => {
    const user = userEvent.setup();
    renderTasksPage();

    await waitFor(() => screen.getByText(mockTask.title));

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
  });

  it('renders all form fields inside the Create Task modal', async () => {
    const user = userEvent.setup();
    renderTasksPage();

    await waitFor(() => screen.getByText(mockTask.title));

    await user.click(screen.getByRole('button', { name: /new task/i }));

    await waitFor(() => {
      expect(screen.getByText('Create Task')).toBeInTheDocument();
    });

    expect(screen.getByLabelText('Title')).toBeInTheDocument();
    expect(screen.getByLabelText('Description')).toBeInTheDocument();
    expect(screen.getByLabelText('Status')).toBeInTheDocument();
    expect(screen.getByLabelText('Project')).toBeInTheDocument();
    expect(screen.getByLabelText('Assign to')).toBeInTheDocument();
  });

  it('calls POST /api/v1/tasks with form values on submit', async () => {
    const user = userEvent.setup();

    let postBody: Record<string, unknown> | null = null;
    server.use(
      http.post('*/api/v1/tasks', async ({ request }) => {
        postBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(
          {
            id: 'task-new',
            title: postBody.title,
            description: '',
            status: 'TODO',
            assignedUser: { id: mockUser.id, name: mockUser.name, email: mockUser.email },
            project: mockProject,
            phase: null,
            comments: [],
          },
          { status: 201 },
        );
      }),
    );

    renderTasksPage();
    await waitFor(() => screen.getByText(mockTask.title));

    await user.click(screen.getByRole('button', { name: /new task/i }));
    await waitFor(() => screen.getByLabelText('Title'));

    await user.type(screen.getByLabelText('Title'), 'My New Task');

    // Select Project and Assignee using Ant Design Select helper
    await selectAntOption('Project', mockProject.name);
    await selectAntOption('Assign to', mockUser.name);

    fireEvent.click(screen.getByRole('button', { name: /^create$/i }));

    await waitFor(
      () => {
        expect(postBody).not.toBeNull();
        expect(postBody!.title).toBe('My New Task');
        expect(postBody!.projectId).toBe(mockProject.id);
        expect(postBody!.assignedUserId).toBe(mockUser.id);
      },
      { timeout: 8000 },
    );
  }, 15000);

  it('renders the page heading "Tasks"', async () => {
    renderTasksPage();

    await waitFor(() => screen.getByText(mockTask.title));

    expect(screen.getByRole('heading', { name: /^tasks$/i })).toBeInTheDocument();
  });
});
