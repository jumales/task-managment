import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Table, Typography, Alert, Spin, Button, Modal, Form, Input, Popconfirm } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { getProjects, createProject, deleteProject } from '../api/taskApi';
import { useAuth } from '../auth/AuthProvider';
import type { TaskProjectResponse } from '../api/types';

/** Displays all projects. Admins can create and delete projects. */
export function ProjectsPage() {
  const { t } = useTranslation();
  const { isAdmin } = useAuth();
  const [projects,   setProjects]   = useState<TaskProjectResponse[]>([]);
  const [loading,    setLoading]    = useState(true);
  const [error,      setError]      = useState<string | null>(null);
  const [modalOpen,  setModalOpen]  = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const [form] = Form.useForm();

  const loadProjects = () =>
    getProjects()
      .then(setProjects)
      .catch(() => setError(t('projects.failedLoad')))
      .finally(() => setLoading(false));

  useEffect(() => { loadProjects(); }, []);

  /** Opens the create project modal. */
  const openModal = () => {
    form.resetFields();
    setModalOpen(true);
  };

  /** Submits the create project form. */
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

  /** Soft-deletes a project after confirmation. */
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

  const columns: ColumnsType<TaskProjectResponse> = [
    { title: t('common.name'),        dataIndex: 'name',        key: 'name' },
    { title: t('common.description'), dataIndex: 'description', key: 'description',
      render: (v: string) => v || '—' },
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
    </>
  );
}
