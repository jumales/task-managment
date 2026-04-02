import { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Input, Tabs, Table, Tag, Typography, Space, Empty } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { SearchOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { searchTasks, searchUsers } from '../api/searchApi';
import type { TaskDocument, UserDocument } from '../api/types';

const { Title, Text } = Typography;

const STATUS_COLORS: Record<string, string> = {
  TODO:        'default',
  IN_PROGRESS: 'processing',
  DONE:        'success',
};

/** Full-text search page backed by Elasticsearch via search-service. */
export function SearchPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const initialQuery = searchParams.get('q') ?? '';
  const [query,    setQuery]    = useState(initialQuery);
  const [tasks,    setTasks]    = useState<TaskDocument[]>([]);
  const [users,    setUsers]    = useState<UserDocument[]>([]);
  const [loading,  setLoading]  = useState(false);
  const [searched, setSearched] = useState(initialQuery.length > 0);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const taskColumns: ColumnsType<TaskDocument> = useMemo(() => [
    {
      title: t('search.title_col'),
      dataIndex: 'title',
      key: 'title',
      render: (title: string) => <Text strong>{title}</Text>,
    },
    {
      title: t('search.description'),
      dataIndex: 'description',
      key: 'description',
      render: (desc: string | null) =>
        desc ? (
          <Text type="secondary" ellipsis style={{ maxWidth: 320, display: 'block' }}>
            {desc}
          </Text>
        ) : (
          <Text type="secondary">—</Text>
        ),
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      width: 130,
      render: (status: string | null) =>
        status ? (
          <Tag color={STATUS_COLORS[status] ?? 'default'}>
            {status.replace('_', ' ')}
          </Tag>
        ) : null,
    },
    {
      title: t('search.projectName'),
      dataIndex: 'projectName',
      key: 'projectName',
      render: (name: string | null) => name ?? <Text type="secondary">—</Text>,
    },
    {
      title: t('search.assignedTo'),
      dataIndex: 'assignedUserName',
      key: 'assignedUserName',
      render: (name: string | null) => name ?? <Text type="secondary">—</Text>,
    },
  ], [t]);

  const userColumns: ColumnsType<UserDocument> = useMemo(() => [
    {
      title: t('common.name'),
      dataIndex: 'name',
      key: 'name',
      render: (name: string) => <Text strong>{name}</Text>,
    },
    {
      title: t('common.email'),
      dataIndex: 'email',
      key: 'email',
    },
    {
      title: t('common.username'),
      dataIndex: 'username',
      key: 'username',
      render: (username: string) => <Text code>{username}</Text>,
    },
    {
      title: t('common.status'),
      dataIndex: 'active',
      key: 'active',
      width: 90,
      render: (active: boolean) => (
        <Tag color={active ? 'success' : 'default'}>{active ? t('common.active') : t('common.inactive')}</Tag>
      ),
    },
  ], [t]);

  const onTaskRow = useCallback((record: TaskDocument) => ({
    onClick: () => navigate(`/tasks?highlight=${record.id}`),
    style: { cursor: 'pointer' },
  }), [navigate]);

  const onUserRow = useCallback((record: UserDocument) => ({
    onClick: () => navigate(`/users?highlight=${record.id}`),
    style: { cursor: 'pointer' },
  }), [navigate]);

  // Run search whenever query changes, debounced 350ms
  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);

    const trimmed = query.trim();
    if (!trimmed) {
      setTasks([]);
      setUsers([]);
      setSearched(false);
      setSearchParams({}, { replace: true });
      return;
    }

    debounceRef.current = setTimeout(() => {
      setLoading(true);
      setSearched(true);
      setSearchParams({ q: trimmed }, { replace: true });
      Promise.all([searchTasks(trimmed), searchUsers(trimmed)])
        .then(([ts, us]) => { setTasks(ts); setUsers(us); })
        .catch(() => { setTasks([]); setUsers([]); })
        .finally(() => setLoading(false));
    }, 350);

    return () => { if (debounceRef.current) clearTimeout(debounceRef.current); };
  }, [query]);

  // Kick off initial search if query came from URL param
  useEffect(() => {
    if (initialQuery) {
      setLoading(true);
      setSearched(true);
      Promise.all([searchTasks(initialQuery), searchUsers(initialQuery)])
        .then(([ts, us]) => { setTasks(ts); setUsers(us); })
        .catch(() => {})
        .finally(() => setLoading(false));
    }
  }, []);

  const tabItems = [
    {
      key: 'tasks',
      label: `${t('nav.tasks')}${searched ? ` (${tasks.length})` : ''}`,
      children: (
        <Table<TaskDocument>
          columns={taskColumns}
          dataSource={tasks}
          rowKey="id"
          loading={loading}
          locale={{ emptyText: searched ? <Empty description={t('search.noTasksFound')} /> : <Empty description={t('search.enterQuery')} /> }}
          onRow={onTaskRow}
          pagination={false}
          size="middle"
        />
      ),
    },
    {
      key: 'users',
      label: `${t('nav.users')}${searched ? ` (${users.length})` : ''}`,
      children: (
        <Table<UserDocument>
          columns={userColumns}
          dataSource={users}
          rowKey="id"
          loading={loading}
          locale={{ emptyText: searched ? <Empty description={t('search.noUsersFound')} /> : <Empty description={t('search.enterQuery')} /> }}
          onRow={onUserRow}
          pagination={false}
          size="middle"
        />
      ),
    },
  ];

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Title level={3} style={{ marginBottom: 0 }}>{t('search.title')}</Title>

      <Input
        size="large"
        placeholder={t('search.placeholder')}
        prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        allowClear
        autoFocus
        style={{ maxWidth: 560 }}
      />

      <Tabs defaultActiveKey="tasks" items={tabItems} />
    </Space>
  );
}
