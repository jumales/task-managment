import { describe, it, expect, beforeAll, afterEach, afterAll } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';

import { server } from '../test/mocks/server';
import { mockProject } from '../test/mocks/handlers';
import { ProjectsPage } from './ProjectsPage';
import { AuthProvider } from '../auth/AuthProvider';

// ---------------------------------------------------------------------------
// MSW lifecycle
// ---------------------------------------------------------------------------
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

// ---------------------------------------------------------------------------
// Helper: render ProjectsPage with auth context and router
// ---------------------------------------------------------------------------
function renderProjectsPage() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <ProjectsPage />
      </AuthProvider>
    </MemoryRouter>,
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------
describe('ProjectsPage', () => {
  it('displays project rows returned by the API', async () => {
    renderProjectsPage();

    await waitFor(() => {
      expect(screen.getByText(mockProject.name)).toBeInTheDocument();
    });

    expect(screen.getByText(mockProject.description)).toBeInTheDocument();
  });

  it('renders the page heading "Projects"', async () => {
    renderProjectsPage();

    await waitFor(() => screen.getByText(mockProject.name));

    expect(screen.getByRole('heading', { name: /^projects$/i })).toBeInTheDocument();
  });

  it('shows an error alert when the projects API call fails', async () => {
    server.use(
      http.get('*/api/v1/projects', () => {
        return HttpResponse.json({ message: 'Service unavailable' }, { status: 503 });
      }),
    );

    renderProjectsPage();

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });

    expect(screen.getByRole('alert')).toHaveTextContent('Failed to load projects.');
  });

  it('opens the Create Project modal when "New Project" button is clicked', async () => {
    const user = userEvent.setup();
    renderProjectsPage();

    await waitFor(() => screen.getByText(mockProject.name));

    await user.click(screen.getByRole('button', { name: /new project/i }));

    await waitFor(() => {
      expect(screen.getByText('Create Project')).toBeInTheDocument();
    });
  });

  it('shows validation error when form is submitted without a name', async () => {
    const user = userEvent.setup();
    renderProjectsPage();

    await waitFor(() => screen.getByText(mockProject.name));

    await user.click(screen.getByRole('button', { name: /new project/i }));
    await waitFor(() => screen.getByText('Create Project'));

    fireEvent.click(screen.getByRole('button', { name: /^create$/i }));

    await waitFor(() => {
      expect(screen.getByText('Name is required')).toBeInTheDocument();
    });
  });

  it('calls POST /api/v1/projects with form values on submit', async () => {
    const user = userEvent.setup();

    let postBody: Record<string, unknown> | null = null;
    server.use(
      http.post('*/api/v1/projects', async ({ request }) => {
        postBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(
          { id: 'proj-new', name: postBody.name as string, description: (postBody.description as string) ?? '' },
          { status: 201 },
        );
      }),
    );

    renderProjectsPage();
    await waitFor(() => screen.getByText(mockProject.name));

    await user.click(screen.getByRole('button', { name: /new project/i }));
    await waitFor(() => screen.getByLabelText('Name'));

    await user.type(screen.getByLabelText('Name'), 'Beta Project');
    await user.type(screen.getByLabelText('Description'), 'Second project');

    fireEvent.click(screen.getByRole('button', { name: /^create$/i }));

    await waitFor(
      () => {
        expect(postBody).not.toBeNull();
        expect(postBody!.name).toBe('Beta Project');
        expect(postBody!.description).toBe('Second project');
      },
      { timeout: 8000 },
    );
  }, 15000);

  it('calls DELETE /api/v1/projects/:id when delete is confirmed', async () => {
    let deletedId: string | null = null;
    server.use(
      http.delete('*/api/v1/projects/:id', ({ params }) => {
        deletedId = params.id as string;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderProjectsPage();
    await waitFor(() => screen.getByText(mockProject.name));

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
      expect(deletedId).toBe(mockProject.id);
    });
  });
});
