import { useEffect, useState } from 'react';
import { Table, Typography, Alert, Spin, Button, Modal, Form, Input, Popconfirm } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { getProjects, createProject, deleteProject } from '../api/taskApi';
import type { TaskProjectResponse } from '../api/types';

/** Displays all projects and allows creating and deleting them. */
export function ProjectsPage() {
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
      .catch(() => setError('Failed to load projects.'))
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
          const message = err?.response?.data?.message ?? err?.message ?? 'Failed to create project.';
          setError(`Failed to create project: ${message}`);
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
        const message = err?.response?.data?.message ?? err?.message ?? 'Failed to delete project.';
        setError(`Failed to delete project: ${message}`);
      })
      .finally(() => setDeletingId(null));
  };

  const columns: ColumnsType<TaskProjectResponse> = [
    { title: 'Name',        dataIndex: 'name',        key: 'name' },
    { title: 'Description', dataIndex: 'description', key: 'description',
      render: (v: string) => v || '—' },
    {
      title: 'Actions', key: 'actions',
      render: (_, record) => (
        <Popconfirm
          title="Delete project?"
          description="This action cannot be undone."
          onConfirm={() => handleDelete(record.id)}
          okText="Delete"
          okButtonProps={{ danger: true }}
        >
          <Button danger size="small" loading={deletingId === record.id}>Delete</Button>
        </Popconfirm>
      ),
    },
  ];

  if (loading) return <Spin />;
  if (error)   return <Alert type="error" message={error} />;

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Typography.Title level={3} style={{ margin: 0 }}>Projects</Typography.Title>
        <Button type="primary" onClick={openModal}>New Project</Button>
      </div>

      <Table rowKey="id" dataSource={projects} columns={columns} pagination={{ pageSize: 20 }} />

      <Modal
        title="Create Project"
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        okText="Create"
        confirmLoading={submitting}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="name" label="Name" rules={[{ required: true, message: 'Name is required' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
