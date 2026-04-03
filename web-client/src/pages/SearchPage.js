import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Input, Tabs, Table, Tag, Typography, Space, Empty } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { searchTasks, searchUsers } from '../api/searchApi';
const { Title, Text } = Typography;
const STATUS_COLORS = {
    TODO: 'default',
    IN_PROGRESS: 'processing',
    DONE: 'success',
};
/** Full-text search page backed by Elasticsearch via search-service. */
export function SearchPage() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();
    const initialQuery = searchParams.get('q') ?? '';
    const [query, setQuery] = useState(initialQuery);
    const [tasks, setTasks] = useState([]);
    const [users, setUsers] = useState([]);
    const [loading, setLoading] = useState(false);
    const [searched, setSearched] = useState(initialQuery.length > 0);
    const debounceRef = useRef(null);
    const taskColumns = useMemo(() => [
        {
            title: t('search.title_col'),
            dataIndex: 'title',
            key: 'title',
            render: (title) => _jsx(Text, { strong: true, children: title }),
        },
        {
            title: t('search.description'),
            dataIndex: 'description',
            key: 'description',
            render: (desc) => desc ? (_jsx(Text, { type: "secondary", ellipsis: true, style: { maxWidth: 320, display: 'block' }, children: desc })) : (_jsx(Text, { type: "secondary", children: "\u2014" })),
        },
        {
            title: t('common.status'),
            dataIndex: 'status',
            key: 'status',
            width: 130,
            render: (status) => status ? (_jsx(Tag, { color: STATUS_COLORS[status] ?? 'default', children: status.replace('_', ' ') })) : null,
        },
        {
            title: t('search.projectName'),
            dataIndex: 'projectName',
            key: 'projectName',
            render: (name) => name ?? _jsx(Text, { type: "secondary", children: "\u2014" }),
        },
        {
            title: t('search.assignedTo'),
            dataIndex: 'assignedUserName',
            key: 'assignedUserName',
            render: (name) => name ?? _jsx(Text, { type: "secondary", children: "\u2014" }),
        },
    ], [t]);
    const userColumns = useMemo(() => [
        {
            title: t('common.name'),
            dataIndex: 'name',
            key: 'name',
            render: (name) => _jsx(Text, { strong: true, children: name }),
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
            render: (username) => _jsx(Text, { code: true, children: username }),
        },
        {
            title: t('common.status'),
            dataIndex: 'active',
            key: 'active',
            width: 90,
            render: (active) => (_jsx(Tag, { color: active ? 'success' : 'default', children: active ? t('common.active') : t('common.inactive') })),
        },
    ], [t]);
    const onTaskRow = useCallback((record) => ({
        onClick: () => navigate(`/tasks?highlight=${record.id}`),
        style: { cursor: 'pointer' },
    }), [navigate]);
    const onUserRow = useCallback((record) => ({
        onClick: () => navigate(`/users?highlight=${record.id}`),
        style: { cursor: 'pointer' },
    }), [navigate]);
    // Run search whenever query changes, debounced 350ms
    useEffect(() => {
        if (debounceRef.current)
            clearTimeout(debounceRef.current);
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
        return () => { if (debounceRef.current)
            clearTimeout(debounceRef.current); };
    }, [query]);
    // Kick off initial search if query came from URL param
    useEffect(() => {
        if (initialQuery) {
            setLoading(true);
            setSearched(true);
            Promise.all([searchTasks(initialQuery), searchUsers(initialQuery)])
                .then(([ts, us]) => { setTasks(ts); setUsers(us); })
                .catch(() => { })
                .finally(() => setLoading(false));
        }
    }, []);
    const tabItems = [
        {
            key: 'tasks',
            label: `${t('nav.tasks')}${searched ? ` (${tasks.length})` : ''}`,
            children: (_jsx(Table, { columns: taskColumns, dataSource: tasks, rowKey: "id", loading: loading, locale: { emptyText: searched ? _jsx(Empty, { description: t('search.noTasksFound') }) : _jsx(Empty, { description: t('search.enterQuery') }) }, onRow: onTaskRow, pagination: false, size: "middle" })),
        },
        {
            key: 'users',
            label: `${t('nav.users')}${searched ? ` (${users.length})` : ''}`,
            children: (_jsx(Table, { columns: userColumns, dataSource: users, rowKey: "id", loading: loading, locale: { emptyText: searched ? _jsx(Empty, { description: t('search.noUsersFound') }) : _jsx(Empty, { description: t('search.enterQuery') }) }, onRow: onUserRow, pagination: false, size: "middle" })),
        },
    ];
    return (_jsxs(Space, { direction: "vertical", size: "large", style: { width: '100%' }, children: [_jsx(Title, { level: 3, style: { marginBottom: 0 }, children: t('search.title') }), _jsx(Input, { size: "large", placeholder: t('search.placeholder'), prefix: _jsx(SearchOutlined, { style: { color: '#bfbfbf' } }), value: query, onChange: (e) => setQuery(e.target.value), allowClear: true, autoFocus: true, style: { maxWidth: 560 } }), _jsx(Tabs, { defaultActiveKey: "tasks", items: tabItems })] }));
}
