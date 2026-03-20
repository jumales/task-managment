import { useEffect, useState } from 'react';
import { Table, Tag, Typography, Alert, Spin, Button, Modal, Form, Input, Select } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { getTasks, createTask, getProjects } from '../api/taskApi';
import { getUsers } from '../api/userApi';
import type { TaskResponse, TaskStatus, TaskProjectResponse, UserResponse } from '../api/types';

const STATUS_COLORS: Record<TaskStatus, string> = {
  TODO:        'default',
  IN_PROGRESS: 'blue',
  DONE:        'green',
};

const STATUS_OPTIONS: { label: string; value: TaskStatus }[] = [
  { label: 'To Do',       value: 'TODO' },
  { label: 'In Progress', value: 'IN_PROGRESS' },
  { label: 'Done',        value: 'DONE' },
];

/** Displays all tasks and allows creating new ones. */
export function TasksPage() {
  const [tasks,    setTasks]    = useState<TaskResponse[]>([]);
  const [projects, setProjects] = useState<TaskProjectResponse[]>([]);
  const [users,    setUsers]    = useState<UserResponse[]>([]);
  const [loading,  setLoading]  = useState(true);
  const [error,    setError]    = useState<string | null>(null);
  const [modalOpen,   setModalOpen]   = useState(false);
  const [submitting,  setSubmitting]  = useState(false);

  const [form] = Form.useForm();

  const loadTasks = () =>
    getTasks()
      .then(setTasks)
      .catch((err) => {
        const status  = err?.response?.status;
        const message = err?.response?.data?.message ?? err?.message ?? 'Unknown error';
        setError(`Failed to load tasks [${status}]: ${message}`);
      })
      .finally(() => setLoading(false));

  useEffect(() => {
    loadTasks();
    getProjects().then(setProjects).catch(() => setError('Failed to load projects.'));
    getUsers().then(setUsers).catch(() => setError('Failed to load users.'));
  }, []);

  const openModal = () => {
    form.resetFields();
    getProjects().then(setProjects).catch(() => setError('Failed to load projects.'));
    getUsers().then(setUsers).catch(() => setError('Failed to load users.'));
    setModalOpen(true);
  };

  const handleSubmit = () => {
    // validateFields() rejects when fields are invalid — the form renders
    // error messages automatically so no additional handling is needed here.
    form.validateFields().catch(() => {}).then((values) => {
      if (!values) return;
      setSubmitting(true);
      createTask({ ...values, phaseId: null })
        .then(() => {
          setModalOpen(false);
          loadTasks();
        })
        .catch((err) => {
          const message = err?.response?.data?.message ?? err?.message ?? 'Failed to create task.';
          setError(`Failed to create task: ${message}`);
        })
        .finally(() => setSubmitting(false));
    });
  };

  const columns: ColumnsType<TaskResponse> = [
    { title: 'Title',       dataIndex: 'title',       key: 'title' },
    { title: 'Project',     dataIndex: ['project', 'name'], key: 'project' },
    { title: 'Assigned to', dataIndex: ['assignedUser', 'name'], key: 'user',
      render: (name: string | null) => name ?? '—' },
    { title: 'Status', dataIndex: 'status', key: 'status',
      render: (status: TaskStatus) => <Tag color={STATUS_COLORS[status]}>{status}</Tag> },
  ];

  if (loading) return <Spin />;
  if (error)   return <Alert type="error" message={error} />;

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Typography.Title level={3} style={{ margin: 0 }}>Tasks</Typography.Title>
        <Button type="primary" onClick={openModal}>New Task</Button>
      </div>

      <Table rowKey="id" dataSource={tasks} columns={columns} pagination={{ pageSize: 20 }} />

      <Modal
        title="Create Task"
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        okText="Create"
        confirmLoading={submitting}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="title" label="Title" rules={[{ required: true, message: 'Title is required' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="status" label="Status" initialValue="TODO" rules={[{ required: true }]}>
            <Select options={STATUS_OPTIONS} />
          </Form.Item>
          <Form.Item name="projectId" label="Project" rules={[{ required: true, message: 'Project is required' }]}>
            <Select
              options={projects.map((p) => ({ label: p.name, value: p.id }))}
              placeholder="Select a project"
            />
          </Form.Item>
          <Form.Item name="assignedUserId" label="Assign to" rules={[{ required: true, message: 'User is required' }]}>
            <Select
              options={users.map((u) => ({ label: u.name, value: u.id }))}
              placeholder="Select a user"
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
