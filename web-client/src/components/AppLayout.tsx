import { useEffect, useState } from 'react';
import { useAvatarBlobUrl } from '../hooks/useAvatarBlobUrl';
import { Layout, Menu, Button, Typography, Avatar, Space } from 'antd';
import {
  DashboardOutlined,
  CheckSquareOutlined,
  ProjectOutlined,
  TeamOutlined,
  LogoutOutlined,
  UserOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { getUsers } from '../api/userApi';

const { Sider, Header, Content } = Layout;

const NAV_ITEMS = [
  { key: '/dashboard', label: 'Dashboard', icon: <DashboardOutlined /> },
  { key: '/tasks',     label: 'Tasks',     icon: <CheckSquareOutlined /> },
  { key: '/projects',  label: 'Projects',  icon: <ProjectOutlined /> },
  { key: '/users',     label: 'Users',     icon: <TeamOutlined /> },
];

/** Main application shell with a collapsible sidebar and top header. */
export function AppLayout({ children }: { children: React.ReactNode }) {
  const navigate  = useNavigate();
  const location  = useLocation();
  const { name, username, logout } = useAuth();

  const [collapsed,      setCollapsed]      = useState(false);
  const [avatarFileId,   setAvatarFileId]   = useState<string | null>(null);
  const avatarUrl = useAvatarBlobUrl(avatarFileId);

  // Look up the current user's avatarFileId from the user-service
  useEffect(() => {
    getUsers({ size: 100 })
      .then((page) => {
        const me = page.content.find((u) => u.username === username);
        setAvatarFileId(me?.avatarFileId ?? null);
      })
      .catch(() => {});
  }, [username]);

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        collapsible
        collapsed={collapsed}
        trigger={null}
        width={220}
        style={{ boxShadow: '2px 0 8px rgba(0,0,0,0.15)' }}
      >
        <div style={{
          height: 64,
          display: 'flex',
          alignItems: 'center',
          justifyContent: collapsed ? 'center' : 'flex-start',
          padding: collapsed ? 0 : '0 24px',
          overflow: 'hidden',
        }}>
          {!collapsed && (
            <Typography.Text style={{ color: 'white', fontWeight: 700, fontSize: 16, whiteSpace: 'nowrap' }}>
              Task Management
            </Typography.Text>
          )}
        </div>

        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={NAV_ITEMS}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>

      <Layout>
        <Header style={{
          padding: '0 24px',
          background: '#fff',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          boxShadow: '0 1px 4px rgba(0,0,0,0.1)',
        }}>
          <Button
            type="text"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
            style={{ fontSize: 18 }}
          />

          <Space>
            {avatarUrl
              ? <Avatar src={avatarUrl} size="small" />
              : <Avatar icon={<UserOutlined />} size="small" />}
            <Typography.Text>{name}</Typography.Text>
            <Button icon={<LogoutOutlined />} onClick={logout} size="small">Logout</Button>
          </Space>
        </Header>

        <Content style={{ margin: 24, minHeight: 0 }}>
          {children}
        </Content>
      </Layout>
    </Layout>
  );
}
