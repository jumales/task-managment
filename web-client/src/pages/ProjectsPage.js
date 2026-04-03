import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Table, Typography, Alert, Spin, Button, Modal, Form, Input, Popconfirm, Space, Tag } from 'antd';
import { getProjects, createProject, deleteProject, getPhases, updatePhase, updateProject } from '../api/taskApi';
import { useAuth } from '../auth/AuthProvider';
import { formatPhaseEnum } from '../utils/phaseUtils';
/** Displays all projects. Admins can create and delete projects, and manage phase custom labels. */
export function ProjectsPage() {
    const { t } = useTranslation();
    const { isAdmin } = useAuth();
    const [projects, setProjects] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [modalOpen, setModalOpen] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [deletingId, setDeletingId] = useState(null);
    // Phase management modal state
    const [phasesProject, setPhasesProject] = useState(null);
    const [phases, setPhases] = useState([]);
    const [phasesLoading, setPhasesLoading] = useState(false);
    const [phasesError, setPhasesError] = useState(null);
    const [customNames, setCustomNames] = useState({});
    const [savingPhaseId, setSavingPhaseId] = useState(null);
    const [settingDefaultId, setSettingDefaultId] = useState(null);
    const [form] = Form.useForm();
    const loadProjects = () => getProjects()
        .then(setProjects)
        .catch(() => setError(t('projects.failedLoad')))
        .finally(() => setLoading(false));
    useEffect(() => { loadProjects(); }, []);
    const openModal = () => {
        form.resetFields();
        setModalOpen(true);
    };
    const handleSubmit = () => {
        form.validateFields().catch(() => { }).then((values) => {
            if (!values)
                return;
            setSubmitting(true);
            createProject(values)
                .then(() => {
                setModalOpen(false);
                loadProjects();
            })
                .catch((err) => {
                const message = err?.response?.data?.message ?? err?.message ?? t('projects.failedCreate');
                setError(`${t('projects.failedCreate')}: ${message}`);
            })
                .finally(() => setSubmitting(false));
        });
    };
    const handleDelete = (id) => {
        setDeletingId(id);
        deleteProject(id)
            .then(() => loadProjects())
            .catch((err) => {
            const message = err?.response?.data?.message ?? err?.message ?? t('projects.failedDelete');
            setError(`${t('projects.failedDelete')}: ${message}`);
        })
            .finally(() => setDeletingId(null));
    };
    /** Opens the phases modal and loads phases for the given project. */
    const openPhasesModal = (project) => {
        setPhasesProject(project);
        setPhasesLoading(true);
        setPhasesError(null);
        getPhases(project.id)
            .then((loaded) => {
            setPhases(loaded);
            setCustomNames(Object.fromEntries(loaded.map((p) => [p.id, p.customName ?? ''])));
        })
            .catch(() => setPhasesError(t('projects.failedLoadPhases')))
            .finally(() => setPhasesLoading(false));
    };
    /** Saves the custom name for a single phase. */
    const handleSaveCustomName = async (phase) => {
        const newCustomName = customNames[phase.id]?.trim() || null;
        setSavingPhaseId(phase.id);
        try {
            const updated = await updatePhase(phase.id, { name: phase.name, customName: newCustomName, projectId: phase.projectId });
            setPhases((prev) => prev.map((p) => (p.id === phase.id ? updated : p)));
            setCustomNames((prev) => ({ ...prev, [phase.id]: updated.customName ?? '' }));
        }
        catch {
            setPhasesError(t('projects.failedSavePhase'));
        }
        finally {
            setSavingPhaseId(null);
        }
    };
    /** Toggles the default phase for the current project. */
    const handleToggleDefault = async (phase) => {
        if (!phasesProject)
            return;
        const newDefaultId = phasesProject.defaultPhaseId === phase.id ? null : phase.id;
        setSettingDefaultId(phase.id);
        try {
            const updated = await updateProject(phasesProject.id, {
                name: phasesProject.name,
                description: phasesProject.description,
                taskCodePrefix: phasesProject.taskCodePrefix,
                defaultPhaseId: newDefaultId,
            });
            setPhasesProject(updated);
            setProjects((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
        }
        catch {
            setPhasesError(t('projects.failedSavePhase'));
        }
        finally {
            setSettingDefaultId(null);
        }
    };
    const phaseColumns = [
        {
            title: t('tasks.phase'),
            dataIndex: 'name',
            width: 140,
            render: (name, record) => (_jsxs(Space, { children: [formatPhaseEnum(name), phasesProject?.defaultPhaseId === record.id && (_jsx(Tag, { color: "blue", children: t('configuration.default') }))] })),
        },
        ...(isAdmin ? [
            {
                title: t('projects.customLabel'),
                render: (_, record) => {
                    const current = customNames[record.id] ?? '';
                    const original = record.customName ?? '';
                    const isDirty = current !== original;
                    return (_jsxs(Space, { children: [_jsx(Input, { size: "small", style: { width: 200 }, placeholder: t('projects.customLabelPlaceholder'), value: current, onChange: (e) => setCustomNames((prev) => ({ ...prev, [record.id]: e.target.value })) }), _jsx(Button, { size: "small", type: "primary", disabled: !isDirty, loading: savingPhaseId === record.id, onClick: () => handleSaveCustomName(record), children: t('common.save') })] }));
                },
            },
            {
                title: t('configuration.default'),
                width: 160,
                render: (_, record) => {
                    const isDefault = phasesProject?.defaultPhaseId === record.id;
                    return (_jsx(Button, { size: "small", type: isDefault ? 'primary' : 'default', loading: settingDefaultId === record.id, onClick: () => handleToggleDefault(record), children: isDefault ? t('configuration.clearDefault') : t('configuration.setAsDefault') }));
                },
            },
        ] : []),
    ];
    const columns = [
        { title: t('common.name'), dataIndex: 'name', key: 'name' },
        { title: t('common.description'), dataIndex: 'description', key: 'description',
            render: (v) => v || '—' },
        {
            title: t('projects.phases'),
            key: 'phases',
            width: 120,
            render: (_, record) => (_jsx(Button, { size: "small", onClick: () => openPhasesModal(record), children: t('projects.managePhases') })),
        },
        ...(isAdmin ? [{
                title: t('common.actions'), key: 'actions',
                render: (_, record) => (_jsx(Popconfirm, { title: t('projects.deleteConfirm'), description: t('projects.deleteDescription'), onConfirm: () => handleDelete(record.id), okText: t('common.delete'), okButtonProps: { danger: true }, children: _jsx(Button, { danger: true, size: "small", loading: deletingId === record.id, children: t('common.delete') }) })),
            }] : []),
    ];
    if (loading)
        return _jsx(Spin, {});
    if (error)
        return _jsx(Alert, { type: "error", message: error });
    return (_jsxs(_Fragment, { children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }, children: [_jsx(Typography.Title, { level: 3, style: { margin: 0 }, children: t('projects.title') }), isAdmin && _jsx(Button, { type: "primary", onClick: openModal, children: t('projects.newProject') })] }), _jsx(Table, { rowKey: "id", dataSource: projects, columns: columns, pagination: { pageSize: 20 } }), _jsx(Modal, { title: t('projects.createProject'), open: modalOpen, onOk: handleSubmit, onCancel: () => setModalOpen(false), okText: t('common.create'), confirmLoading: submitting, children: _jsxs(Form, { form: form, layout: "vertical", style: { marginTop: 16 }, children: [_jsx(Form.Item, { name: "name", label: t('common.name'), rules: [{ required: true, message: t('projects.nameRequired') }], children: _jsx(Input, {}) }), _jsx(Form.Item, { name: "description", label: t('common.description'), children: _jsx(Input.TextArea, { rows: 3 }) })] }) }), _jsxs(Modal, { title: `${t('projects.phasesModalTitle')} — ${phasesProject?.name ?? ''}`, open: !!phasesProject, onCancel: () => { setPhasesProject(null); setPhasesError(null); }, footer: null, width: isAdmin ? 720 : 400, destroyOnClose: true, children: [phasesError && (_jsx(Alert, { message: phasesError, type: "error", showIcon: true, closable: true, onClose: () => setPhasesError(null), style: { marginBottom: 16 } })), _jsx(Table, { rowKey: "id", columns: phaseColumns, dataSource: phases, loading: phasesLoading, pagination: false, size: "small" })] })] }));
}
