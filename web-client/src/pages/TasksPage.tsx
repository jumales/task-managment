import { useEffect, useRef, useState, useCallback } from 'react';
import {
  Table, Tag, Typography, Alert, Spin, Button, Modal, Form, Input, Select,
  Drawer, Space, List, Popconfirm, Divider, Progress, InputNumber,
} from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { getTasks, createTask, updateTask, deleteTask, addComment, getTaskComments, getProjects, addParticipant, removeParticipant } from '../api/taskApi';
import { getUsers } from '../api/userApi';
import { searchTasks } from '../api/searchApi';
import type { TaskParticipantResponse, TaskParticipantRole, TaskResponse, TaskCommentResponse, TaskStatus, TaskType, TaskProjectResponse, UserResponse } from '../api/types';

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

const TYPE_COLORS: Record<TaskType, string> = {
  FEATURE:        'purple',
  BUG_FIXING:     'red',
  TESTING:        'cyan',
  PLANNING:       'gold',
  TECHNICAL_DEBT: 'orange',
  DOCUMENTATION:  'geekblue',
  OTHER:          'default',
};

const TYPE_LABELS: Record<TaskType, string> = {
  FEATURE:        'Feature',
  BUG_FIXING:     'Bug Fix',
  TESTING:        'Testing',
  PLANNING:       'Planning',
  TECHNICAL_DEBT: 'Tech Debt',
  DOCUMENTATION:  'Docs',
  OTHER:          'Other',
};

const TYPE_OPTIONS: { label: string; value: TaskType }[] = (
  Object.keys(TYPE_LABELS) as TaskType[]
).map((t) => ({ label: TYPE_LABELS[t], value: t }));

