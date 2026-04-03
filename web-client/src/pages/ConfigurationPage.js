import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Form, Input, Modal, Popconfirm, Select, Table, Tabs, Tag, Tooltip, Typography, } from 'antd';
import { DeleteOutlined, EditOutlined } from '@ant-design/icons';
import { deleteNotificationTemplate, getNotificationTemplates, getProjects, getTemplatePlaceholders, upsertNotificationTemplate, } from '../api/taskApi';
const ALL_EVENT_TYPES = [
    'TASK_CREATED',
    'STATUS_CHANGED',
    'COMMENT_ADDED',
    'PHASE_CHANGED',
    'PLANNED_WORK_CREATED',
    'BOOKED_WORK_CREATED',
    'BOOKED_WORK_UPDATED',
    'BOOKED_WORK_DELETED',
];
export function ConfigurationPage() {
    const { t } = useTranslation();
    return (_jsxs("div", { children: [_jsx(Typography.Title, { level: 3, style: { marginTop: 0 }, children: t('configuration.title') }), _jsx(Tabs, { defaultActiveKey: "templates", items: [
                    { key: 'templates', label: t('configuration.templates'), children: _jsx(TemplatesTab, {}) },
                ] })] }));
}
/** Templates tab — per-project email template management. */
function TemplatesTab() {
    const { t } = useTranslation();
    const [projects, setProjects] = useState([]);
    const [projectId, setProjectId] = useState(null);
    const [templates, setTemplates] = useState([]);
    const [placeholders, setPlaceholders] = useState([]);
    const [loadingList, setLoadingList] = useState(false);
    const [error, setError] = useState(null);
    const [modalOpen, setModalOpen] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [editingType, setEditingType] = useState(null);
    const [deletingType, setDeletingType] = useState(null);
    const [form] = Form.useForm();
    // Track which field (subject or body) was last focused so placeholder clicks insert there.
    const lastFocusedField = useRef('bodyTemplate');
    // Load project list once on mount.
    useEffect(() => {
        getProjects()
            .then(setProjects)
            .catch(() => setError(t('configuration.failedLoadProjects')));
    }, []);
    // Reload templates and fetch placeholders whenever the selected project changes.
    useEffect(() => {
        if (!projectId)
            return;
        setError(null);
        setLoadingList(true);
        Promise.all([
            getNotificationTemplates(projectId),
            getTemplatePlaceholders(projectId),
        ])
            .then(([tmpl, ph]) => {
            setTemplates(tmpl);
            setPlaceholders(ph);
        })
            .catch(() => setError(t('configuration.failedLoad')))
            .finally(() => setLoadingList(false));
    }, [projectId]);
    function loadTemplates() {
        if (!projectId)
            return;
        setLoadingList(true);
        getNotificationTemplates(projectId)
            .then(setTemplates)
            .catch(() => setError(t('configuration.failedLoad')))
            .finally(() => setLoadingList(false));
    }
    function validatePlaceholders(_, value) {
        if (!value)
            return Promise.resolve();
        const knownKeys = new Set(placeholders.map((p) => p.key));
        const unknown = [...value.matchAll(/\{(\w+)\}/g)]
            .map((m) => m[1])
            .filter((tok) => !knownKeys.has(tok));
        if (unknown.length === 0)
            return Promise.resolve();
        return Promise.reject(new Error(t('configuration.unknownPlaceholders', { tokens: unknown.join(', ') })));
    }
    function openEdit(eventType, existing) {
        setEditingType(eventType);
        form.setFieldsValue({
            subjectTemplate: existing?.subjectTemplate ?? '',
            bodyTemplate: existing?.bodyTemplate ?? '',
        });
        setModalOpen(true);
    }
    async function handleSave() {
        if (!projectId || !editingType)
            return;
        try {
            const values = await form.validateFields();
            setSubmitting(true);
            await upsertNotificationTemplate(projectId, editingType, values);
            setModalOpen(false);
            loadTemplates();
        }
        catch (err) {
            const msg = err?.response?.data?.message;
            setError(msg ?? t('configuration.failedSave'));
        }
        finally {
            setSubmitting(false);
        }
    }
    async function handleDelete(eventType) {
        if (!projectId)
            return;
        setDeletingType(eventType);
        try {
            await deleteNotificationTemplate(projectId, eventType);
            loadTemplates();
        }
        catch {
            setError(t('configuration.failedDelete'));
        }
        finally {
            setDeletingType(null);
        }
    }
    /** Inserts a placeholder token into the last-focused template field. */
    function insertPlaceholder(key) {
        const field = lastFocusedField.current;
        const current = form.getFieldValue(field) ?? '';
        form.setFieldValue(field, current + `{${key}}`);
    }
    // Build a Map once (O(n)) so each eventType lookup is O(1) instead of O(n) per row.
    const templatesByEvent = new Map(templates.map((tmpl) => [tmpl.eventType, tmpl]));
    const rows = ALL_EVENT_TYPES.map((eventType) => ({
        eventType,
        template: templatesByEvent.get(eventType) ?? null,
    }));
    const columns = [
        {
            title: t('configuration.event'),
            dataIndex: 'eventType',
            width: 200,
            render: (et) => t(`configuration.eventTypes.${et}`),
        },
        {
            title: t('configuration.subjectTemplate'),
            render: (_, row) => row.template
                ? _jsx(Typography.Text, { ellipsis: true, style: { maxWidth: 300 }, children: row.template.subjectTemplate })
                : _jsx(Typography.Text, { type: "secondary", children: t('configuration.usingDefault') }),
        },
        {
            title: t('configuration.bodyTemplate'),
            render: (_, row) => row.template
                ? _jsx(Typography.Text, { ellipsis: true, style: { maxWidth: 300 }, children: row.template.bodyTemplate })
                : _jsx(Typography.Text, { type: "secondary", children: t('configuration.usingDefault') }),
        },
        {
            title: t('common.actions'),
            width: 120,
            render: (_, row) => (_jsxs("span", { children: [_jsx(Tooltip, { title: t('configuration.editTemplate'), children: _jsx(Button, { type: "link", size: "small", icon: _jsx(EditOutlined, {}), onClick: () => openEdit(row.eventType, row.template) }) }), row.template && (_jsx(Popconfirm, { title: t('configuration.resetConfirm'), description: t('configuration.resetDescription'), onConfirm: () => handleDelete(row.eventType), okText: t('configuration.resetToDefault'), okButtonProps: { danger: true }, children: _jsx(Tooltip, { title: t('configuration.resetToDefault'), children: _jsx(Button, { type: "link", size: "small", danger: true, icon: _jsx(DeleteOutlined, {}), loading: deletingType === row.eventType }) }) }))] })),
        },
    ];
    return (_jsxs("div", { children: [error && (_jsx(Alert, { message: error, type: "error", showIcon: true, closable: true, onClose: () => setError(null), style: { marginBottom: 16 } })), _jsxs("div", { style: { marginBottom: 16, display: 'flex', alignItems: 'center', gap: 8 }, children: [_jsxs(Typography.Text, { strong: true, children: [t('common.project'), ":"] }), _jsx(Select, { style: { width: 280 }, placeholder: t('configuration.selectProject'), value: projectId, onChange: setProjectId, options: projects.map((p) => ({ value: p.id, label: p.name })) })] }), _jsx(Table, { rowKey: "eventType", columns: columns, dataSource: projectId ? rows : [], loading: loadingList, pagination: false, locale: { emptyText: projectId ? t('configuration.emptyWithProject') : t('configuration.emptyWithoutProject') } }), _jsxs(Modal, { title: editingType ? `${t('configuration.editTemplate')} — ${t(`configuration.eventTypes.${editingType}`)}` : '', open: modalOpen, onOk: handleSave, onCancel: () => setModalOpen(false), confirmLoading: submitting, okText: t('common.save'), width: 680, destroyOnClose: true, children: [placeholders.length > 0 && (_jsxs("div", { style: { marginBottom: 16 }, children: [_jsx(Typography.Text, { type: "secondary", style: { display: 'block', marginBottom: 6 }, children: t('configuration.insertTokenHint') }), _jsx("div", { style: { display: 'flex', flexWrap: 'wrap', gap: 4 }, children: placeholders.map((ph) => (_jsx(Tooltip, { title: ph.description, children: _jsx(Tag, { style: { cursor: 'pointer' }, color: "blue", onClick: () => insertPlaceholder(ph.key), children: `{${ph.key}}` }) }, ph.key))) })] })), _jsxs(Form, { form: form, layout: "vertical", children: [_jsx(Form.Item, { name: "subjectTemplate", label: t('configuration.subject'), rules: [
                                    { required: true, message: t('configuration.subjectRequired') },
                                    { validator: validatePlaceholders, warningOnly: true },
                                ], children: _jsx(Input, { onFocus: () => { lastFocusedField.current = 'subjectTemplate'; } }) }), _jsx(Form.Item, { name: "bodyTemplate", label: t('configuration.body'), rules: [
                                    { required: true, message: t('configuration.bodyRequired') },
                                    { validator: validatePlaceholders, warningOnly: true },
                                ], children: _jsx(Input.TextArea, { rows: 6, onFocus: () => { lastFocusedField.current = 'bodyTemplate'; } }) })] })] })] }));
}
