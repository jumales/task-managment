import { Layout, Menu, Button, Typography } from 'antd';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';

const { Header, Content } = Layout;

const NAV_ITEMS = [
  { key: '/tasks',    label: 'Tasks' },
  { key: '/projects', label: 'Projects' },
  { key: '/users',    label: 'Users' },
];

/** Main application shell with top navigation and user logout. */
export function AppLayout({ children }: { children: React.ReactNode }) {
  const navigate  = useNavigate();
  const location  = useLocation();
  const { username, logout } = useAuth();

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', gap: 24 }}>
        <Typography.Text style={{ color: 'white', fontWeight: 'bold', fontSize: 16 }}>
          Task Management
        </Typography.Text>
        <Menu
          theme="dark"
          mode="horizontal"
          selectedKeys={[location.pathname]}
          items={NAV_ITEMS}
          onClick={({ key }) => navigate(key)}
          style={{ flex: 1 }}
        />
        <Typography.Text style={{ color: 'white' }}>{username}</Typography.Text>
        <Button onClick={logout} size="small">Logout</Button>
      </Header>
      <Content style={{ padding: 24 }}>{children}</Content>
    </Layout>
  );
}
