import { useEffect, useState } from 'react';
import { Table, Tag, Typography, Alert, Spin } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { getUsers } from '../api/userApi';
import type { UserResponse, RoleDto } from '../api/types';

const COLUMNS: ColumnsType<UserResponse> = [
  { title: 'Name',     dataIndex: 'name',     key: 'name' },
  { title: 'Username', dataIndex: 'username', key: 'username',
    render: (v: string | null) => v ?? '—',
  },
  { title: 'Email',    dataIndex: 'email',    key: 'email' },
  { title: 'Active',   dataIndex: 'active',   key: 'active',
    render: (v: boolean) => (
      <Tag color={v ? 'green' : 'red'}>{v ? 'Active' : 'Inactive'}</Tag>
    ),
  },
  { title: 'Roles',    dataIndex: 'roles',    key: 'roles',
    render: (roles: RoleDto[]) =>
      roles.map((r) => <Tag key={r.id}>{r.name}</Tag>),
  },
];

/** Displays all users and their assigned roles. */
export function UsersPage() {
  const [users,   setUsers]   = useState<UserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error,   setError]   = useState<string | null>(null);

  useEffect(() => {
    getUsers()
      .then(setUsers)
      .catch(() => setError('Failed to load users.'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <Spin />;
  if (error)   return <Alert type="error" message={error} />;

  return (
    <>
      <Typography.Title level={3}>Users</Typography.Title>
      <Table
        rowKey="id"
        dataSource={users}
        columns={COLUMNS}
        pagination={{ pageSize: 20 }}
      />
    </>
  );
}
