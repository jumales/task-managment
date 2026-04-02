import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Alert,
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { DeleteOutlined, EditOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  createPhase,
  deleteNotificationTemplate,
  deletePhase,
  getNotificationTemplates,
  getPhases,
  getProjects,
  getTemplatePlaceholders,
  updateProject,
  upsertNotificationTemplate,
} from '../api/taskApi';
import type {
  ProjectNotificationTemplateResponse,
  TaskChangeType,
  TaskPhaseName,
  TaskPhaseResponse,
  TaskProjectResponse,
  TemplatePlaceholder,
} from '../api/types';

const PHASE_NAMES: TaskPhaseName[] = [
  'BACKLOG', 'TODO', 'IN_PROGRESS', 'IN_REVIEW', 'TESTING', 'DONE', 'RELEASED',
];

const ALL_EVENT_TYPES: TaskChangeType[] = [
  'TASK_CREATED',
  'STATUS_CHANGED',
  'COMMENT_ADDED',
  'PHASE_CHANGED',
  'PLANNED_WORK_CREATED',
  'BOOKED_WORK_CREATED',
  'BOOKED_WORK_UPDATED',
  'BOOKED_WORK_DELETED',
];

/** Row shape for the templates table — one row per event type. */
interface TemplateRow {
  eventType: TaskChangeType;
  template: ProjectNotificationTemplateResponse | null;
}

export function ConfigurationPage() {
  const { t } = useTranslation();
  return (
    <div>
      <Typography.Title level={3} style={{ marginTop: 0 }}>{t('configuration.title')}</Typography.Title>
      <Tabs
        defaultActiveKey="phases"
        items={[
          { key: 'phases',    label: t('configuration.phases'),    children: <PhasesTab /> },
          { key: 'templates', label: t('configuration.templates'), children: <TemplatesTab /> },
        ]}
      />
    </div>
  );
}

