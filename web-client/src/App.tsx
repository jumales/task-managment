import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AppLayout } from './components/AppLayout';
import { DashboardPage } from './pages/DashboardPage';
import { TasksPage } from './pages/TasksPage';
import { UsersPage } from './pages/UsersPage';
import { ProjectsPage } from './pages/ProjectsPage';
import { SearchPage }   from './pages/SearchPage';

/** Root router — all routes are protected by AuthProvider in main.tsx. */
export default function App() {
  return (
    <BrowserRouter>
      <AppLayout>
        <Routes>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/tasks"     element={<TasksPage />} />
          <Route path="/projects"  element={<ProjectsPage />} />
          <Route path="/users"     element={<UsersPage />} />
          <Route path="/search"    element={<SearchPage />} />
          <Route path="*"          element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </AppLayout>
    </BrowserRouter>
  );
}
