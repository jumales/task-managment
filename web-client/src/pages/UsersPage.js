import { jsx as _jsx, Fragment as _Fragment, jsxs as _jsxs } from "react/jsx-runtime";
import { useCallback, useEffect, useRef, useState } from 'react';
import { Table, Tag, Typography, Alert, Spin, Button, Modal, Form, Input, Switch, Space, message } from 'antd';
import { UploadOutlined, SearchOutlined } from '@ant-design/icons';
import { getUsers, createUser, updateUser, uploadAvatar, updateUserAvatar } from '../api/userApi';
import { searchUsers } from '../api/searchApi';
import { useAuth } from '../auth/AuthProvider';
/** Upload button that triggers a hidden file input. */
function AvatarUploadButton({ user, onDone }) {
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
            message.success('Avatar updated');
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
    return (_jsxs(_Fragment, { children: [_jsx("input", { ref: inputRef, type: "file", accept: "image/*", style: { display: 'none' }, onChange: handleFileChange }), _jsx(Button, { size: "small", icon: _jsx(UploadOutlined, {}), loading: loading, onClick: () => inputRef.current?.click(), children: "Upload" })] }));
}
/** Displays all users with name, role and status. Admins can create, edit, and upload avatars. */
export function UsersPage() {
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
            }));
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
                const action = editingUser ? 'update' : 'create';
                const msg = err?.response?.data?.message ?? err?.message ?? `Failed to ${action} user.`;
                setError(`Failed to ${action} user: ${msg}`);
            })
                .finally(() => setSubmitting(false));
        });
    };
    function handleAvatarUpdated(updated) {
        setUsers((prev) => prev.map((u) => (u.id === updated.id ? updated : u)));
    }
    const columns = [
        { title: 'Name', dataIndex: 'name', key: 'name' },
        { title: 'Username', dataIndex: 'username', key: 'username',
            render: (v) => v ?? '—' },
        { title: 'Email', dataIndex: 'email', key: 'email' },
        { title: 'Active', dataIndex: 'active', key: 'active',
            render: (v) => _jsx(Tag, { color: v ? 'green' : 'red', children: v ? 'Active' : 'Inactive' }) },
        { title: 'Roles', dataIndex: 'roles', key: 'roles',
            render: (roles) => roles.map((r) => _jsx(Tag, { children: r.name }, r.id)) },
        {
            title: 'Upload Avatar',
            key: 'upload',
            render: (_, user) => _jsx(AvatarUploadButton, { user: user, onDone: handleAvatarUpdated }),
        },
        ...(isAdmin ? [{
                title: 'Actions', key: 'actions',
                render: (_, record) => (_jsx(Button, { size: "small", onClick: () => openEditModal(record), children: "Edit" })),
            }] : []),
    ];
    if (loading)
        return _jsx(Spin, {});
    if (error)
        return _jsx(Alert, { type: "error", message: error });
    return (_jsxs(_Fragment, { children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }, children: [_jsx(Typography.Title, { level: 3, style: { margin: 0 }, children: "Users" }), _jsxs(Space, { children: [_jsx(Input, { ref: searchInputRef, placeholder: "Search users\u2026", prefix: _jsx(SearchOutlined, { style: { color: '#bfbfbf' } }), value: searchQuery, onChange: (e) => setSearchQuery(e.target.value), onPressEnter: handleSearch, allowClear: true, autoFocus: true, style: { width: 240 } }), isAdmin && _jsx(Button, { type: "primary", onClick: openCreateModal, children: "New User" })] })] }), _jsx(Table, { rowKey: "id", dataSource: users, columns: columns, pagination: activeSearch
                    ? false
                    : { current: currentPage, pageSize, total: totalUsers, showSizeChanger: true }, onChange: activeSearch ? undefined : handleTableChange }), _jsx(Modal, { title: editingUser ? 'Edit User' : 'New User', open: modalOpen, onOk: handleSubmit, onCancel: () => { setModalOpen(false); setEditingUser(null); }, okText: editingUser ? 'Save' : 'Create', confirmLoading: submitting, children: _jsxs(Form, { form: form, layout: "vertical", style: { marginTop: 16 }, children: [_jsx(Form.Item, { name: "name", label: "Name", rules: [{ required: true, message: 'Name is required' }], children: _jsx(Input, {}) }), _jsx(Form.Item, { name: "email", label: "Email", rules: [
                                { required: true, message: 'Email is required' },
                                { type: 'email', message: 'Must be a valid email address' },
                            ], children: _jsx(Input, {}) }), !editingUser && (_jsx(Form.Item, { name: "username", label: "Username", rules: [{ required: true, message: 'Username is required' }], children: _jsx(Input, {}) })), editingUser && (_jsx(Form.Item, { name: "active", label: "Active", valuePropName: "checked", children: _jsx(Switch, {}) }))] }) })] }));
}
