import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Table, Tag, Typography, Alert, Spin, Button, Modal, Form, Input, Switch, Space, Select, message, Avatar } from 'antd';
import { UploadOutlined, SearchOutlined, UserOutlined } from '@ant-design/icons';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import type { InputRef } from 'antd';
import { getUsers, createUser, updateUser, uploadAvatar, updateUserAvatar, getUserRoles, setUserRoles } from '../api/userApi';
import { useAvatarBlobUrl } from '../hooks/useAvatarBlobUrl';
import { searchUsers } from '../api/searchApi';
import { useAuth } from '../auth/AuthProvider';
import type { RealmRole, UserResponse } from '../api/types';

/** Assignable realm roles shown in the edit modal (WEB_APP is always auto-assigned and excluded). */
const ASSIGNABLE_ROLE_OPTIONS = (
  ['ADMIN', 'DEVELOPER', 'QA', 'DEVOPS', 'PM', 'SUPERVISOR'] as RealmRole[]
).map((r) => ({ label: r, value: r }));

/** Upload button that triggers a hidden file input. */
function AvatarUploadButton({ user, onDone }: { user: UserResponse; onDone: (updated: UserResponse) => void }) {
  const { t } = useTranslation();
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
      message.success(t('users.avatarUpdated'));
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
        {t('common.upload')}
      </Button>
    </>
  );
}

/** Renders a small avatar image for the given fileId, falling back to a generic user icon. */
function UserAvatarCell({ fileId }: { fileId: string | null }) {
  const url = useAvatarBlobUrl(fileId);
  return url
    ? <Avatar src={url} size="small" />
    : <Avatar icon={<UserOutlined />} size="small" />;
}

