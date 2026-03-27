import { useState, useEffect, useRef } from 'react';
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

const TASK_COLUMNS: ColumnsType<TaskDocument> = [
  {
    title: 'Title',
    dataIndex: 'title',
    key: 'title',
    render: (title: string) => <Text strong>{title}</Text>,
  },
  {
    title: 'Description',
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
    title: 'Status',
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
    title: 'Project',
    dataIndex: 'projectName',
    key: 'projectName',
    render: (name: string | null) => name ?? <Text type="secondary">—</Text>,
  },
  {
    title: 'Assigned to',
    dataIndex: 'assignedUserName',
    key: 'assignedUserName',
    render: (name: string | null) => name ?? <Text type="secondary">—</Text>,
  },
];

const USER_COLUMNS: ColumnsType<UserDocument> = [
  {
    title: 'Name',
    dataIndex: 'name',
    key: 'name',
    render: (name: string) => <Text strong>{name}</Text>,
  },
  {
    title: 'Email',
    dataIndex: 'email',
    key: 'email',
  },
  {
    title: 'Username',
    dataIndex: 'username',
    key: 'username',
    render: (username: string) => <Text code>{username}</Text>,
  },
  {
    title: 'Status',
    dataIndex: 'active',
    key: 'active',
    width: 90,
    render: (active: boolean) => (
      <Tag color={active ? 'success' : 'default'}>{active ? 'Active' : 'Inactive'}</Tag>
    ),
  },
];

/** Full-text search page backed by Elasticsearch via search-service. */
export function SearchPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const initialQuery = searchParams.get('q') ?? '';
  const [query,     setQuery]     = useState(initialQuery);
  const [tasks,     setTasks]     = useState<TaskDocument[]>([]);
  const [users,     setUsers]     = useState<UserDocument[]>([]);
  const [loading,   setLoading]   = useState(false);
  const [searched,  setSearched]  = useState(initialQuery.length > 0);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

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
        .then(([t, u]) => { setTasks(t); setUsers(u); })
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
        .then(([t, u]) => { setTasks(t); setUsers(u); })
        .catch(() => {})
        .finally(() => setLoading(false));
    }
  }, []);

  const tabItems = [
    {
      key: 'tasks',
      label: `Tasks${searched ? ` (${tasks.length})` : ''}`,
      children: (
        <Table<TaskDocument>
          columns={TASK_COLUMNS}
          dataSource={tasks}
          rowKey="id"
          loading={loading}
          locale={{ emptyText: searched ? <Empty description="No tasks found" /> : <Empty description="Enter a search query" /> }}
          onRow={(record) => ({
            onClick: () => navigate(`/tasks?highlight=${record.id}`),
            style: { cursor: 'pointer' },
          })}
          pagination={false}
          size="middle"
        />
      ),
    },
    {
      key: 'users',
      label: `Users${searched ? ` (${users.length})` : ''}`,
      children: (
        <Table<UserDocument>
          columns={USER_COLUMNS}
          dataSource={users}
          rowKey="id"
          loading={loading}
          locale={{ emptyText: searched ? <Empty description="No users found" /> : <Empty description="Enter a search query" /> }}
          onRow={(record) => ({
            onClick: () => navigate(`/users?highlight=${record.id}`),
            style: { cursor: 'pointer' },
          })}
          pagination={false}
          size="middle"
        />
      ),
    },
  ];

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Title level={3} style={{ marginBottom: 0 }}>Search</Title>

      <Input
        size="large"
        placeholder="Search tasks and users…"
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
