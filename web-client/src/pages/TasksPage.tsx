import { useEffect, useState } from 'react';
import {
  Table, Tag, Typography, Alert, Spin, Button, Modal, Form, Input, Select,
  Drawer, Space, List, Popconfirm,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { getTasks, createTask, updateTask, deleteTask, addComment, getTaskComments, getProjects } from '../api/taskApi';
import { getUsers } from '../api/userApi';
import type { TaskResponse, TaskCommentResponse, TaskStatus, TaskProjectResponse, UserResponse } from '../api/types';

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

/** Displays all tasks and allows creating, editing, deleting, and commenting on them. */
export function TasksPage() {
  const [tasks,         setTasks]         = useState<TaskResponse[]>([]);
  const [projects,      setProjects]      = useState<TaskProjectResponse[]>([]);
  const [users,         setUsers]         = useState<UserResponse[]>([]);
  const [loading,       setLoading]       = useState(true);
  const [error,         setError]         = useState<string | null>(null);
  const [modalOpen,     setModalOpen]     = useState(false);
  const [editingTask,   setEditingTask]   = useState<TaskResponse | null>(null);
  const [submitting,    setSubmitting]    = useState(false);
  const [deletingId,    setDeletingId]    = useState<string | null>(null);
  const [detailTask,       setDetailTask]       = useState<TaskResponse | null>(null);
  const [comments,         setComments]         = useState<TaskCommentResponse[]>([]);
  const [commentsLoading,  setCommentsLoading]  = useState(false);
  const [comment,          setComment]          = useState('');
  const [addingComment,    setAddingComment]    = useState(false);

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

  const refreshDropdowns = () => {
    getProjects().then(setProjects).catch(() => setError('Failed to load projects.'));
    getUsers().then(setUsers).catch(() => setError('Failed to load users.'));
  };

  useEffect(() => {
    loadTasks();
    refreshDropdowns();
  }, []);

  /** Opens the modal in create mode, auto-selecting any dropdown with a single option. */
  const openCreateModal = () => {
    setEditingTask(null);
    form.resetFields();
    Promise.all([getProjects(), getUsers()])
      .then(([fetchedProjects, fetchedUsers]) => {
        setProjects(fetchedProjects);
        setUsers(fetchedUsers);
        if (fetchedProjects.length === 1) form.setFieldValue('projectId',      fetchedProjects[0].id);
        if (fetchedUsers.length    === 1) form.setFieldValue('assignedUserId', fetchedUsers[0].id);
      })
      .catch(() => setError('Failed to load options.'));
    setModalOpen(true);
  };

  /** Opens the modal in edit mode, pre-filled with the given task's values. */
  const openEditModal = (task: TaskResponse) => {
    setEditingTask(task);
    form.setFieldsValue({
      title:          task.title,
      description:    task.description,
      status:         task.status,
      projectId:      task.project.id,
      assignedUserId: task.assignedUser?.id ?? null,
    });
    refreshDropdowns();
    setModalOpen(true);
  };

  /** Submits the form — calls createTask or updateTask based on edit mode. */
  const handleSubmit = () => {
    form.validateFields().catch(() => {}).then((values) => {
      if (!values) return;
      setSubmitting(true);
      const request = { ...values, phaseId: null };
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
          const action  = editingTask ? 'update' : 'create';
          const message = err?.response?.data?.message ?? err?.message ?? `Failed to ${action} task.`;
          setError(`Failed to ${action} task: ${message}`);
        })
        .finally(() => setSubmitting(false));
    });
  };

  /** Opens the detail drawer and asynchronously loads the task's comments. */
  const openDetailDrawer = (task: TaskResponse) => {
    setDetailTask(task);
    setComments([]);
    setCommentsLoading(true);
    getTaskComments(task.id)
      .then(setComments)
      .catch(() => setError('Failed to load comments.'))
      .finally(() => setCommentsLoading(false));
  };

  /** Soft-deletes a task after confirmation. */
  const handleDelete = (id: string) => {
    setDeletingId(id);
    deleteTask(id)
      .then(() => loadTasks())
      .catch((err) => {
        const message = err?.response?.data?.message ?? err?.message ?? 'Failed to delete task.';
        setError(`Failed to delete task: ${message}`);
      })
      .finally(() => setDeletingId(null));
  };

  /** Posts a new comment and appends it to the local comments list. */
  const handleAddComment = (task: TaskResponse) => {
    if (!comment.trim()) return;
    setAddingComment(true);
    addComment(task.id, comment.trim())
      .then((created) => {
        setComments((prev) => [...prev, created]);
        setComment('');
      })
      .catch((err) => {
        const message = err?.response?.data?.message ?? err?.message ?? 'Failed to add comment.';
        setError(`Failed to add comment: ${message}`);
      })
      .finally(() => setAddingComment(false));
  };

  const columns: ColumnsType<TaskResponse> = [
    { title: 'Title',       dataIndex: 'title',                  key: 'title' },
    { title: 'Project',     dataIndex: ['project', 'name'],      key: 'project' },
    { title: 'Assigned to', dataIndex: ['assignedUser', 'name'], key: 'user',
      render: (name: string | null) => name ?? '—' },
    { title: 'Status', dataIndex: 'status', key: 'status',
      render: (status: TaskStatus) => <Tag color={STATUS_COLORS[status]}>{status}</Tag> },
    {
      title: 'Actions', key: 'actions',
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => openDetailDrawer(record)}>View</Button>
          <Button size="small" onClick={() => openEditModal(record)}>Edit</Button>
          <Popconfirm
            title="Delete task?"
            description="This action cannot be undone."
            onConfirm={() => handleDelete(record.id)}
            okText="Delete"
            okButtonProps={{ danger: true }}
          >
            <Button danger size="small" loading={deletingId === record.id}>Delete</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  if (loading) return <Spin />;
  if (error)   return <Alert type="error" message={error} />;

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Typography.Title level={3} style={{ margin: 0 }}>Tasks</Typography.Title>
        <Button type="primary" onClick={openCreateModal}>New Task</Button>
      </div>

      <Table rowKey="id" dataSource={tasks} columns={columns} pagination={{ pageSize: 20 }} />

      {/* Create / Edit Modal */}
      <Modal
        title={editingTask ? 'Edit Task' : 'Create Task'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => { setModalOpen(false); setEditingTask(null); }}
        okText={editingTask ? 'Save' : 'Create'}
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

      {/* Task Detail Drawer */}
      <Drawer
        title={detailTask?.title}
        open={detailTask !== null}
        onClose={() => { setDetailTask(null); setComments([]); setComment(''); }}
        width={480}
      >
        {detailTask && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <div><strong>Description:</strong> {detailTask.description || '—'}</div>
            <div>
              <strong>Status:</strong>{' '}
              <Tag color={STATUS_COLORS[detailTask.status]}>{detailTask.status}</Tag>
            </div>
            <div><strong>Project:</strong> {detailTask.project.name}</div>
            <div><strong>Assigned to:</strong> {detailTask.assignedUser?.name ?? '—'}</div>

            <Typography.Title level={5} style={{ marginTop: 16, marginBottom: 0 }}>Comments</Typography.Title>

            {commentsLoading ? (
              <Spin size="small" />
            ) : comments.length === 0 ? (
              <Typography.Text type="secondary">No comments yet.</Typography.Text>
            ) : (
              <List
                dataSource={comments}
                renderItem={(c) => (
                  <List.Item key={c.id}>
                    <List.Item.Meta
                      title={c.content}
                      description={new Date(c.createdAt).toLocaleString()}
                    />
                  </List.Item>
                )}
              />
            )}

            <Input.TextArea
              rows={3}
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              placeholder="Add a comment..."
              aria-label="New comment"
            />
            <Button
              type="primary"
              loading={addingComment}
              onClick={() => handleAddComment(detailTask)}
              disabled={!comment.trim()}
            >
              Add Comment
            </Button>
          </Space>
        )}
      </Drawer>
    </>
  );
}
