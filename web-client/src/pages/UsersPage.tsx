import { useCallback, useEffect, useRef, useState } from 'react';
import { Table, Tag, Typography, Alert, Spin, Button, Modal, Form, Input, Switch, Space, message } from 'antd';
import { UploadOutlined, SearchOutlined } from '@ant-design/icons';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { getUsers, createUser, updateUser, uploadAvatar, updateUserAvatar, downloadFile } from '../api/userApi';
import { searchUsers } from '../api/searchApi';
import { useAuth } from '../auth/AuthProvider';
import type { UserResponse, RoleDto } from '../api/types';

/** Upload button that triggers a hidden file input. */
function AvatarUploadButton({ user, onDone }: { user: UserResponse; onDone: (updated: UserResponse) => void }) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [loading, setLoading] = useState(false);

  async function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setLoading(true);
    try {
      const { fileId } = await uploadAvatar(file);
      const updated = await updateUserAvatar(user.id, fileId);
      onDone(updated);
      message.success('Avatar updated');
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number; data?: unknown } })?.response?.status;
      const data   = (err as { response?: { data?: unknown } })?.response?.data;
      message.error(`Upload failed (${status ?? 'network'}): ${JSON.stringify(data) ?? ''}`);
      console.error('Avatar upload error:', err);
    } finally {
      setLoading(false);
      if (inputRef.current) inputRef.current.value = '';
    }
  }

  return (
    <>
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        style={{ display: 'none' }}
        onChange={handleFileChange}
      />
      <Button
        size="small"
        icon={<UploadOutlined />}
        loading={loading}
        onClick={() => inputRef.current?.click()}
      >
        Upload
      </Button>
    </>
  );
}