/** Phases tab — per-project phase management (create, delete, set default). */
function PhasesTab() {
  const { t } = useTranslation();
  const [projects,         setProjects]         = useState<TaskProjectResponse[]>([]);
  const [selectedProject,  setSelectedProject]  = useState<TaskProjectResponse | null>(null);
  const [phases,           setPhases]           = useState<TaskPhaseResponse[]>([]);
  const [loading,          setLoading]          = useState(false);
  const [error,            setError]            = useState<string | null>(null);
  const [modalOpen,        setModalOpen]        = useState(false);
  const [submitting,       setSubmitting]       = useState(false);
  const [deletingId,       setDeletingId]       = useState<string | null>(null);
  const [settingDefaultId, setSettingDefaultId] = useState<string | null>(null);
  const [form]                                  = Form.useForm();

  useEffect(() => {
    getProjects()
      .then(setProjects)
      .catch(() => setError(t('configuration.failedLoadProjects')));
  }, []);

  useEffect(() => {
    if (!selectedProject) return;
    loadPhases(selectedProject.id);
  }, [selectedProject?.id]);

  function loadPhases(projectId: string) {
    setLoading(true);
    getPhases(projectId)
      .then(setPhases)
      .catch(() => setError(t('configuration.failedLoadPhases')))
      .finally(() => setLoading(false));
  }

  function handleProjectChange(projectId: string) {
    setSelectedProject(projects.find((p) => p.id === projectId) ?? null);
    setPhases([]);
  }

  async function handleCreate() {
    if (!selectedProject) return;
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      await createPhase({ ...values, projectId: selectedProject.id });
      setModalOpen(false);
      form.resetFields();
      loadPhases(selectedProject.id);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      if (msg) setError(msg);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(id: string) {
    setDeletingId(id);
    try {
      await deletePhase(id);
      setPhases((prev) => prev.filter((ph) => ph.id !== id));
      // Clear the cached defaultPhaseId if the deleted phase was the default
      if (selectedProject?.defaultPhaseId === id) {
        setSelectedProject({ ...selectedProject, defaultPhaseId: null });
      }
    } catch {
      setError(t('configuration.failedDeletePhase'));
    } finally {
      setDeletingId(null);
    }
  }

  async function handleSetDefault(phase: TaskPhaseResponse) {
    if (!selectedProject) return;
    const newDefaultId = selectedProject.defaultPhaseId === phase.id ? null : phase.id;
    setSettingDefaultId(phase.id);
    try {
      const updated = await updateProject(selectedProject.id, {
        name:           selectedProject.name,
        description:    selectedProject.description,
        taskCodePrefix: selectedProject.taskCodePrefix,
        defaultPhaseId: newDefaultId,
      });
      setSelectedProject(updated);
    } catch {
      setError(t('configuration.failedSetDefault'));
    } finally {
      setSettingDefaultId(null);
    }
  }

  const columns: ColumnsType<TaskPhaseResponse> = [
    {
      title: t('configuration.phaseName'),
      dataIndex: 'name',
      render: (name: TaskPhaseName, record) => (
        <span>
          {name}
          {selectedProject?.defaultPhaseId === record.id && (
            <Tag color="blue" style={{ marginLeft: 8 }}>{t('configuration.default')}</Tag>
          )}
        </span>
      ),
    },
    {
      title: t('common.description'),
      dataIndex: 'description',
      render: (desc: string | null) => desc ?? '—',
    },
    {
      title: t('common.actions'),
      width: 200,
      render: (_, record) => {
        const isDefault = selectedProject?.defaultPhaseId === record.id;
        return (
          <Space>
            <Button
              size="small"
              type={isDefault ? 'primary' : 'default'}
              loading={settingDefaultId === record.id}
              onClick={() => handleSetDefault(record)}
            >
              {isDefault ? t('configuration.clearDefault') : t('configuration.setAsDefault')}
            </Button>
            <Popconfirm
              title={t('configuration.deletePhaseConfirm')}
              description={t('configuration.deletePhaseDescription')}
              onConfirm={() => handleDelete(record.id)}
              okText={t('common.delete')}
              okButtonProps={{ danger: true }}
            >
              <Button danger size="small" loading={deletingId === record.id} icon={<DeleteOutlined />} />
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  return (
    <div>
      {error && (
        <Alert message={error} type="error" showIcon closable onClose={() => setError(null)}
               style={{ marginBottom: 16 }} />
      )}

      <div style={{ marginBottom: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
        <Typography.Text strong>{t('common.project')}:</Typography.Text>
        <Select
          style={{ width: 280 }}
          placeholder={t('configuration.selectProject')}
          value={selectedProject?.id ?? null}
          onChange={handleProjectChange}
          options={projects.map((p) => ({ value: p.id, label: p.name }))}
        />
        {selectedProject && (
          <Button type="primary" onClick={() => setModalOpen(true)}>
            {t('configuration.addPhase')}
          </Button>
        )}
      </div>

      <Table<TaskPhaseResponse>
        rowKey="id"
        columns={columns}
        dataSource={selectedProject ? phases : []}
        loading={loading}
        pagination={false}
        locale={{
          emptyText: selectedProject
            ? t('configuration.emptyPhases')
            : t('configuration.emptyPhasesNoProject'),
        }}
      />

      <Modal
        title={t('configuration.createPhase')}
        open={modalOpen}
        onOk={handleCreate}
        onCancel={() => { setModalOpen(false); form.resetFields(); }}
        confirmLoading={submitting}
        okText={t('common.create')}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="name"
            label={t('configuration.phaseName')}
            rules={[{ required: true, message: t('configuration.nameRequired') }]}
          >
            <Select
              options={PHASE_NAMES.map((n) => ({ value: n, label: n }))}
              placeholder={t('configuration.selectPhaseName')}
            />
          </Form.Item>
          <Form.Item name="description" label={t('common.description')}>
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

/** Templates tab — per-project email template management. */
function TemplatesTab() {
  const { t } = useTranslation();
  const [projects,     setProjects]     = useState<TaskProjectResponse[]>([]);
  const [projectId,    setProjectId]    = useState<string | null>(null);
  const [templates,    setTemplates]    = useState<ProjectNotificationTemplateResponse[]>([]);
  const [placeholders, setPlaceholders] = useState<TemplatePlaceholder[]>([]);
  const [loadingList,  setLoadingList]  = useState(false);
  const [error,        setError]        = useState<string | null>(null);
  const [modalOpen,    setModalOpen]    = useState(false);
  const [submitting,   setSubmitting]   = useState(false);
  const [editingType,  setEditingType]  = useState<TaskChangeType | null>(null);
  const [deletingType, setDeletingType] = useState<TaskChangeType | null>(null);
  const [form]                          = Form.useForm();

  // Track which field (subject or body) was last focused so placeholder clicks insert there.
  const lastFocusedField = useRef<'subjectTemplate' | 'bodyTemplate'>('bodyTemplate');

  // Load project list once on mount.
  useEffect(() => {
    getProjects()
      .then(setProjects)
      .catch(() => setError(t('configuration.failedLoadProjects')));
  }, []);

  // Reload templates and fetch placeholders whenever the selected project changes.
  useEffect(() => {
    if (!projectId) return;
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
    if (!projectId) return;
    setLoadingList(true);
    getNotificationTemplates(projectId)
      .then(setTemplates)
      .catch(() => setError(t('configuration.failedLoad')))
      .finally(() => setLoadingList(false));
  }

  function openEdit(eventType: TaskChangeType, existing: ProjectNotificationTemplateResponse | null) {
    setEditingType(eventType);
    form.setFieldsValue({
      subjectTemplate: existing?.subjectTemplate ?? '',
      bodyTemplate:    existing?.bodyTemplate    ?? '',
    });
    setModalOpen(true);
  }

  async function handleSave() {
    if (!projectId || !editingType) return;
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      await upsertNotificationTemplate(projectId, editingType, values);
      setModalOpen(false);
      loadTemplates();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg ?? t('configuration.failedSave'));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(eventType: TaskChangeType) {
    if (!projectId) return;
    setDeletingType(eventType);
    try {
      await deleteNotificationTemplate(projectId, eventType);
      loadTemplates();
    } catch {
      setError(t('configuration.failedDelete'));
    } finally {
      setDeletingType(null);
    }
  }

  /** Inserts a placeholder token into the last-focused template field. */
  function insertPlaceholder(key: string) {
    const field = lastFocusedField.current;
    const current: string = form.getFieldValue(field) ?? '';
    form.setFieldValue(field, current + `{${key}}`);
  }

  // Build a Map once (O(n)) so each eventType lookup is O(1) instead of O(n) per row.
  const templatesByEvent = new Map(templates.map((tmpl) => [tmpl.eventType, tmpl]));
  const rows: TemplateRow[] = ALL_EVENT_TYPES.map((eventType) => ({
    eventType,
    template: templatesByEvent.get(eventType) ?? null,
  }));

  const columns: ColumnsType<TemplateRow> = [
    {
      title: t('configuration.event'),
      dataIndex: 'eventType',
      width: 200,
      render: (et: TaskChangeType) => t(`configuration.eventTypes.${et}`),
    },
    {
      title: t('configuration.subjectTemplate'),
      render: (_, row) =>
        row.template
          ? <Typography.Text ellipsis style={{ maxWidth: 300 }}>{row.template.subjectTemplate}</Typography.Text>
          : <Typography.Text type="secondary">{t('configuration.usingDefault')}</Typography.Text>,
    },
    {
      title: t('configuration.bodyTemplate'),
      render: (_, row) =>
        row.template
          ? <Typography.Text ellipsis style={{ maxWidth: 300 }}>{row.template.bodyTemplate}</Typography.Text>
          : <Typography.Text type="secondary">{t('configuration.usingDefault')}</Typography.Text>,
    },
    {
      title: t('common.actions'),
      width: 120,
      render: (_, row) => (
        <span>
          <Tooltip title={t('configuration.editTemplate')}>
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => openEdit(row.eventType, row.template)}
            />
          </Tooltip>
          {row.template && (
            <Popconfirm
              title={t('configuration.resetConfirm')}
              description={t('configuration.resetDescription')}
              onConfirm={() => handleDelete(row.eventType)}
              okText={t('configuration.resetToDefault')}
              okButtonProps={{ danger: true }}
            >
              <Tooltip title={t('configuration.resetToDefault')}>
                <Button
                  type="link"
                  size="small"
                  danger
                  icon={<DeleteOutlined />}
                  loading={deletingType === row.eventType}
                />
              </Tooltip>
            </Popconfirm>
          )}
        </span>
      ),
    },
  ];

  return (
    <div>
      {error && (
        <Alert message={error} type="error" showIcon closable onClose={() => setError(null)}
               style={{ marginBottom: 16 }} />
      )}

      <div style={{ marginBottom: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
        <Typography.Text strong>{t('common.project')}:</Typography.Text>
        <Select
          style={{ width: 280 }}
          placeholder={t('configuration.selectProject')}
          value={projectId}
          onChange={setProjectId}
          options={projects.map((p) => ({ value: p.id, label: p.name }))}
        />
      </div>

      <Table<TemplateRow>
        rowKey="eventType"
        columns={columns}
        dataSource={projectId ? rows : []}
        loading={loadingList}
        pagination={false}
        locale={{ emptyText: projectId ? t('configuration.emptyWithProject') : t('configuration.emptyWithoutProject') }}
      />

      <Modal
        title={editingType ? `${t('configuration.editTemplate')} — ${t(`configuration.eventTypes.${editingType}`)}` : ''}
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
        confirmLoading={submitting}
        okText={t('common.save')}
        width={680}
        destroyOnClose
      >
        {/* Placeholder catalogue */}
        {placeholders.length > 0 && (
          <div style={{ marginBottom: 16 }}>
            <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 6 }}>
              {t('configuration.insertTokenHint')}
            </Typography.Text>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
              {placeholders.map((ph) => (
                <Tooltip key={ph.key} title={ph.description}>
                  <Tag
                    style={{ cursor: 'pointer' }}
                    color="blue"
                    onClick={() => insertPlaceholder(ph.key)}
                  >
                    {`{${ph.key}}`}
                  </Tag>
                </Tooltip>
              ))}
            </div>
          </div>
        )}

        <Form form={form} layout="vertical">
          <Form.Item
            name="subjectTemplate"
            label={t('configuration.subject')}
            rules={[{ required: true, message: t('configuration.subjectRequired') }]}
          >
            <Input onFocus={() => { lastFocusedField.current = 'subjectTemplate'; }} />
          </Form.Item>
          <Form.Item
            name="bodyTemplate"
            label={t('configuration.body')}
            rules={[{ required: true, message: t('configuration.bodyRequired') }]}
          >
            <Input.TextArea
              rows={6}
              onFocus={() => { lastFocusedField.current = 'bodyTemplate'; }}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
