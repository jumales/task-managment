import { jsx as _jsx } from "react/jsx-runtime";
import { describe, it, expect, beforeAll, afterEach, afterAll } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { server } from '../test/mocks/server';
import { mockUser, mockUser2 } from '../test/mocks/handlers';
import { UsersPage } from './UsersPage';
import { AuthProvider } from '../auth/AuthProvider';
// ---------------------------------------------------------------------------
// MSW lifecycle
// ---------------------------------------------------------------------------
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
// ---------------------------------------------------------------------------
// Helper: render UsersPage with auth context and router
// ---------------------------------------------------------------------------
function renderUsersPage() {
    return render(_jsx(MemoryRouter, { children: _jsx(AuthProvider, { children: _jsx(UsersPage, {}) }) }));
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
        server.use(http.get('*/api/v1/users', () => {
            return HttpResponse.json({ message: 'Internal Server Error' }, { status: 500 });
        }));
        renderUsersPage();
        await waitFor(() => {
            expect(screen.getByRole('alert')).toBeInTheDocument();
            expect(screen.getByText('Failed to load users.')).toBeInTheDocument();
        });
    });
    it('shows "New User" button and Edit buttons for admin users', async () => {
        renderUsersPage();
        await waitFor(() => screen.getByText(mockUser.name));
        expect(screen.getByRole('button', { name: /new user/i })).toBeInTheDocument();
        // One Edit button per user row
        expect(screen.getAllByRole('button', { name: /^edit$/i })).toHaveLength(2);
    });
    it('opens the New User modal when "New User" is clicked', async () => {
        const user = userEvent.setup();
        renderUsersPage();
        await waitFor(() => screen.getByText(mockUser.name));
        await user.click(screen.getByRole('button', { name: /new user/i }));
        // Modal is open when its form fields appear
        await waitFor(() => {
            expect(screen.getByLabelText('Name')).toBeInTheDocument();
        });
        expect(screen.getByLabelText('Email')).toBeInTheDocument();
        expect(screen.getByLabelText('Username')).toBeInTheDocument();
    });
    it('shows email validation error for invalid email', async () => {
        const user = userEvent.setup();
        renderUsersPage();
        await waitFor(() => screen.getByText(mockUser.name));
        await user.click(screen.getByRole('button', { name: /new user/i }));
        await waitFor(() => screen.getByLabelText('Email'));
        await user.type(screen.getByLabelText('Name'), 'Test User');
        await user.type(screen.getByLabelText('Email'), 'not-an-email');
        fireEvent.click(screen.getByRole('button', { name: /^create$/i }));
        await waitFor(() => { expect(screen.getByText('Must be a valid email address')).toBeInTheDocument(); }, { timeout: 8000 });
    }, 15000);
    it('calls POST /api/v1/users with form values on submit', async () => {
        const user = userEvent.setup();
        let postBody = null;
        server.use(http.post('*/api/v1/users', async ({ request }) => {
            postBody = (await request.json());
            return HttpResponse.json({ ...mockUser, id: 'user-new', name: postBody.name, email: postBody.email }, { status: 201 });
        }));
        renderUsersPage();
        await waitFor(() => screen.getByText(mockUser.name));
        await user.click(screen.getByRole('button', { name: /new user/i }));
        await waitFor(() => screen.getByLabelText('Name'));
        await user.type(screen.getByLabelText('Name'), 'Charlie Brown');
        await user.type(screen.getByLabelText('Email'), 'charlie@example.com');
        fireEvent.click(screen.getByRole('button', { name: /^create$/i }));
        await waitFor(() => {
            expect(postBody).not.toBeNull();
            expect(postBody.name).toBe('Charlie Brown');
            expect(postBody.email).toBe('charlie@example.com');
        }, { timeout: 8000 });
    }, 15000);
    it('opens the Edit User modal pre-filled when "Edit" is clicked', async () => {
        const user = userEvent.setup();
        renderUsersPage();
        await waitFor(() => screen.getByText(mockUser.name));
        const [firstEditBtn] = screen.getAllByRole('button', { name: /^edit$/i });
        await user.click(firstEditBtn);
        await waitFor(() => {
            expect(screen.getByText('Edit User')).toBeInTheDocument();
        });
        expect(screen.getByLabelText('Name').value).toBe(mockUser.name);
        expect(screen.getByLabelText('Email').value).toBe(mockUser.email);
    });
    it('calls PUT /api/v1/users/:id when edit form is saved', async () => {
        const user = userEvent.setup();
        let putBody = null;
        server.use(http.put('*/api/v1/users/:id', async ({ request }) => {
            putBody = (await request.json());
            return HttpResponse.json({ ...mockUser, name: putBody.name });
        }));
        renderUsersPage();
        await waitFor(() => screen.getByText(mockUser.name));
        const [firstEditBtn] = screen.getAllByRole('button', { name: /^edit$/i });
        await user.click(firstEditBtn);
        await waitFor(() => screen.getByText('Edit User'));
        const nameInput = screen.getByLabelText('Name');
        await user.clear(nameInput);
        await user.type(nameInput, 'Alice Updated');
        fireEvent.click(screen.getByRole('button', { name: /^save$/i }));
        await waitFor(() => {
            expect(putBody).not.toBeNull();
            expect(putBody.name).toBe('Alice Updated');
        }, { timeout: 8000 });
    }, 15000);
});
