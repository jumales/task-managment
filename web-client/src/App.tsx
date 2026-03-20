import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AppLayout } from './components/AppLayout';
import { TasksPage } from './pages/TasksPage';
import { UsersPage } from './pages/UsersPage';

/** Root router — all routes are protected by AuthProvider in main.tsx. */
export default function App() {
  return (
    <BrowserRouter>
      <AppLayout>
        <Routes>
          <Route path="/tasks" element={<TasksPage />} />
          <Route path="/users" element={<UsersPage />} />
          <Route path="*"     element={<Navigate to="/tasks" replace />} />
        </Routes>
      </AppLayout>
    </BrowserRouter>
  );
}
