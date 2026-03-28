import { useEffect, useRef, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Table, Tag, Typography, Alert, Spin, Button, Modal, Form, Input, Select,
  Drawer, Space, List, Popconfirm, Divider, Progress, InputNumber,
} from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { getTasks, createTask, updateTask, deleteTask, addComment, getTaskComments, getProjects, addParticipant, removeParticipant, getWorkLogs, createWorkLog, updateWorkLog, deleteWorkLog } from '../api/taskApi';
import { getUsers } from '../api/userApi';
import { searchTasks } from '../api/searchApi';
import type { TaskParticipantResponse, TaskParticipantRole, TaskResponse, TaskCommentResponse, TaskStatus, TaskType, TaskProjectResponse, UserResponse, WorkType, TaskWorkLogResponse } from '../api/types';

const STATUS_COLORS: Record<TaskStatus, string> = {
  TODO:        'default',
  IN_PROGRESS: 'blue',
  DONE:        'green',
};

const TYPE_COLORS: Record<TaskType, string> = {
  FEATURE:        'purple',
  BUG_FIXING:     'red',
  TESTING:        'cyan',
  PLANNING:       'gold',
  TECHNICAL_DEBT: 'orange',
  DOCUMENTATION:  'geekblue',
  OTHER:          'default',
};

