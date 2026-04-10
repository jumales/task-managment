import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Table, Typography, Alert, Spin, Button, Modal, Form, Input, Popconfirm, Space, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { getProjects, createProject, deleteProject, getPhases, updatePhase, updateProject } from '../api/taskApi';
import { useAuth } from '../auth/AuthProvider';
import type { TaskPhaseResponse, TaskProjectResponse } from '../api/types';
import { formatPhaseEnum } from '../utils/phaseUtils';

/** Displays all projects. Admins can create and delete projects, and manage phase custom labels. */
export function ProjectsPage() {
  const { t } = useTranslation();
  const { isAdmin } = useAuth();
  const [projects,   setProjects]   = useState<TaskProjectResponse[]>([]);
  const [loading,    setLoading]    = useState(true);
  const [error,      setError]      = useState<string | null>(null);
  const [modalOpen,  setModalOpen]  = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  // Phase management modal state
  const [phasesProject,    setPhasesProject]    = useState<TaskProjectResponse | null>(null);
  const [phases,           setPhases]           = useState<TaskPhaseResponse[]>([]);
  const [phasesLoading,    setPhasesLoading]    = useState(false);
  const [phasesError,      setPhasesError]      = useState<string | null>(null);
  const [customNames,      setCustomNames]      = useState<Record<string, string>>({});
  const [savingPhaseId,    setSavingPhaseId]    = useState<string | null>(null);
  const [settingDefaultId, setSettingDefaultId] = useState<string | null>(null);

  const [form] = Form.useForm();

  const loadProjects = () =>
    getProjects()
      .then(setProjects)
      .catch(() => setError(t('projects.failedLoad')))
      .finally(() => setLoading(false));

  useEffect(() => { loadProjects(); }, []);

  const openModal = () => {
    form.resetFields();
    setModalOpen(true);
  };

  const handleSubmit = () => {
    form.validateFields().catch(() => {}).then((values) => {
      if (!values) return;
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

  const handleDelete = (id: string) => {
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
  const openPhasesModal = (project: TaskProjectResponse) => {
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
  const handleSaveCustomName = async (phase: TaskPhaseResponse) => {
    const newCustomName = customNames[phase.id]?.trim() || null;
    setSavingPhaseId(phase.id);
    try {
      const updated = await updatePhase(phase.id, { name: phase.name, customName: newCustomName, projectId: phase.projectId });
      setPhases((prev) => prev.map((p) => (p.id === phase.id ? updated : p)));
      setCustomNames((prev) => ({ ...prev, [phase.id]: updated.customName ?? '' }));
    } catch {
      setPhasesError(t('projects.failedSavePhase'));
    } finally {
      setSavingPhaseId(null);
    }
  };

  /** Toggles the default phase for the current project. */
  const handleToggleDefault = async (phase: TaskPhaseResponse) => {
    if (!phasesProject) return;
    const newDefaultId = phasesProject.defaultPhaseId === phase.id ? null : phase.id;
    setSettingDefaultId(phase.id);
    try {
      const updated = await updateProject(phasesProject.id, {
        name:           phasesProject.name,
        description:    phasesProject.description,
        taskCodePrefix: phasesProject.taskCodePrefix,
        defaultPhaseId: newDefaultId,
      });
      setPhasesProject(updated);
      setProjects((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
    } catch {
      setPhasesError(t('projects.failedSavePhase'));
    } finally {
      setSettingDefaultId(null);
    }
  };

  const phaseColumns: ColumnsType<TaskPhaseResponse> = [
    {
      title: t('tasks.phase'),
      dataIndex: 'name',
      width: 140,
      render: (name: string, record) => (
        <Space>
          {formatPhaseEnum(name)}
          {phasesProject?.defaultPhaseId === record.id && (
            <Tag color="blue">{t('configuration.default')}</Tag>
          )}
        </Space>
      ),
    },
    ...(isAdmin ? [
      {
        title: t('projects.customLabel'),
        render: (_: unknown, record: TaskPhaseResponse) => {
          const current = customNames[record.id] ?? '';
          const original = record.customName ?? '';
          const isDirty = current !== original;
          return (
            <Space>
              <Input
                size="small"
                style={{ width: 200 }}
                placeholder={t('projects.customLabelPlaceholder')}
                value={current}
                onChange={(e) =>
                  setCustomNames((prev) => ({ ...prev, [record.id]: e.target.value }))
                }
              />
              <Button
                size="small"
                type="primary"
                disabled={!isDirty}
                loading={savingPhaseId === record.id}
                onClick={() => handleSaveCustomName(record)}
              >
                {t('common.save')}
              </Button>
            </Space>
          );
        },
      },
      {
        title: t('configuration.default'),
        width: 160,
        render: (_: unknown, record: TaskPhaseResponse) => {
          const isDefault = phasesProject?.defaultPhaseId === record.id;
          return (
            <Button
              size="small"
              type={isDefault ? 'primary' : 'default'}
              loading={settingDefaultId === record.id}
              onClick={() => handleToggleDefault(record)}
            >
              {isDefault ? t('configuration.clearDefault') : t('configuration.setAsDefault')}
            </Button>
          );
        },
      },
    ] : []),
  ];

  const columns: ColumnsType<TaskProjectResponse> = [
    { title: t('common.name'),        dataIndex: 'name',        key: 'name' },
    { title: t('common.description'), dataIndex: 'description', key: 'description',
      render: (v: string) => v || '—' },
    {
      title: t('projects.phases'),
      key: 'phases',
      width: 120,
      render: (_: unknown, record: TaskProjectResponse) => (
        <Button size="small" onClick={() => openPhasesModal(record)}>
          {t('projects.managePhases')}
        </Button>
      ),
    },
    ...(isAdmin ? [{
      title: t('common.actions'), key: 'actions',
      render: (_: unknown, record: TaskProjectResponse) => (
        <Popconfirm
          title={t('projects.deleteConfirm')}
          description={t('projects.deleteDescription')}
          onConfirm={() => handleDelete(record.id)}
          okText={t('common.delete')}
          okButtonProps={{ danger: true }}
        >
          <Button danger size="small" loading={deletingId === record.id}>{t('common.delete')}</Button>
        </Popconfirm>
      ),
    }] : []),
  ];

  if (loading) return <Spin />;
  if (error)   return <Alert type="error" message={error} />;

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Typography.Title level={3} style={{ margin: 0 }}>{t('projects.title')}</Typography.Title>
        {isAdmin && <Button type="primary" onClick={openModal}>{t('projects.newProject')}</Button>}
      </div>

      <Table rowKey="id" dataSource={projects} columns={columns} pagination={{ pageSize: 20 }} />

      {/* Create project modal */}
      <Modal
        title={t('projects.createProject')}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        okText={t('common.create')}
        confirmLoading={submitting}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="name" label={t('common.name')} rules={[{ required: true, message: t('projects.nameRequired') }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label={t('common.description')}>
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Manage phases modal */}
      <Modal
        title={`${t('projects.phasesModalTitle')} — ${phasesProject?.name ?? ''}`}
        open={!!phasesProject}
        onCancel={() => { setPhasesProject(null); setPhasesError(null); }}
        footer={null}
        width={isAdmin ? 720 : 400}
        destroyOnHidden
      >
        {phasesError && (
          <Alert
            message={phasesError}
            type="error"
            showIcon
            closable
            onClose={() => setPhasesError(null)}
            style={{ marginBottom: 16 }}
          />
        )}
        <Table<TaskPhaseResponse>
          rowKey="id"
          columns={phaseColumns}
          dataSource={phases}
          loading={phasesLoading}
          pagination={false}
          size="small"
        />
      </Modal>
    </>
  );
}
