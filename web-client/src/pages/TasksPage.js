import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useEffect, useRef, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Table, Tag, Typography, Alert, Spin, Button, Modal, Form, Input, Select, Space, Popconfirm, Progress, InputNumber, DatePicker, } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { getTasks, createTask, updateTask, deleteTask, getProjects } from '../api/taskApi';
import { getUsers } from '../api/userApi';
import { searchTasks } from '../api/searchApi';
const STATUS_COLORS = {
    TODO: 'default',
    IN_PROGRESS: 'blue',
    DONE: 'green',
};
const TYPE_COLORS = {
    FEATURE: 'purple',
    BUG_FIXING: 'red',
    TESTING: 'cyan',
    PLANNING: 'gold',
    TECHNICAL_DEBT: 'orange',
    DOCUMENTATION: 'geekblue',
    OTHER: 'default',
};
/** Displays all tasks and allows creating, editing, and deleting them. Detail view opens as a full page. */
export function TasksPage() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const [tasks, setTasks] = useState([]);
    const [totalTasks, setTotalTasks] = useState(0);
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(20);
    const [projects, setProjects] = useState([]);
    const [users, setUsers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [activeSearch, setActiveSearch] = useState('');
    const searchInputRef = useRef(null);
    const [modalOpen, setModalOpen] = useState(false);
    const [editingTask, setEditingTask] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const [deletingId, setDeletingId] = useState(null);
    const [form] = Form.useForm();
    // Derived translation maps — recomputed on language change
    const statusOptions = [
        { label: t('tasks.statuses.TODO'), value: 'TODO' },
        { label: t('tasks.statuses.IN_PROGRESS'), value: 'IN_PROGRESS' },
        { label: t('tasks.statuses.DONE'), value: 'DONE' },
    ];
    const typeLabels = {
        FEATURE: t('tasks.types.FEATURE'),
        BUG_FIXING: t('tasks.types.BUG_FIXING'),
        TESTING: t('tasks.types.TESTING'),
        PLANNING: t('tasks.types.PLANNING'),
        TECHNICAL_DEBT: t('tasks.types.TECHNICAL_DEBT'),
        DOCUMENTATION: t('tasks.types.DOCUMENTATION'),
        OTHER: t('tasks.types.OTHER'),
    };
    const typeOptions = Object.keys(typeLabels).map((k) => ({ label: typeLabels[k], value: k }));
    const loadTasks = (page = currentPage, size = pageSize) => getTasks({ page: page - 1, size })
        .then((data) => {
        setTasks(data.content);
        setTotalTasks(data.totalElements);
    })
        .catch((err) => {
        const status = err?.response?.status;
        const message = err?.response?.data?.message ?? err?.message ?? 'Unknown error';
        setError(`${t('tasks.failedLoad')} [${status}]: ${message}`);
    })
        .finally(() => setLoading(false));
    const refreshDropdowns = () => {
        getProjects().then(setProjects).catch(() => setError(t('projects.failedLoad')));
        getUsers().then((data) => setUsers(data.content)).catch(() => setError(t('users.failedLoad')));
    };
    useEffect(() => {
        loadTasks();
        refreshDropdowns();
    }, []);
    // Run Elasticsearch search on Enter; restore normal list when query is cleared
    const handleSearch = useCallback(() => {
        const trimmed = searchQuery.trim();
        if (!trimmed) {
            setActiveSearch('');
            loadTasks(1, pageSize);
            return;
        }
        setActiveSearch(trimmed);
        setLoading(true);
        searchTasks(trimmed)
            .then((docs) => {
            const mapped = docs.map((d) => ({
                id: d.id,
                title: d.title,
                description: d.description ?? '',
                status: d.status ?? 'TODO',
                type: null,
                progress: 0,
                assignedUserId: d.assignedUserId ?? null,
                assignedUserName: d.assignedUserName ?? null,
                projectId: d.projectId ?? null,
                projectName: d.projectName ?? '—',
                phaseId: d.phaseId ?? null,
                phaseName: d.phaseName ?? null,
            }));
            setTasks(mapped);
            setTotalTasks(mapped.length);
        })
            .catch(() => setError(t('tasks.searchFailed')))
            .finally(() => {
            setLoading(false);
            searchInputRef.current?.focus();
        });
    }, [searchQuery, pageSize]);
    // Clear search when input is emptied via the × button
    useEffect(() => {
        if (searchQuery === '' && activeSearch !== '') {
            setActiveSearch('');
            loadTasks(1, pageSize);
        }
    }, [searchQuery]);
    const handleTableChange = (pagination) => {
        const page = pagination.current ?? 1;
        const size = pagination.pageSize ?? 20;
        setCurrentPage(page);
        setPageSize(size);
        setLoading(true);
        loadTasks(page, size);
    };
    /** Opens the modal in create mode, auto-selecting any dropdown with a single option. */
    const openCreateModal = () => {
        setEditingTask(null);
        form.resetFields();
        Promise.all([getProjects(), getUsers()])
            .then(([fetchedProjects, fetchedUsersPage]) => {
            const fetchedUsers = fetchedUsersPage.content;
            setProjects(fetchedProjects);
            setUsers(fetchedUsers);
            if (fetchedProjects.length === 1)
                form.setFieldValue('projectId', fetchedProjects[0].id);
            if (fetchedUsers.length === 1)
                form.setFieldValue('assignedUserId', fetchedUsers[0].id);
        })
            .catch(() => setError(t('tasks.failedOptions')));
        setModalOpen(true);
    };
    /** Opens the modal in edit mode, pre-filled with the given task's values. */
    const openEditModal = (task) => {
        setEditingTask(task);
        form.setFieldsValue({
            title: task.title,
            description: task.description,
            status: task.status,
            type: task.type ?? null,
            progress: task.progress,
            projectId: task.projectId,
            assignedUserId: task.assignedUserId ?? null,
        });
        refreshDropdowns();
        setModalOpen(true);
    };
    /** Submits the form — calls createTask or updateTask based on edit mode. */
    const handleSubmit = () => {
        form.validateFields().catch(() => { }).then((values) => {
            if (!values)
                return;
            setSubmitting(true);
            const request = {
                ...values,
                phaseId: null,
                type: values.type ?? null,
                progress: values.progress ?? 0,
                // plannedStart/End only on create; format dayjs to ISO string
                ...(editingTask ? {} : {
                    plannedStart: values.plannedStart?.toISOString(),
                    plannedEnd: values.plannedEnd?.toISOString(),
                }),
            };
            const apiCall = editingTask
                ? updateTask(editingTask.id, request)
                : createTask(request);
            apiCall
                .then(() => {
                setModalOpen(false);
                setEditingTask(null);
                loadTasks();
            })
                .catch((err) => {
                const label = editingTask ? t('tasks.failedUpdate') : t('tasks.failedCreate');
                const message = err?.response?.data?.message ?? err?.message ?? label;
                setError(`${label}: ${message}`);
            })
                .finally(() => setSubmitting(false));
        });
    };
    /** Soft-deletes a task after confirmation. */
    const handleDelete = (id) => {
        setDeletingId(id);
        deleteTask(id)
            .then(() => loadTasks())
            .catch((err) => {
            const message = err?.response?.data?.message ?? err?.message ?? t('tasks.failedDelete');
            setError(`${t('tasks.failedDelete')}: ${message}`);
        })
            .finally(() => setDeletingId(null));
    };
    const columns = [
        { title: t('tasks.title_field'), dataIndex: 'title', key: 'title' },
        { title: t('common.project'), dataIndex: 'projectName', key: 'project' },
        { title: t('tasks.assignedTo'), key: 'user',
            render: (_, record) => record.assignedUserName ?? '—',
        },
        { title: t('tasks.type'), dataIndex: 'type', key: 'type',
            render: (type) => type
                ? _jsx(Tag, { color: TYPE_COLORS[type], children: typeLabels[type] })
                : '—' },
        { title: t('common.status'), dataIndex: 'status', key: 'status',
            render: (status) => _jsx(Tag, { color: STATUS_COLORS[status], children: t(`tasks.statuses.${status}`) }) },
        { title: t('tasks.progress'), dataIndex: 'progress', key: 'progress', width: 140,
            render: (progress) => (_jsx(Progress, { percent: progress, size: "small", strokeColor: progress === 100 ? '#52c41a' : undefined })) },
        {
            title: t('common.actions'), key: 'actions',
            render: (_, record) => (_jsxs(Space, { children: [_jsx(Button, { size: "small", onClick: () => navigate(`/tasks/${record.id}`), children: t('tasks.view') }), _jsx(Button, { size: "small", onClick: () => openEditModal(record), children: t('common.edit') }), _jsx(Popconfirm, { title: t('tasks.deleteConfirm'), description: t('tasks.deleteDescription'), onConfirm: () => handleDelete(record.id), okText: t('common.delete'), okButtonProps: { danger: true }, children: _jsx(Button, { danger: true, size: "small", loading: deletingId === record.id, children: t('common.delete') }) })] })),
        },
    ];
    if (loading)
        return _jsx(Spin, {});
    if (error)
        return _jsx(Alert, { type: "error", message: error });
    return (_jsxs(_Fragment, { children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }, children: [_jsx(Typography.Title, { level: 3, style: { margin: 0 }, children: t('tasks.title') }), _jsxs(Space, { children: [_jsx(Input, { ref: searchInputRef, placeholder: t('tasks.searchPlaceholder'), prefix: _jsx(SearchOutlined, { style: { color: '#bfbfbf' } }), value: searchQuery, onChange: (e) => setSearchQuery(e.target.value), onPressEnter: handleSearch, allowClear: true, autoFocus: true, style: { width: 240 } }), _jsx(Button, { type: "primary", onClick: openCreateModal, children: t('tasks.newTask') })] })] }), _jsx(Table, { rowKey: "id", dataSource: tasks, columns: columns, pagination: activeSearch
                    ? false
                    : { current: currentPage, pageSize, total: totalTasks, showSizeChanger: true }, onChange: activeSearch ? undefined : handleTableChange }), _jsx(Modal, { title: editingTask ? t('tasks.editTask') : t('tasks.createTask'), open: modalOpen, onOk: handleSubmit, onCancel: () => { setModalOpen(false); setEditingTask(null); }, okText: editingTask ? t('common.save') : t('common.create'), confirmLoading: submitting, children: _jsxs(Form, { form: form, layout: "vertical", style: { marginTop: 16 }, children: [_jsx(Form.Item, { name: "title", label: t('tasks.title_field'), rules: [{ required: true, message: t('tasks.titleRequired') }], children: _jsx(Input, {}) }), _jsx(Form.Item, { name: "description", label: t('common.description'), children: _jsx(Input.TextArea, { rows: 3 }) }), _jsx(Form.Item, { name: "status", label: t('common.status'), initialValue: "TODO", rules: [{ required: true }], children: _jsx(Select, { options: statusOptions }) }), _jsx(Form.Item, { name: "type", label: t('tasks.type'), children: _jsx(Select, { options: typeOptions, placeholder: t('tasks.selectType'), allowClear: true }) }), _jsx(Form.Item, { name: "progress", label: t('tasks.progressPct'), initialValue: 0, children: _jsx(InputNumber, { min: 0, max: 100, style: { width: '100%' }, addonAfter: "%" }) }), _jsx(Form.Item, { name: "projectId", label: t('common.project'), rules: [{ required: true, message: t('tasks.projectRequired') }], children: _jsx(Select, { options: projects.map((p) => ({ label: p.name, value: p.id })), placeholder: t('tasks.selectProject') }) }), _jsx(Form.Item, { name: "assignedUserId", label: t('tasks.assignedTo'), rules: [{ required: true, message: t('tasks.userRequired') }], children: _jsx(Select, { options: users.map((u) => ({ label: u.name, value: u.id })), placeholder: t('tasks.selectUser') }) }), !editingTask && (_jsxs(_Fragment, { children: [_jsx(Form.Item, { name: "plannedStart", label: t('tasks.plannedStart'), rules: [{ required: true, message: t('tasks.plannedStartRequired') }], children: _jsx(DatePicker, { showTime: true, style: { width: '100%' }, placeholder: t('tasks.selectDate') }) }), _jsx(Form.Item, { name: "plannedEnd", label: t('tasks.plannedEnd'), rules: [{ required: true, message: t('tasks.plannedEndRequired') }], children: _jsx(DatePicker, { showTime: true, style: { width: '100%' }, placeholder: t('tasks.selectDate') }) })] }))] }) })] }));
}
