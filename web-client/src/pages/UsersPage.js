import { jsx as _jsx, Fragment as _Fragment, jsxs as _jsxs } from "react/jsx-runtime";
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Table, Tag, Typography, Alert, Spin, Button, Modal, Form, Input, Switch, Space, message } from 'antd';
import { UploadOutlined, SearchOutlined } from '@ant-design/icons';
import { getUsers, createUser, updateUser, uploadAvatar, updateUserAvatar } from '../api/userApi';
import { searchUsers } from '../api/searchApi';
import { useAuth } from '../auth/AuthProvider';
/** Upload button that triggers a hidden file input. */
function AvatarUploadButton({ user, onDone }) {
    const { t } = useTranslation();
    const inputRef = useRef(null);
    const [loading, setLoading] = useState(false);
    async function handleFileChange(e) {
        const file = e.target.files?.[0];
        if (!file)
            return;
        setLoading(true);
        try {
            const { fileId } = await uploadAvatar(file);
            const updated = await updateUserAvatar(user.id, fileId);
            onDone(updated);
            message.success(t('users.avatarUpdated'));
        }
        catch (err) {
            const status = err?.response?.status;
            const data = err?.response?.data;
            message.error(`Upload failed (${status ?? 'network'}): ${JSON.stringify(data) ?? ''}`);
            console.error('Avatar upload error:', err);
        }
        finally {
            setLoading(false);
            if (inputRef.current)
                inputRef.current.value = '';
        }
    }
    return (_jsxs(_Fragment, { children: [_jsx("input", { ref: inputRef, type: "file", accept: "image/*", style: { display: 'none' }, onChange: handleFileChange }), _jsx(Button, { size: "small", icon: _jsx(UploadOutlined, {}), loading: loading, onClick: () => inputRef.current?.click(), children: t('common.upload') })] }));
}
/** Displays all users with name, role and status. Admins can create, edit, and upload avatars. */
export function UsersPage() {
    const { t } = useTranslation();
    const { isAdmin } = useAuth();
    const [users, setUsers] = useState([]);
    const [totalUsers, setTotalUsers] = useState(0);
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(20);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [activeSearch, setActiveSearch] = useState('');
    const searchInputRef = useRef(null);
    const [modalOpen, setModalOpen] = useState(false);
    const [editingUser, setEditingUser] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const [form] = Form.useForm();
    const loadUsers = (page = currentPage, size = pageSize) => getUsers({ page: page - 1, size })
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
                roles: [],
                avatarFileId: null,
                language: 'en',
            }));
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
    const handleTableChange = (pagination) => {
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
    const openEditModal = (user) => {
        setEditingUser(user);
        form.setFieldsValue({ name: user.name, email: user.email, active: user.active });
        setModalOpen(true);
    };
    /** Submits the form — calls createUser or updateUser based on edit mode. */
    const handleSubmit = () => {
        form.validateFields().catch(() => { }).then((values) => {
            if (!values)
                return;
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
                const action = editingUser ? t('users.failedUpdate') : t('users.failedCreate');
                const msg = err?.response?.data?.message ?? err?.message ?? action;
                setError(`${action}: ${msg}`);
            })
                .finally(() => setSubmitting(false));
        });
    };
    function handleAvatarUpdated(updated) {
        setUsers((prev) => prev.map((u) => (u.id === updated.id ? updated : u)));
    }
    const columns = useMemo(() => [
        { title: t('common.name'), dataIndex: 'name', key: 'name' },
        { title: t('common.username'), dataIndex: 'username', key: 'username',
            render: (v) => v ?? '—' },
        { title: t('common.email'), dataIndex: 'email', key: 'email' },
        { title: t('common.status'), dataIndex: 'active', key: 'active',
            render: (v) => _jsx(Tag, { color: v ? 'green' : 'red', children: v ? t('common.active') : t('common.inactive') }) },
        { title: t('users.roles'), dataIndex: 'roles', key: 'roles',
            render: (roles) => roles.map((r) => _jsx(Tag, { children: r.name }, r.id)) },
        {
            title: t('users.uploadAvatar'),
            key: 'upload',
            render: (_, user) => _jsx(AvatarUploadButton, { user: user, onDone: handleAvatarUpdated }),
        },
        ...(isAdmin ? [{
                title: t('common.actions'), key: 'actions',
                render: (_, record) => (_jsx(Button, { size: "small", onClick: () => openEditModal(record), children: t('common.edit') })),
            }] : []),
        // eslint-disable-next-line react-hooks/exhaustive-deps
    ], [t, isAdmin]);
    if (loading)
        return _jsx(Spin, {});
    if (error)
        return _jsx(Alert, { type: "error", message: error });
    return (_jsxs(_Fragment, { children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }, children: [_jsx(Typography.Title, { level: 3, style: { margin: 0 }, children: t('users.title') }), _jsxs(Space, { children: [_jsx(Input, { ref: searchInputRef, placeholder: t('users.searchPlaceholder'), prefix: _jsx(SearchOutlined, { style: { color: '#bfbfbf' } }), value: searchQuery, onChange: (e) => setSearchQuery(e.target.value), onPressEnter: handleSearch, allowClear: true, autoFocus: true, style: { width: 240 } }), isAdmin && _jsx(Button, { type: "primary", onClick: openCreateModal, children: t('users.newUser') })] })] }), _jsx(Table, { rowKey: "id", dataSource: users, columns: columns, pagination: activeSearch
                    ? false
                    : { current: currentPage, pageSize, total: totalUsers, showSizeChanger: true }, onChange: activeSearch ? undefined : handleTableChange }), _jsx(Modal, { title: editingUser ? t('users.editUser') : t('users.newUser'), open: modalOpen, onOk: handleSubmit, onCancel: () => { setModalOpen(false); setEditingUser(null); }, okText: editingUser ? t('common.save') : t('common.create'), confirmLoading: submitting, children: _jsxs(Form, { form: form, layout: "vertical", style: { marginTop: 16 }, children: [_jsx(Form.Item, { name: "name", label: t('common.name'), rules: [{ required: true, message: t('users.nameRequired') }], children: _jsx(Input, {}) }), _jsx(Form.Item, { name: "email", label: t('common.email'), rules: [
                                { required: true, message: t('users.emailRequired') },
                                { type: 'email', message: t('users.emailInvalid') },
                            ], children: _jsx(Input, {}) }), !editingUser && (_jsx(Form.Item, { name: "username", label: t('common.username'), rules: [{ required: true, message: t('users.usernameRequired') }], children: _jsx(Input, {}) })), editingUser && (_jsx(Form.Item, { name: "active", label: t('common.active'), valuePropName: "checked", children: _jsx(Switch, {}) }))] }) })] }));
}
