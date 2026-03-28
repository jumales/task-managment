import { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { DeleteOutlined, EditOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  deleteNotificationTemplate,
  getNotificationTemplates,
  getProjects,
  getTemplatePlaceholders,
  upsertNotificationTemplate,
} from '../api/taskApi';
import type {
  ProjectNotificationTemplateResponse,
  TaskChangeType,
  TaskProjectResponse,
  TemplatePlaceholder,
} from '../api/types';

const ALL_EVENT_TYPES: TaskChangeType[] = [
  'TASK_CREATED',
  'STATUS_CHANGED',
  'COMMENT_ADDED',
  'PHASE_CHANGED',
  'WORK_LOG_CREATED',
  'WORK_LOG_UPDATED',
  'WORK_LOG_DELETED',
];

const EVENT_TYPE_LABELS: Record<TaskChangeType, string> = {
  TASK_CREATED:     'Task created',
  STATUS_CHANGED:   'Status changed',
  COMMENT_ADDED:    'Comment added',
  PHASE_CHANGED:    'Phase changed',
  WORK_LOG_CREATED: 'Work log created',
  WORK_LOG_UPDATED: 'Work log updated',
  WORK_LOG_DELETED: 'Work log deleted',
};

/** Row shape for the templates table — one row per event type. */
interface TemplateRow {
  eventType: TaskChangeType;
  template: ProjectNotificationTemplateResponse | null;
}

export function ConfigurationPage() {
  return (
    <div>
      <Typography.Title level={3} style={{ marginTop: 0 }}>Configuration</Typography.Title>
      <Tabs
        defaultActiveKey="templates"
        items={[{ key: 'templates', label: 'Templates', children: <TemplatesTab /> }]}
      />
    </div>
  );
}

/** Templates tab — per-project email template management. */
function TemplatesTab() {
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
      .catch(() => setError('Failed to load projects.'));
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
      .catch(() => setError('Failed to load templates.'))
      .finally(() => setLoadingList(false));
  }, [projectId]);

  function loadTemplates() {
    if (!projectId) return;
    setLoadingList(true);
    getNotificationTemplates(projectId)
      .then(setTemplates)
      .catch(() => setError('Failed to load templates.'))
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
      setError(msg ?? 'Failed to save template.');
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
      setError('Failed to delete template.');
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

  const rows: TemplateRow[] = ALL_EVENT_TYPES.map((eventType) => ({
    eventType,
    template: templates.find((t) => t.eventType === eventType) ?? null,
  }));

  const columns: ColumnsType<TemplateRow> = [
    {
      title: 'Event',
      dataIndex: 'eventType',
      width: 200,
      render: (et: TaskChangeType) => EVENT_TYPE_LABELS[et],
    },
    {
      title: 'Subject template',
      render: (_, row) =>
        row.template
          ? <Typography.Text ellipsis style={{ maxWidth: 300 }}>{row.template.subjectTemplate}</Typography.Text>
          : <Typography.Text type="secondary">Using default</Typography.Text>,
    },
    {
      title: 'Body template',
      render: (_, row) =>
        row.template
          ? <Typography.Text ellipsis style={{ maxWidth: 300 }}>{row.template.bodyTemplate}</Typography.Text>
          : <Typography.Text type="secondary">Using default</Typography.Text>,
    },
    {
      title: 'Actions',
      width: 120,
      render: (_, row) => (
        <span>
          <Tooltip title="Edit template">
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => openEdit(row.eventType, row.template)}
            />
          </Tooltip>
          {row.template && (
            <Popconfirm
              title="Reset to default?"
              description="This will remove the custom template."
              onConfirm={() => handleDelete(row.eventType)}
              okText="Reset"
              okButtonProps={{ danger: true }}
            >
              <Tooltip title="Reset to default">
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
        <Typography.Text strong>Project:</Typography.Text>
        <Select
          style={{ width: 280 }}
          placeholder="Select a project"
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
        locale={{ emptyText: projectId ? 'No templates configured' : 'Select a project to manage its templates' }}
      />

      <Modal
        title={editingType ? `Edit template — ${EVENT_TYPE_LABELS[editingType]}` : ''}
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
        confirmLoading={submitting}
        okText="Save"
        width={680}
        destroyOnClose
      >
        {/* Placeholder catalogue */}
        {placeholders.length > 0 && (
          <div style={{ marginBottom: 16 }}>
            <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 6 }}>
              Click a token to insert it into the focused field:
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
            label="Subject"
            rules={[{ required: true, message: 'Subject is required' }]}
          >
            <Input
              onFocus={() => { lastFocusedField.current = 'subjectTemplate'; }}
            />
          </Form.Item>
          <Form.Item
            name="bodyTemplate"
            label="Body"
            rules={[{ required: true, message: 'Body is required' }]}
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