/** Displays all tasks and allows creating, editing, deleting, and commenting on them. */
export function TasksPage() {
  const { t } = useTranslation();

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

  const [workLogs,          setWorkLogs]          = useState<TaskWorkLogResponse[]>([]);
  const [workLogsLoading,   setWorkLogsLoading]   = useState(false);
  const [editingWorkLog,    setEditingWorkLog]     = useState<TaskWorkLogResponse | null>(null);
  const [workLogUserId,     setWorkLogUserId]      = useState<string | null>(null);
  const [workLogType,       setWorkLogType]        = useState<WorkType>('DEVELOPMENT');
  const [workLogPlanned,    setWorkLogPlanned]     = useState<number>(0);
  const [workLogBooked,     setWorkLogBooked]      = useState<number>(0);
  const [savingWorkLog,     setSavingWorkLog]      = useState(false);
  const [deletingWorkLogId, setDeletingWorkLogId]  = useState<string | null>(null);

  const [form] = Form.useForm();

  // Derived translation maps — recomputed on language change
  const statusOptions = [
    { label: t('tasks.statuses.TODO'),        value: 'TODO' as TaskStatus },
    { label: t('tasks.statuses.IN_PROGRESS'), value: 'IN_PROGRESS' as TaskStatus },
    { label: t('tasks.statuses.DONE'),        value: 'DONE' as TaskStatus },
  ];

  const typeLabels: Record<TaskType, string> = {
    FEATURE:        t('tasks.types.FEATURE'),
    BUG_FIXING:     t('tasks.types.BUG_FIXING'),
    TESTING:        t('tasks.types.TESTING'),
    PLANNING:       t('tasks.types.PLANNING'),
    TECHNICAL_DEBT: t('tasks.types.TECHNICAL_DEBT'),
    DOCUMENTATION:  t('tasks.types.DOCUMENTATION'),
    OTHER:          t('tasks.types.OTHER'),
  };

  const typeOptions = (Object.keys(typeLabels) as TaskType[]).map((k) => ({ label: typeLabels[k], value: k }));

  const workTypeLabels: Record<WorkType, string> = {
    DEVELOPMENT:   t('tasks.workTypes.DEVELOPMENT'),
    TESTING:       t('tasks.workTypes.TESTING'),
    CODE_REVIEW:   t('tasks.workTypes.CODE_REVIEW'),
    DESIGN:        t('tasks.workTypes.DESIGN'),
    PLANNING:      t('tasks.workTypes.PLANNING'),
    DOCUMENTATION: t('tasks.workTypes.DOCUMENTATION'),
    DEPLOYMENT:    t('tasks.workTypes.DEPLOYMENT'),
    MEETING:       t('tasks.workTypes.MEETING'),
    OTHER:         t('tasks.workTypes.OTHER'),
  };

  const workTypeOptions = (Object.keys(workTypeLabels) as WorkType[]).map((w) => ({
    label: workTypeLabels[w],
    value: w,
  }));

  const loadTasks = (page = currentPage, size = pageSize) =>
    getTasks({ page: page - 1, size })
      .then((data) => {
        setTasks(data.content);
        setTotalTasks(data.totalElements);
      })
      .catch((err) => {
        const status  = err?.response?.status;
        const message = err?.response?.data?.message ?? err?.message ?? 'Unknown error';
        setError(`${t('tasks.failedLoad')} [${status}]: ${message}`);
      })
      .finally(() => setLoading(false));

  const refreshDropdowns = () => {
    getProjects().then(setProjects).catch(() => setError(t('projects.failedLoad')));
    getUsers().then((data) => setUsers(data.content)).catch(() => setError(t('users.failedLoad')));
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
      .catch(() => setError(t('tasks.searchFailed')))
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
      .catch(() => setError(t('tasks.failedOptions')));
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
          const label   = editingTask ? t('tasks.failedUpdate') : t('tasks.failedCreate');
          const message = err?.response?.data?.message ?? err?.message ?? label;
          setError(`${label}: ${message}`);
        })
        .finally(() => setSubmitting(false));
    });
  };

  /** Opens the detail drawer and asynchronously loads the task's comments and work logs. */
  const openDetailDrawer = (task: TaskResponse) => {
    setDetailTask(task);
    setDrawerParticipants(task.participants ?? []);
    setNewParticipantUserId(null);
    setNewParticipantRole('VIEWER');
    setComments([]);
    setCommentsLoading(true);
    setWorkLogs([]);
    setWorkLogsLoading(true);
    resetWorkLogForm();
    getTaskComments(task.id)
      .then(setComments)
      .catch(() => setError(t('tasks.failedLoadComments')))
      .finally(() => setCommentsLoading(false));
    getWorkLogs(task.id)
      .then(setWorkLogs)
      .catch(() => setError(t('tasks.failedLoadWorkLogs')))
      .finally(() => setWorkLogsLoading(false));
  };

  const resetWorkLogForm = () => {
    setEditingWorkLog(null);
    setWorkLogUserId(null);
    setWorkLogType('DEVELOPMENT');
    setWorkLogPlanned(0);
    setWorkLogBooked(0);
  };

  const startEditWorkLog = (log: TaskWorkLogResponse) => {
    setEditingWorkLog(log);
    setWorkLogUserId(log.userId);
    setWorkLogType(log.workType);
    setWorkLogPlanned(Number(log.plannedHours));
    setWorkLogBooked(Number(log.bookedHours));
  };

  /** Saves (creates or updates) a work log entry and refreshes the list. */
  const handleSaveWorkLog = () => {
    if (!detailTask || !workLogUserId) return;
    setSavingWorkLog(true);
    const request = { userId: workLogUserId, workType: workLogType, plannedHours: workLogPlanned, bookedHours: workLogBooked };
    const apiCall = editingWorkLog
      ? updateWorkLog(detailTask.id, editingWorkLog.id, request)
      : createWorkLog(detailTask.id, request);
    apiCall
      .then((saved) => {
        setWorkLogs((prev) =>
          editingWorkLog ? prev.map((l) => (l.id === saved.id ? saved : l)) : [...prev, saved]
        );
        resetWorkLogForm();
      })
      .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedSaveWorkLog')))
      .finally(() => setSavingWorkLog(false));
  };

  /** Soft-deletes a work log entry and removes it from the local list. */
  const handleDeleteWorkLog = (workLogId: string) => {
    if (!detailTask) return;
    setDeletingWorkLogId(workLogId);
    deleteWorkLog(detailTask.id, workLogId)
      .then(() => setWorkLogs((prev) => prev.filter((l) => l.id !== workLogId)))
      .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedDeleteWorkLog')))
      .finally(() => setDeletingWorkLogId(null));
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
      .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedAddParticipant')))
      .finally(() => setAddingParticipant(false));
  };

  /** Removes a participant from the open task and updates the local participants list. */
  const handleRemoveParticipant = (taskId: string, participantId: string) => {
    setRemovingPId(participantId);
    removeParticipant(taskId, participantId)
      .then(() => setDrawerParticipants((prev) => prev.filter((p) => p.id !== participantId)))
      .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedRemoveParticipant')))
      .finally(() => setRemovingPId(null));
  };

  /** Soft-deletes a task after confirmation. */
  const handleDelete = (id: string) => {
    setDeletingId(id);
    deleteTask(id)
      .then(() => loadTasks())
      .catch((err) => {
        const message = err?.response?.data?.message ?? err?.message ?? t('tasks.failedDelete');
        setError(`${t('tasks.failedDelete')}: ${message}`);
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
        const message = err?.response?.data?.message ?? err?.message ?? t('tasks.failedAddComment');
        setError(`${t('tasks.failedAddComment')}: ${message}`);
      })
      .finally(() => setAddingComment(false));
  };

  const columns: ColumnsType<TaskResponse> = [
    { title: t('tasks.title_field'), dataIndex: 'title',             key: 'title' },
    { title: t('common.project'),    dataIndex: ['project', 'name'], key: 'project' },
    { title: t('tasks.assignedTo'),  key: 'user',
      render: (_: unknown, record: TaskResponse) => {
        const assignee = record.participants?.find((p) => p.role === 'ASSIGNEE');
        return assignee?.userName ?? '—';
      },
    },
    { title: t('tasks.type'), dataIndex: 'type', key: 'type',
      render: (type: TaskType | null) => type
        ? <Tag color={TYPE_COLORS[type]}>{typeLabels[type]}</Tag>
        : '—' },
    { title: t('common.status'), dataIndex: 'status', key: 'status',
      render: (status: TaskStatus) => <Tag color={STATUS_COLORS[status]}>{t(`tasks.statuses.${status}`)}</Tag> },
    { title: t('tasks.progress'), dataIndex: 'progress', key: 'progress', width: 140,
      render: (progress: number) => (
        <Progress percent={progress} size="small" strokeColor={progress === 100 ? '#52c41a' : undefined} />
      ) },
    {
      title: t('common.actions'), key: 'actions',
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => openDetailDrawer(record)}>{t('tasks.view')}</Button>
          <Button size="small" onClick={() => openEditModal(record)}>{t('common.edit')}</Button>
          <Popconfirm
            title={t('tasks.deleteConfirm')}
            description={t('tasks.deleteDescription')}
            onConfirm={() => handleDelete(record.id)}
            okText={t('common.delete')}
            okButtonProps={{ danger: true }}
          >
            <Button danger size="small" loading={deletingId === record.id}>{t('common.delete')}</Button>
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
        <Typography.Title level={3} style={{ margin: 0 }}>{t('tasks.title')}</Typography.Title>
        <Space>
          <Input
            ref={searchInputRef}
            placeholder={t('tasks.searchPlaceholder')}
            prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onPressEnter={handleSearch}
            allowClear
            autoFocus
            style={{ width: 240 }}
          />
          <Button type="primary" onClick={openCreateModal}>{t('tasks.newTask')}</Button>
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
        title={editingTask ? t('tasks.editTask') : t('tasks.createTask')}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => { setModalOpen(false); setEditingTask(null); }}
        okText={editingTask ? t('common.save') : t('common.create')}
        confirmLoading={submitting}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="title" label={t('tasks.title_field')} rules={[{ required: true, message: t('tasks.titleRequired') }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label={t('common.description')}>
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="status" label={t('common.status')} initialValue="TODO" rules={[{ required: true }]}>
            <Select options={statusOptions} />
          </Form.Item>
          <Form.Item name="type" label={t('tasks.type')}>
            <Select options={typeOptions} placeholder={t('tasks.selectType')} allowClear />
          </Form.Item>
          <Form.Item name="progress" label={t('tasks.progressPct')} initialValue={0}>
            <InputNumber min={0} max={100} style={{ width: '100%' }} addonAfter="%" />
          </Form.Item>
          <Form.Item name="projectId" label={t('common.project')} rules={[{ required: true, message: t('tasks.projectRequired') }]}>
            <Select
              options={projects.map((p) => ({ label: p.name, value: p.id }))}
              placeholder={t('tasks.selectProject')}
            />
          </Form.Item>
          <Form.Item name="assignedUserId" label={t('tasks.assignedTo')} rules={[{ required: true, message: t('tasks.userRequired') }]}>
            <Select
              options={users.map((u) => ({ label: u.name, value: u.id }))}
              placeholder={t('tasks.selectUser')}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* Task Detail Drawer */}
      <Drawer
        title={detailTask?.title}
        open={detailTask !== null}
        onClose={() => { setDetailTask(null); setComments([]); setComment(''); setDrawerParticipants([]); setWorkLogs([]); resetWorkLogForm(); }}
        width={480}
      >
        {detailTask && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <div><strong>{t('common.description')}:</strong> {detailTask.description || '—'}</div>
            <div>
              <strong>{t('common.status')}:</strong>{' '}
              <Tag color={STATUS_COLORS[detailTask.status]}>{t(`tasks.statuses.${detailTask.status}`)}</Tag>
            </div>
            {detailTask.type && (
              <div>
                <strong>{t('tasks.type')}:</strong>{' '}
                <Tag color={TYPE_COLORS[detailTask.type]}>{typeLabels[detailTask.type]}</Tag>
              </div>
            )}
            <div>
              <strong>{t('tasks.progress')}:</strong>
              <Progress
                percent={detailTask.progress}
                strokeColor={detailTask.progress === 100 ? '#52c41a' : undefined}
                style={{ marginTop: 4 }}
              />
            </div>
            <div><strong>{t('common.project')}:</strong> {detailTask.project.name}</div>

            <Divider orientation="left" orientationMargin={0} style={{ marginBottom: 4 }}>{t('tasks.participants')}</Divider>
            <List
              size="small"
              dataSource={drawerParticipants}
              locale={{ emptyText: t('tasks.noParticipants') }}
              renderItem={(p) => (
                <List.Item
                  key={p.id}
                  actions={[
                    <Popconfirm
                      key="remove"
                      title={t('tasks.removeParticipant')}
                      onConfirm={() => handleRemoveParticipant(detailTask.id, p.id)}
                      okText={t('common.remove')}
                      okButtonProps={{ danger: true }}
                    >
                      <Button danger size="small" loading={removingPId === p.id}>{t('common.remove')}</Button>
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
                placeholder={t('tasks.selectUser')}
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
                {t('common.add')}
              </Button>
            </Space.Compact>

            <Divider orientation="left" orientationMargin={0} style={{ marginBottom: 4 }}>{t('tasks.workLogs')}</Divider>
            {workLogsLoading ? (
              <Spin size="small" />
            ) : (
              <>
                <List
                  size="small"
                  dataSource={workLogs}
                  locale={{ emptyText: t('tasks.noWorkLogs') }}
                  renderItem={(log) => (
                    <List.Item
                      key={log.id}
                      actions={[
                        <Button key="edit" size="small" onClick={() => startEditWorkLog(log)}>{t('common.edit')}</Button>,
                        <Popconfirm
                          key="del"
                          title={t('tasks.deleteWorkLog')}
                          onConfirm={() => handleDeleteWorkLog(log.id)}
                          okText={t('common.delete')}
                          okButtonProps={{ danger: true }}
                        >
                          <Button danger size="small" loading={deletingWorkLogId === log.id}>{t('common.delete')}</Button>
                        </Popconfirm>,
                      ]}
                    >
                      <Space direction="vertical" size={0}>
                        <Space>
                          <Tag color="blue">{workTypeLabels[log.workType]}</Tag>
                          <Typography.Text strong>{log.userName ?? log.userId}</Typography.Text>
                        </Space>
                        <Typography.Text type="secondary">
                          {t('tasks.planned')}: <strong>{log.plannedHours}h</strong>
                          {' · '}
                          {t('tasks.booked')}: <strong>{log.bookedHours}h</strong>
                        </Typography.Text>
                      </Space>
                    </List.Item>
                  )}
                />
                <div style={{ marginTop: 8, padding: '8px 0', borderTop: '1px solid #f0f0f0' }}>
                  <Typography.Text strong style={{ display: 'block', marginBottom: 6 }}>
                    {editingWorkLog ? t('tasks.editWorkLog') : t('tasks.addWorkLog')}
                  </Typography.Text>
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <Select
                      style={{ width: '100%' }}
                      placeholder={t('tasks.selectUser')}
                      value={workLogUserId}
                      onChange={setWorkLogUserId}
                      options={users.map((u) => ({ label: u.name, value: u.id }))}
                    />
                    <Select
                      style={{ width: '100%' }}
                      value={workLogType}
                      onChange={setWorkLogType}
                      options={workTypeOptions}
                    />
                    <Space style={{ width: '100%' }}>
                      {!editingWorkLog && (
                        <InputNumber
                          min={0}
                          step={1}
                          precision={0}
                          value={workLogPlanned}
                          onChange={(v) => setWorkLogPlanned(v ?? 0)}
                          addonBefore={t('tasks.planned')}
                          addonAfter="h"
                          style={{ flex: 1 }}
                        />
                      )}
                      <InputNumber
                        min={0}
                        step={1}
                        precision={0}
                        value={workLogBooked}
                        onChange={(v) => setWorkLogBooked(v ?? 0)}
                        addonBefore={t('tasks.booked')}
                        addonAfter="h"
                        style={{ flex: 1 }}
                      />
                    </Space>
                    <Space>
                      <Button
                        type="primary"
                        loading={savingWorkLog}
                        disabled={!workLogUserId}
                        onClick={handleSaveWorkLog}
                      >
                        {editingWorkLog ? t('common.save') : t('common.add')}
                      </Button>
                      {editingWorkLog && (
                        <Button onClick={resetWorkLogForm}>{t('common.cancel')}</Button>
                      )}
                    </Space>
                  </Space>
                </div>
              </>
            )}

            <Typography.Title level={5} style={{ marginTop: 16, marginBottom: 0 }}>{t('tasks.comments')}</Typography.Title>

            {commentsLoading ? (
              <Spin size="small" />
            ) : comments.length === 0 ? (
              <Typography.Text type="secondary">{t('tasks.noComments')}</Typography.Text>
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
              placeholder={t('tasks.addCommentPlaceholder')}
              aria-label="New comment"
            />
            <Button
              type="primary"
              loading={addingComment}
              onClick={() => handleAddComment(detailTask)}
              disabled={!comment.trim()}
            >
              {t('tasks.addComment')}
            </Button>
          </Space>
        )}
      </Drawer>
    </>
  );
}
