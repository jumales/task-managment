import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Table, Tag, Typography, Alert, Spin, Button, Modal, Form, Input, Select, Space, Popconfirm, Progress, InputNumber, DatePicker, Steps, } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { getTasks, createTask, updateTask, deleteTask, getProjects, getPhases } from '../api/taskApi';
import { getUsers } from '../api/userApi';
import { searchTasks } from '../api/searchApi';
import { getTypeLabels } from './taskDetail/taskDetailConstants';
import { resolvePhaseLabel } from '../utils/phaseUtils';
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
    const [phases, setPhases] = useState([]);
    const [users, setUsers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [modalError, setModalError] = useState(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [activeSearch, setActiveSearch] = useState('');
    const searchInputRef = useRef(null);
    const [modalOpen, setModalOpen] = useState(false);
    const [wizardStep, setWizardStep] = useState(0);
    const [editingTask, setEditingTask] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const [deletingId, setDeletingId] = useState(null);
    const [form] = Form.useForm();
    // Derived translation maps — recomputed on language change only
    const statusOptions = useMemo(() => [
        { label: t('tasks.statuses.TODO'), value: 'TODO' },
        { label: t('tasks.statuses.IN_PROGRESS'), value: 'IN_PROGRESS' },
        { label: t('tasks.statuses.DONE'), value: 'DONE' },
    ], [t]);
    const typeLabels = useMemo(() => getTypeLabels(t), [t]);
    const typeOptions = useMemo(() => Object.keys(typeLabels).map((k) => ({ label: typeLabels[k], value: k })), [typeLabels]);
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
    const loadPhases = (projectId) => getPhases(projectId).then(setPhases).catch(() => setModalError(t('tasks.failedLoadPhases')));
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
        setWizardStep(0);
        setPhases([]);
        setModalError(null);
        form.resetFields();
        Promise.all([getProjects(), getUsers()])
            .then(([fetchedProjects, fetchedUsersPage]) => {
            const fetchedUsers = fetchedUsersPage.content;
            setProjects(fetchedProjects);
            setUsers(fetchedUsers);
            if (fetchedProjects.length === 1) {
                const projectId = fetchedProjects[0].id;
                form.setFieldValue('projectId', projectId);
                loadPhases(projectId).then(() => {
                    const defaultPhaseId = fetchedProjects[0].defaultPhaseId;
                    if (defaultPhaseId)
                        form.setFieldValue('phaseId', defaultPhaseId);
                });
            }
            if (fetchedUsers.length === 1)
                form.setFieldValue('assignedUserId', fetchedUsers[0].id);
        })
            .catch(() => setError(t('tasks.failedOptions')));
        setModalOpen(true);
    };
    /** Opens the modal in edit mode, pre-filled with the given task's values. */
    const openEditModal = (task) => {
        setModalError(null);
        setEditingTask(task);
        form.setFieldsValue({
            title: task.title,
            description: task.description,
            status: task.status,
            type: task.type ?? null,
            progress: task.progress,
            projectId: task.projectId,
            phaseId: task.phaseId,
            assignedUserId: task.assignedUserId ?? null,
        });
        refreshDropdowns();
        if (task.projectId)
            loadPhases(task.projectId);
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
                type: values.type ?? null,
                progress: editingTask ? (values.progress ?? 0) : 0,
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
            if (err?.response?.status === 409) {
                setError(t('tasks.deleteBlockedByRelations'));
            }
            else {
                const message = err?.response?.data?.message ?? err?.message ?? t('tasks.failedDelete');
                setError(`${t('tasks.failedDelete')}: ${message}`);
            }
        })
            .finally(() => setDeletingId(null));
    };
    const columns = useMemo(() => [
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
        // eslint-disable-next-line react-hooks/exhaustive-deps
    ], [t, navigate, typeLabels, deletingId]);
    if (loading)
        return _jsx(Spin, {});
    if (error)
        return _jsx(Alert, { type: "error", message: error });
    return (_jsxs(_Fragment, { children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }, children: [_jsx(Typography.Title, { level: 3, style: { margin: 0 }, children: t('tasks.title') }), _jsxs(Space, { children: [_jsx(Input, { ref: searchInputRef, placeholder: t('tasks.searchPlaceholder'), prefix: _jsx(SearchOutlined, { style: { color: '#bfbfbf' } }), value: searchQuery, onChange: (e) => setSearchQuery(e.target.value), onPressEnter: handleSearch, allowClear: true, autoFocus: true, style: { width: 240 } }), _jsx(Button, { type: "primary", onClick: openCreateModal, children: t('tasks.newTask') })] })] }), _jsx(Table, { rowKey: "id", dataSource: tasks, columns: columns, pagination: activeSearch
                    ? false
                    : { current: currentPage, pageSize, total: totalTasks, showSizeChanger: true }, onChange: activeSearch ? undefined : handleTableChange }), _jsxs(Modal, { title: editingTask ? t('tasks.editTask') : t('tasks.createTask'), open: modalOpen, onCancel: () => { setModalOpen(false); setEditingTask(null); setWizardStep(0); setModalError(null); }, footer: null, width: 560, children: [modalError && _jsx(Alert, { type: "error", message: modalError, style: { marginBottom: 12 } }), editingTask && (_jsxs(Form, { form: form, layout: "vertical", style: { marginTop: 16 }, children: [_jsx(Form.Item, { name: "title", label: t('tasks.title_field'), rules: [{ required: true, message: t('tasks.titleRequired') }], children: _jsx(Input, {}) }), _jsx(Form.Item, { name: "description", label: t('common.description'), children: _jsx(Input.TextArea, { rows: 3 }) }), _jsx(Form.Item, { name: "status", label: t('common.status'), initialValue: "TODO", rules: [{ required: true }], children: _jsx(Select, { options: statusOptions }) }), _jsx(Form.Item, { name: "type", label: t('tasks.type'), rules: [{ required: true, message: t('tasks.typeRequired') }], children: _jsx(Select, { options: typeOptions, placeholder: t('tasks.selectType') }) }), _jsx(Form.Item, { name: "progress", label: t('tasks.progressPct'), initialValue: 0, children: _jsx(InputNumber, { min: 0, max: 100, style: { width: '100%' }, addonAfter: "%" }) }), _jsx(Form.Item, { name: "projectId", label: t('common.project'), rules: [{ required: true, message: t('tasks.projectRequired') }], children: _jsx(Select, { options: projects.map((p) => ({ label: p.name, value: p.id })), placeholder: t('tasks.selectProject'), onChange: (projectId) => {
                                        form.setFieldValue('phaseId', undefined);
                                        setPhases([]);
                                        loadPhases(projectId);
                                    } }) }), _jsx(Form.Item, { name: "phaseId", label: t('tasks.phase'), rules: [{ required: true, message: t('tasks.phaseRequired') }], children: _jsx(Select, { options: phases.map((ph) => ({ label: resolvePhaseLabel(ph), value: ph.id })), placeholder: phases.length === 0 ? t('tasks.selectProjectFirst') : t('tasks.selectPhase') }) }), _jsx(Form.Item, { name: "assignedUserId", label: t('tasks.assignedTo'), rules: [{ required: true, message: t('tasks.userRequired') }], children: _jsx(Select, { options: users.map((u) => ({ label: u.name, value: u.id })), placeholder: t('tasks.selectUser') }) }), _jsx(Form.Item, { style: { marginBottom: 0, textAlign: 'right' }, children: _jsxs(Space, { children: [_jsx(Button, { onClick: () => { setModalOpen(false); setEditingTask(null); setModalError(null); }, children: t('common.cancel') }), _jsx(Button, { type: "primary", loading: submitting, onClick: handleSubmit, children: t('common.save') })] }) })] })), !editingTask && (_jsxs(_Fragment, { children: [_jsx(Steps, { current: wizardStep, size: "small", style: { marginTop: 16, marginBottom: 24 }, items: [
                                    { title: t('tasks.wizardStep1') },
                                    { title: t('tasks.wizardStep2') },
                                    { title: t('tasks.wizardStep3') },
                                ] }), _jsxs(Form, { form: form, layout: "vertical", children: [_jsxs("div", { style: { display: wizardStep === 0 ? 'block' : 'none' }, children: [_jsx(Form.Item, { name: "title", label: t('tasks.title_field'), rules: [{ required: true, message: t('tasks.titleRequired') }], children: _jsx(Input, {}) }), _jsx(Form.Item, { name: "description", label: t('common.description'), children: _jsx(Input.TextArea, { rows: 3 }) })] }), _jsxs("div", { style: { display: wizardStep === 1 ? 'block' : 'none' }, children: [_jsx(Form.Item, { name: "type", label: t('tasks.type'), rules: [{ required: true, message: t('tasks.typeRequired') }], children: _jsx(Select, { options: typeOptions, placeholder: t('tasks.selectType') }) }), _jsx(Form.Item, { name: "status", label: t('common.status'), initialValue: "TODO", rules: [{ required: true }], children: _jsx(Select, { options: statusOptions }) }), _jsx(Form.Item, { name: "projectId", label: t('common.project'), rules: [{ required: true, message: t('tasks.projectRequired') }], children: _jsx(Select, { options: projects.map((p) => ({ label: p.name, value: p.id })), placeholder: t('tasks.selectProject'), onChange: (projectId) => {
                                                        form.setFieldValue('phaseId', undefined);
                                                        setPhases([]);
                                                        loadPhases(projectId);
                                                    } }) }), _jsx(Form.Item, { name: "phaseId", label: t('tasks.phase'), rules: [{ required: true, message: t('tasks.phaseRequired') }], children: _jsx(Select, { options: phases.map((ph) => ({ label: resolvePhaseLabel(ph), value: ph.id })), placeholder: phases.length === 0 ? t('tasks.selectProjectFirst') : t('tasks.selectPhase') }) })] }), _jsxs("div", { style: { display: wizardStep === 2 ? 'block' : 'none' }, children: [_jsx(Form.Item, { name: "assignedUserId", label: t('tasks.assignedTo'), rules: [{ required: true, message: t('tasks.userRequired') }], children: _jsx(Select, { options: users.map((u) => ({ label: u.name, value: u.id })), placeholder: t('tasks.selectUser') }) }), _jsx(Form.Item, { name: "plannedStart", label: t('tasks.plannedStart'), rules: [{ required: true, message: t('tasks.plannedStartRequired') }], children: _jsx(DatePicker, { showTime: true, style: { width: '100%' }, placeholder: t('tasks.selectDate') }) }), _jsx(Form.Item, { name: "plannedEnd", label: t('tasks.plannedEnd'), dependencies: ['plannedStart'], rules: [
                                                    { required: true, message: t('tasks.plannedEndRequired') },
                                                    ({ getFieldValue }) => ({
                                                        validator(_, value) {
                                                            const start = getFieldValue('plannedStart');
                                                            if (!value || !start || value.isAfter(start))
                                                                return Promise.resolve();
                                                            return Promise.reject(new Error(t('tasks.plannedEndMustBeAfterStart')));
                                                        },
                                                    }),
                                                ], children: _jsx(DatePicker, { showTime: true, style: { width: '100%' }, placeholder: t('tasks.selectDate') }) })] })] }), _jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', marginTop: 8 }, children: [_jsx(Button, { disabled: wizardStep === 0, onClick: () => setWizardStep((s) => s - 1), children: t('common.back') }), _jsxs(Space, { children: [_jsx(Button, { onClick: () => { setModalOpen(false); setWizardStep(0); }, children: t('common.cancel') }), wizardStep < 2 ? (_jsx(Button, { type: "primary", onClick: () => {
                                                    const fieldsForStep = [
                                                        ['title'],
                                                        ['type', 'status', 'projectId', 'phaseId'],
                                                    ][wizardStep];
                                                    form.validateFields(fieldsForStep)
                                                        .then(() => setWizardStep((s) => s + 1))
                                                        .catch(() => { });
                                                }, children: t('common.next') })) : (_jsx(Button, { type: "primary", loading: submitting, onClick: handleSubmit, children: t('common.create') }))] })] })] }))] })] }));
}