/** Displays all tasks and allows creating, editing, deleting, and commenting on them. */
export function TasksPage() {
  const [tasks,         setTasks]         = useState<TaskResponse[]>([]);
  const [totalTasks,    setTotalTasks]    = useState(0);
  const [currentPage,   setCurrentPage]   = useState(1);
  const [pageSize,      setPageSize]      = useState(20);
  const [projects,      setProjects]      = useState<TaskProjectResponse[]>([]);
  const [users,         setUsers]         = useState<UserResponse[]>([]);
  const [loading,       setLoading]       = useState(true);
  const [error,         setError]         = useState<string | null>(null);
  const [searchQuery,   setSearchQuery]   = useState('');
  const [activeSearch,  setActiveSearch]  = useState('');
  const searchInputRef = useRef<HTMLInputElement | null>(null);
  const [modalOpen,     setModalOpen]     = useState(false);
  const [editingTask,   setEditingTask]   = useState<TaskResponse | null>(null);
  const [submitting,    setSubmitting]    = useState(false);
  const [deletingId,    setDeletingId]    = useState<string | null>(null);
  const [detailTask,         setDetailTask]         = useState<TaskResponse | null>(null);
  const [comments,           setComments]           = useState<TaskCommentResponse[]>([]);
  const [commentsLoading,    setCommentsLoading]    = useState(false);
  const [comment,            setComment]            = useState('');
  const [addingComment,      setAddingComment]      = useState(false);
  const [drawerParticipants, setDrawerParticipants] = useState<TaskParticipantResponse[]>([]);
  const [removingPId,        setRemovingPId]        = useState<string | null>(null);
  const [addingParticipant,  setAddingParticipant]  = useState(false);
  const [newParticipantUserId, setNewParticipantUserId] = useState<string | null>(null);
  const [newParticipantRole,   setNewParticipantRole]   = useState<TaskParticipantRole>('VIEWER');

  const [form] = Form.useForm();

  const loadTasks = (page = currentPage, size = pageSize) =>
    getTasks({ page: page - 1, size })
      .then((data) => {
        setTasks(data.content);
        setTotalTasks(data.totalElements);
      })
      .catch((err) => {
        const status  = err?.response?.status;
        const message = err?.response?.data?.message ?? err?.message ?? 'Unknown error';
        setError(`Failed to load tasks [${status}]: ${message}`);
      })
      .finally(() => setLoading(false));

  const refreshDropdowns = () => {
    getProjects().then(setProjects).catch(() => setError('Failed to load projects.'));
    getUsers().then((data) => setUsers(data.content)).catch(() => setError('Failed to load users.'));
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
          project: { id: d.projectId ?? '', name: d.projectName ?? '—', description: '' },
          participants: d.assignedUserName
            ? [{ id: d.assignedUserId ?? '', userId: d.assignedUserId ?? '', userName: d.assignedUserName, userEmail: null, role: 'ASSIGNEE' as TaskParticipantRole }]
            : [],
          phase: null,
        } as TaskResponse));
        setTasks(mapped);
        setTotalTasks(mapped.length);
      })
      .catch(() => setError('Search failed.'))
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

  const handleTableChange = (pagination: TablePaginationConfig) => {
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
    form.resetFields();
    Promise.all([getProjects(), getUsers()])
      .then(([fetchedProjects, fetchedUsersPage]) => {
        const fetchedUsers = fetchedUsersPage.content;
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
    const assignee = task.participants.find((p) => p.role === 'ASSIGNEE');
    form.setFieldsValue({
      title:          task.title,
      description:    task.description,
      status:         task.status,
      type:           task.type ?? null,
      progress:       task.progress,
      projectId:      task.project.id,
      assignedUserId: assignee?.userId ?? null,
    });
    refreshDropdowns();
    setModalOpen(true);
  };

  /** Submits the form — calls createTask or updateTask based on edit mode. */
  const handleSubmit = () => {
    form.validateFields().catch(() => {}).then((values) => {
      if (!values) return;
      setSubmitting(true);
      const request = { ...values, phaseId: null, type: values.type ?? null, progress: values.progress ?? 0 };
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
    setDrawerParticipants(task.participants ?? []);
    setNewParticipantUserId(null);
    setNewParticipantRole('VIEWER');
    setComments([]);
    setCommentsLoading(true);
    getTaskComments(task.id)
      .then(setComments)
      .catch(() => setError('Failed to load comments.'))
      .finally(() => setCommentsLoading(false));
  };

  /** Adds a participant to the open task and refreshes the local participants list. */
  const handleAddParticipant = () => {
    if (!detailTask || !newParticipantUserId) return;
    setAddingParticipant(true);
    addParticipant(detailTask.id, { userId: newParticipantUserId, role: newParticipantRole })
      .then((created) => {
        setDrawerParticipants((prev) => [...prev, created]);
        setNewParticipantUserId(null);
      })
      .catch((err) => setError(err?.response?.data?.message ?? 'Failed to add participant.'))
      .finally(() => setAddingParticipant(false));
  };

  /** Removes a participant from the open task and updates the local participants list. */
  const handleRemoveParticipant = (taskId: string, participantId: string) => {
    setRemovingPId(participantId);
    removeParticipant(taskId, participantId)
      .then(() => setDrawerParticipants((prev) => prev.filter((p) => p.id !== participantId)))
      .catch((err) => setError(err?.response?.data?.message ?? 'Failed to remove participant.'))
      .finally(() => setRemovingPId(null));
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
    { title: 'Assigned to', key: 'user',
      render: (_: unknown, record: TaskResponse) => {
        const assignee = record.participants?.find((p) => p.role === 'ASSIGNEE');
        return assignee?.userName ?? '—';
      },
    },
    { title: 'Type', dataIndex: 'type', key: 'type',
      render: (type: TaskType | null) => type
        ? <Tag color={TYPE_COLORS[type]}>{TYPE_LABELS[type]}</Tag>
        : '—' },
    { title: 'Status', dataIndex: 'status', key: 'status',
      render: (status: TaskStatus) => <Tag color={STATUS_COLORS[status]}>{status}</Tag> },
    { title: 'Progress', dataIndex: 'progress', key: 'progress', width: 140,
      render: (progress: number) => (
        <Progress percent={progress} size="small" strokeColor={progress === 100 ? '#52c41a' : undefined} />
      ) },
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
        <Space>
          <Input
            ref={searchInputRef}
            placeholder="Search tasks…"
            prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onPressEnter={handleSearch}
            allowClear
            autoFocus
            style={{ width: 240 }}
          />
          <Button type="primary" onClick={openCreateModal}>New Task</Button>
        </Space>
      </div>

      <Table
        rowKey="id"
        dataSource={tasks}
        columns={columns}
        pagination={activeSearch
          ? false
          : { current: currentPage, pageSize, total: totalTasks, showSizeChanger: true }}
        onChange={activeSearch ? undefined : handleTableChange}
      />

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
          <Form.Item name="type" label="Type">
            <Select options={TYPE_OPTIONS} placeholder="Select type (optional)" allowClear />
          </Form.Item>
          <Form.Item name="progress" label="Progress (%)" initialValue={0}>
            <InputNumber min={0} max={100} style={{ width: '100%' }} addonAfter="%" />
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
        onClose={() => { setDetailTask(null); setComments([]); setComment(''); setDrawerParticipants([]); }}
        width={480}
      >
        {detailTask && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <div><strong>Description:</strong> {detailTask.description || '—'}</div>
            <div>
              <strong>Status:</strong>{' '}
              <Tag color={STATUS_COLORS[detailTask.status]}>{detailTask.status}</Tag>
            </div>
            {detailTask.type && (
              <div>
                <strong>Type:</strong>{' '}
                <Tag color={TYPE_COLORS[detailTask.type]}>{TYPE_LABELS[detailTask.type]}</Tag>
              </div>
            )}
            <div>
              <strong>Progress:</strong>
              <Progress
                percent={detailTask.progress}
                strokeColor={detailTask.progress === 100 ? '#52c41a' : undefined}
                style={{ marginTop: 4 }}
              />
            </div>
            <div><strong>Project:</strong> {detailTask.project.name}</div>

            <Divider orientation="left" orientationMargin={0} style={{ marginBottom: 4 }}>Participants</Divider>
            <List
              size="small"
              dataSource={drawerParticipants}
              locale={{ emptyText: 'No participants yet.' }}
              renderItem={(p) => (
                <List.Item
                  key={p.id}
                  actions={[
                    <Popconfirm
                      key="remove"
                      title="Remove participant?"
                      onConfirm={() => handleRemoveParticipant(detailTask.id, p.id)}
                      okText="Remove"
                      okButtonProps={{ danger: true }}
                    >
                      <Button danger size="small" loading={removingPId === p.id}>Remove</Button>
                    </Popconfirm>,
                  ]}
                >
                  <Space>
                    <Tag color="blue">{p.role}</Tag>
                    {p.userName ?? p.userId}
                  </Space>
                </List.Item>
              )}
            />
            <Space.Compact style={{ width: '100%' }}>
              <Select
                style={{ flex: 1 }}
                placeholder="Select user"
                value={newParticipantUserId}
                onChange={setNewParticipantUserId}
                options={users.map((u) => ({ label: u.name, value: u.id }))}
              />
              <Select
                style={{ width: 120 }}
                value={newParticipantRole}
                onChange={setNewParticipantRole}
                options={(['ASSIGNEE', 'VIEWER', 'REVIEWER'] as TaskParticipantRole[]).map((r) => ({ label: r, value: r }))}
              />
              <Button
                type="primary"
                loading={addingParticipant}
                disabled={!newParticipantUserId}
                onClick={handleAddParticipant}
              >
                Add
              </Button>
            </Space.Compact>

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
