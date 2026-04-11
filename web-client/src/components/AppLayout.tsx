import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAvatarBlobUrl } from '../hooks/useAvatarBlobUrl';
import { Layout, Menu, Button, Typography, Avatar, Space, Select } from 'antd';
import {
  DashboardOutlined,
  CheckSquareOutlined,
  ProjectOutlined,
  TeamOutlined,
  LogoutOutlined,
  UserOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SettingOutlined,
  GlobalOutlined,
  BarChartOutlined,
} from '@ant-design/icons';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { getMe, updateUserLanguage } from '../api/userApi';
import i18n from '../i18n';

const { Sider, Header, Content } = Layout;

const LANGUAGE_OPTIONS = [
  { value: 'en', label: 'English' },
  { value: 'hr', label: 'Hrvatski' },
];

/** Main application shell with a collapsible sidebar and top header. */
export function AppLayout({ children }: { children: React.ReactNode }) {
  const { t } = useTranslation();
  const navigate  = useNavigate();
  const location  = useLocation();
  const { name, logout } = useAuth();

  const [collapsed,    setCollapsed]    = useState(false);
  const [avatarFileId, setAvatarFileId] = useState<string | null>(null);
  const [currentUserId, setCurrentUserId] = useState<string | null>(null);
  const [language, setLanguage] = useState<string>(i18n.language);
  const avatarUrl = useAvatarBlobUrl(avatarFileId);

  // Look up the current user's profile (avatar + language) from the user-service
  useEffect(() => {
    getMe()
      .then((me) => {
        setAvatarFileId(me.avatarFileId ?? null);
        setCurrentUserId(me.id);
        // Apply the language stored in the backend, persisting it locally as well
        const lang = me.language ?? 'en';
        setLanguage(lang);
        i18n.changeLanguage(lang);
        localStorage.setItem('language', lang);
      })
      .catch(() => {});
  }, []);

  /** Switches the UI language, persists to localStorage and to the backend. */
  function handleLanguageChange(lang: string) {
    setLanguage(lang);
    i18n.changeLanguage(lang);
    localStorage.setItem('language', lang);
    if (currentUserId) {
      updateUserLanguage(currentUserId, lang).catch(() => {});
    }
  }

  const navItems = useMemo(() => [
    { key: '/dashboard',     label: t('nav.dashboard'),     icon: <DashboardOutlined /> },
    { key: '/tasks',         label: t('nav.tasks'),         icon: <CheckSquareOutlined /> },
    { key: '/projects',      label: t('nav.projects'),      icon: <ProjectOutlined /> },
    { key: '/users',         label: t('nav.users'),         icon: <TeamOutlined /> },
    { key: '/reports',       label: t('nav.reports'),       icon: <BarChartOutlined /> },
    { key: '/configuration', label: t('nav.configuration'), icon: <SettingOutlined /> },
  ], [t]);

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
              {t('nav.appName')}
            </Typography.Text>
          )}
        </div>

        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={navItems}
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
            <GlobalOutlined style={{ color: '#888' }} />
            <Select
              value={language}
              onChange={handleLanguageChange}
              options={LANGUAGE_OPTIONS}
              size="small"
              style={{ width: 110 }}
              variant="borderless"
              aria-label="Language"
            />
            {avatarUrl
              ? <Avatar src={avatarUrl} size="small" />
              : <Avatar icon={<UserOutlined />} size="small" />}
            <Typography.Text>{name}</Typography.Text>
            <Button icon={<LogoutOutlined />} onClick={logout} size="small">
              {t('common.logout')}
            </Button>
          </Space>
        </Header>

        <Content style={{ margin: 24, minHeight: 0 }}>
          {children}
        </Content>
      </Layout>
    </Layout>
  );
}