/** Displays all users with name, role and status. Admins can create, edit, and upload avatars. */
export function UsersPage() {
  const { t } = useTranslation();
  const { isAdmin, isSupervisor } = useAuth();

  const [users,       setUsers]       = useState<UserResponse[]>([]);
  const [totalUsers,  setTotalUsers]  = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize,    setPageSize]    = useState(20);
  const [loading,     setLoading]     = useState(true);
  const [error,       setError]       = useState<string | null>(null);
  const [searchQuery,  setSearchQuery]  = useState('');
  const [activeSearch, setActiveSearch] = useState('');
  const searchInputRef = useRef<InputRef | null>(null);
  const [modalOpen,    setModalOpen]    = useState(false);
  const [editingUser,  setEditingUser]  = useState<UserResponse | null>(null);
  const [submitting,   setSubmitting]   = useState(false);
  const [editingRoles, setEditingRoles] = useState<string[]>([]);
  const [rolesLoading, setRolesLoading] = useState(false);

  const [form] = Form.useForm();

  const loadUsers = (page = currentPage, size = pageSize) =>
    getUsers({ page: page - 1, size })
      .then((data) => {
        setUsers(data.content);
        setTotalUsers(data.totalElements);
      })
      .catch(() => setError(t('users.failedLoad')))
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
          avatarFileId: null,
          language: 'en',
          roles: [],
        } as UserResponse));
        setUsers(mapped);
        setTotalUsers(mapped.length);
      })
      .catch(() => setError(t('users.failedSearch')))
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
    setEditingRoles([]);
    form.resetFields();
    form.setFieldValue('active', true);
    setModalOpen(true);
  };

  /** Opens the modal in edit mode, pre-filling user fields and fetching current roles in the background. */
  const openEditModal = (user: UserResponse) => {
    setEditingUser(user);
    setEditingRoles([]);
    form.setFieldsValue({ name: user.name, email: user.email, active: user.active });
    setModalOpen(true);
    // Load roles in background — modal opens immediately, Select shows a loading spinner
    setRolesLoading(true);
    getUserRoles(user.id)
      .then(setEditingRoles)
      .catch(() => setError(t('users.failedLoadRoles')))
      .finally(() => setRolesLoading(false));
  };

  /** Submits the form — calls createUser or updateUser (and setUserRoles for edits) based on mode. */
  const handleSubmit = () => {
    form.validateFields().catch(() => {}).then((values) => {
      if (!values) return;
      setSubmitting(true);
      const userUpdate = editingUser
        ? updateUser(editingUser.id, { name: values.name, email: values.email, active: values.active })
        : createUser({ name: values.name, email: values.email, username: values.username ?? null, active: true });
      // Only set roles when editing — new users start with no manageable roles (WEB_APP is auto-assigned)
      const roleUpdate = editingUser
        ? setUserRoles(editingUser.id, editingRoles)
        : Promise.resolve([]);
      Promise.all([userUpdate, roleUpdate])
        .then(() => {
          setModalOpen(false);
          setEditingUser(null);
          loadUsers();
        })
        .catch((err) => {
          const action = editingUser ? t('users.failedUpdate') : t('users.failedCreate');
          const msg = err?.response?.data?.message ?? err?.message ?? action;
          setError(`${action}: ${msg}`);
        })
        .finally(() => setSubmitting(false));
    });
  };

  function handleAvatarUpdated(updated: UserResponse) {
    setUsers((prev) => prev.map((u) => (u.id === updated.id ? updated : u)));
  }

  const columns: ColumnsType<UserResponse> = useMemo(() => [
    { title: t('users.avatar'),    key: 'avatar',         width: 60,
      render: (_: unknown, user: UserResponse) => <UserAvatarCell fileId={user.avatarFileId} /> },
    { title: t('common.name'),     dataIndex: 'name',     key: 'name' },
    { title: t('common.username'), dataIndex: 'username', key: 'username',
      render: (v: string | null) => v ?? '—' },
    { title: t('common.email'),    dataIndex: 'email',    key: 'email' },
    { title: t('users.roles'),     dataIndex: 'roles',    key: 'roles',
      render: (roles: string[]) => (
        <Space size={[0, 4]} wrap>
          {(roles ?? []).map((r) => <Tag key={r} color="blue">{r}</Tag>)}
        </Space>
      ),
    },
    { title: t('common.status'),   dataIndex: 'active',   key: 'active',
      render: (v: boolean) => <Tag color={v ? 'green' : 'red'}>{v ? t('common.active') : t('common.inactive')}</Tag> },
    ...(!isSupervisor ? [{
      title: t('users.uploadAvatar'),
      key: 'upload',
      render: (_: unknown, user: UserResponse) => <AvatarUploadButton user={user} onDone={handleAvatarUpdated} />,
    }] : []),
    ...(isAdmin ? [{
      title: t('common.actions'), key: 'actions',
      render: (_: unknown, record: UserResponse) => (
        <Button size="small" onClick={() => openEditModal(record)}>{t('common.edit')}</Button>
      ),
    }] : []),
  // eslint-disable-next-line react-hooks/exhaustive-deps
  ], [t, isAdmin, isSupervisor]);

  if (loading) return <Spin />;
  if (error)   return <Alert type="error" message={error} />;

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Typography.Title level={3} style={{ margin: 0 }}>{t('users.title')}</Typography.Title>
        <Space>
          <Input
            ref={searchInputRef}
            placeholder={t('users.searchPlaceholder')}
            prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onPressEnter={handleSearch}
            allowClear
            autoFocus
            style={{ width: 240 }}
          />
          {isAdmin && <Button type="primary" onClick={openCreateModal}>{t('users.newUser')}</Button>}
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
        title={editingUser ? t('users.editUser') : t('users.newUser')}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => { setModalOpen(false); setEditingUser(null); }}
        okText={editingUser ? t('common.save') : t('common.create')}
        confirmLoading={submitting}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="name" label={t('common.name')} rules={[{ required: true, message: t('users.nameRequired') }]}>
            <Input />
          </Form.Item>
          <Form.Item
            name="email"
            label={t('common.email')}
            rules={[
              { required: true, message: t('users.emailRequired') },
              { type: 'email',  message: t('users.emailInvalid') },
            ]}
          >
            <Input />
          </Form.Item>
          {/* Username is set on creation only — immutable after that */}
          {!editingUser && (
            <Form.Item name="username" label={t('common.username')} rules={[{ required: true, message: t('users.usernameRequired') }]}>
              <Input />
            </Form.Item>
          )}
          {/* Active toggle is only meaningful when editing an existing user */}
          {editingUser && (
            <Form.Item name="active" label={t('common.active')} valuePropName="checked">
              <Switch />
            </Form.Item>
          )}
          {/* Role multi-select is only shown when editing — new users start with no manageable roles */}
          {editingUser && (
            <Form.Item label={t('users.roles')}>
              <Select
                mode="multiple"
                loading={rolesLoading}
                value={editingRoles}
                onChange={setEditingRoles}
                options={ASSIGNABLE_ROLE_OPTIONS}
                placeholder={t('users.selectRoles')}
              />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </>
  );
}
