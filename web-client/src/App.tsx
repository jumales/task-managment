import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ConfigProvider, Spin } from 'antd';
import { AppLayout } from './components/AppLayout';

// Route-level code splitting — each page is loaded only when first navigated to
const DashboardPage     = lazy(() => import('./pages/DashboardPage').then(m => ({ default: m.DashboardPage })));
const TasksPage         = lazy(() => import('./pages/TasksPage').then(m => ({ default: m.TasksPage })));
const TaskDetailPage    = lazy(() => import('./pages/TaskDetailPage').then(m => ({ default: m.TaskDetailPage })));
const UsersPage         = lazy(() => import('./pages/UsersPage').then(m => ({ default: m.UsersPage })));
const ProjectsPage      = lazy(() => import('./pages/ProjectsPage').then(m => ({ default: m.ProjectsPage })));
const ConfigurationPage = lazy(() => import('./pages/ConfigurationPage').then(m => ({ default: m.ConfigurationPage })));
const ReportsPage       = lazy(() => import('./pages/ReportsPage').then(m => ({ default: m.ReportsPage })));

/** Root router — all routes are protected by AuthProvider in main.tsx. */
export default function App() {
  return (
    <ConfigProvider
      theme={{
        components: {
          // Raise inactive menu item text contrast from 0.65 to 0.85 opacity (WCAG AA)
          Menu: {
            darkItemColor: 'rgba(255,255,255,0.85)',
          },
        },
      }}
    >
      <BrowserRouter>
        <AppLayout>
          <Suspense fallback={<Spin size="large" style={{ display: 'block', marginTop: 120, textAlign: 'center' }} />}>
            <Routes>
              <Route path="/dashboard"     element={<DashboardPage />} />
              <Route path="/tasks"         element={<TasksPage />} />
              <Route path="/tasks/:id"     element={<TaskDetailPage />} />
              <Route path="/projects"      element={<ProjectsPage />} />
              <Route path="/users"         element={<UsersPage />} />
              <Route path="/configuration" element={<ConfigurationPage />} />
              <Route path="/reports"       element={<ReportsPage />} />
              <Route path="*"              element={<Navigate to="/dashboard" replace />} />
            </Routes>
          </Suspense>
        </AppLayout>
      </BrowserRouter>
    </ConfigProvider>
  );
}
