import { describe, it, expect, beforeAll, afterEach, afterAll } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';

import { server } from '../test/mocks/server';
import { mockUser, mockUser2 } from '../test/mocks/handlers';
import { UsersPage } from './UsersPage';

// ---------------------------------------------------------------------------
// MSW lifecycle
// ---------------------------------------------------------------------------
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

// ---------------------------------------------------------------------------
// Helper: render UsersPage inside a router (needed for any nav links)
// ---------------------------------------------------------------------------
function renderUsersPage() {
  return render(
    <MemoryRouter>
      <UsersPage />
    </MemoryRouter>,
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------
describe('UsersPage', () => {
  it('displays a row for each user returned by the API', async () => {
    renderUsersPage();

    // Both user names should appear in the table once loading is done
    await waitFor(() => {
      expect(screen.getByText(mockUser.name)).toBeInTheDocument();
      expect(screen.getByText(mockUser2.name)).toBeInTheDocument();
    });
  });

  it('displays the email address of each user', async () => {
    renderUsersPage();

    await waitFor(() => {
      expect(screen.getByText(mockUser.email)).toBeInTheDocument();
      expect(screen.getByText(mockUser2.email)).toBeInTheDocument();
    });
  });

  it('displays the role tags for each user', async () => {
    renderUsersPage();

    await waitFor(() => {
      // mockUser has role ADMIN, mockUser2 has role DEVELOPER
      expect(screen.getByText('ADMIN')).toBeInTheDocument();
      expect(screen.getByText('DEVELOPER')).toBeInTheDocument();
    });
  });

  it('shows an error alert when the API call fails', async () => {
    server.use(
      http.get('*/api/v1/users', () => {
        return HttpResponse.json({ message: 'Internal Server Error' }, { status: 500 });
      }),
    );

    renderUsersPage();

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
      expect(screen.getByText('Failed to load users.')).toBeInTheDocument();
    });
  });
});