/** Displays all users with name, role and status. Admins can create, edit, and upload avatars. */
export function UsersPage() {
  const { isAdmin } = useAuth();

  const [users,       setUsers]       = useState<UserResponse[]>([]);
  const [totalUsers,  setTotalUsers]  = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize,    setPageSize]    = useState(20);
  const [loading,     setLoading]     = useState(true);
  const [error,       setError]       = useState<string | null>(null);
  const [searchQuery,  setSearchQuery]  = useState('');
  const [activeSearch, setActiveSearch] = useState('');
  const searchInputRef = useRef<HTMLInputElement | null>(null);
  const [modalOpen,   setModalOpen]   = useState(false);
  const [editingUser, setEditingUser] = useState<UserResponse | null>(null);
  const [submitting,  setSubmitting]  = useState(false);

  const [form] = Form.useForm();

  const loadUsers = (page = currentPage, size = pageSize) =>
    getUsers({ page: page - 1, size })
      .then((data) => {
        setUsers(data.content);
        setTotalUsers(data.totalElements);
      })
      .catch(() => setError('Failed to load users.'))
      .finally(() => setLoading(false));

  useEffect(() => { loadUsers(); }, []);

  const handleSearch = useCallback(() => {
    const trimmed = searchQuery.trim();
    if (!trimmed) {
      setActiveSearch('');
      loadUsers(1, pageSize);
      return;
    }
    setActiveSearch(trimmed);
    setLoading(true);
    searchUsers(trimmed)
      .then((docs) => {
        const mapped = docs.map((d) => ({
          id: d.id,
          name: d.name,
          email: d.email,
          username: d.username,
          active: d.active,
          roles: [],
          avatarFileId: null,
        } as UserResponse));
        setUsers(mapped);
        setTotalUsers(mapped.length);
      })
      .catch(() => setError('Search failed.'))
      .finally(() => {
        setLoading(false);
        searchInputRef.current?.focus();
      });
  }, [searchQuery, pageSize]);

  useEffect(() => {
    if (searchQuery === '' && activeSearch !== '') {
      setActiveSearch('');
      loadUsers(1, pageSize);
    }
  }, [searchQuery]);

  const handleTableChange = (pagination: TablePaginationConfig) => {
    const page = pagination.current ?? 1;
    const size = pagination.pageSize ?? 20;
    setCurrentPage(page);
    setPageSize(size);
    setLoading(true);
    loadUsers(page, size);
  };

  /** Opens the modal in create mode. */
  const openCreateModal = () => {
    setEditingUser(null);
    form.resetFields();
    form.setFieldValue('active', true);
    setModalOpen(true);
  };

  /** Opens the modal in edit mode pre-filled with the given user's values. */
  const openEditModal = (user: UserResponse) => {
    setEditingUser(user);
    form.setFieldsValue({ name: user.name, email: user.email, active: user.active });
    setModalOpen(true);
  };

  /** Submits the form — calls createUser or updateUser based on edit mode. */
  const handleSubmit = () => {
    form.validateFields().catch(() => {}).then((values) => {
      if (!values) return;
      setSubmitting(true);
      const apiCall = editingUser
        ? updateUser(editingUser.id, { name: values.name, email: values.email, active: values.active })
        : createUser({ name: values.name, email: values.email, username: values.username ?? null, active: true });
      apiCall
        .then(() => {
          setModalOpen(false);
          setEditingUser(null);
          loadUsers();
        })
        .catch((err) => {
          const action  = editingUser ? 'update' : 'create';
          const msg = err?.response?.data?.message ?? err?.message ?? `Failed to ${action} user.`;
          setError(`Failed to ${action} user: ${msg}`);
        })
        .finally(() => setSubmitting(false));
    });
  };

  function handleAvatarUpdated(updated: UserResponse) {
    setUsers((prev) => prev.map((u) => (u.id === updated.id ? updated : u)));
  }

  const columns: ColumnsType<UserResponse> = [
    { title: 'Name',     dataIndex: 'name',     key: 'name' },
    { title: 'Username', dataIndex: 'username', key: 'username',
      render: (v: string | null) => v ?? '—' },
    { title: 'Email',    dataIndex: 'email',    key: 'email' },
    { title: 'Active',   dataIndex: 'active',   key: 'active',
      render: (v: boolean) => <Tag color={v ? 'green' : 'red'}>{v ? 'Active' : 'Inactive'}</Tag> },
    { title: 'Roles',    dataIndex: 'roles',    key: 'roles',
      render: (roles: RoleDto[]) => roles.map((r) => <Tag key={r.id}>{r.name}</Tag>) },
    {
      title: 'Upload Avatar',
      key: 'upload',
      render: (_, user) => <AvatarUploadButton user={user} onDone={handleAvatarUpdated} />,
    },
    ...(isAdmin ? [{
      title: 'Actions', key: 'actions',
      render: (_: unknown, record: UserResponse) => (
        <Button size="small" onClick={() => openEditModal(record)}>Edit</Button>
      ),
    }] : []),
  ];

  if (loading) return <Spin />;
  if (error)   return <Alert type="error" message={error} />;

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Typography.Title level={3} style={{ margin: 0 }}>Users</Typography.Title>
        <Space>
          <Input
            ref={searchInputRef}
            placeholder="Search users…"
            prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onPressEnter={handleSearch}
            allowClear
            autoFocus
            style={{ width: 240 }}
          />
          {isAdmin && <Button type="primary" onClick={openCreateModal}>New User</Button>}
        </Space>
      </div>

      <Table
        rowKey="id"
        dataSource={users}
        columns={columns}
        pagination={activeSearch
          ? false
          : { current: currentPage, pageSize, total: totalUsers, showSizeChanger: true }}
        onChange={activeSearch ? undefined : handleTableChange}
      />

      <Modal
        title={editingUser ? 'Edit User' : 'New User'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => { setModalOpen(false); setEditingUser(null); }}
        okText={editingUser ? 'Save' : 'Create'}
        confirmLoading={submitting}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="name" label="Name" rules={[{ required: true, message: 'Name is required' }]}>
            <Input />
          </Form.Item>
          <Form.Item
            name="email"
            label="Email"
            rules={[
              { required: true, message: 'Email is required' },
              { type: 'email',  message: 'Must be a valid email address' },
            ]}
          >
            <Input />
          </Form.Item>
          {/* Username is set on creation only — immutable after that */}
          {!editingUser && (
            <Form.Item name="username" label="Username">
              <Input />
            </Form.Item>
          )}
          {/* Active toggle is only meaningful when editing an existing user */}
          {editingUser && (
            <Form.Item name="active" label="Active" valuePropName="checked">
              <Switch />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </>
  );
}
