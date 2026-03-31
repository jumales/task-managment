import { useEffect, useRef, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import {
  Table, Tag, Typography, Alert, Spin, Button, Modal, Form, Input, Select,
  Space, Popconfirm, Progress, InputNumber, DatePicker,
} from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { getTasks, createTask, updateTask, deleteTask, getProjects } from '../api/taskApi';
import { getUsers } from '../api/userApi';
import { searchTasks } from '../api/searchApi';
import type { TaskSummaryResponse, TaskStatus, TaskType, TaskProjectResponse, UserResponse } from '../api/types';

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

/** Displays all tasks and allows creating, editing, and deleting them. Detail view opens as a full page. */
export function TasksPage() {
  const { t }    = useTranslation();
  const navigate = useNavigate();

  const [tasks,        setTasks]        = useState<TaskSummaryResponse[]>([]);
  const [totalTasks,   setTotalTasks]   = useState(0);
  const [currentPage,  setCurrentPage]  = useState(1);
  const [pageSize,     setPageSize]     = useState(20);
  const [projects,     setProjects]     = useState<TaskProjectResponse[]>([]);
  const [users,        setUsers]        = useState<UserResponse[]>([]);
  const [loading,      setLoading]      = useState(true);
  const [error,        setError]        = useState<string | null>(null);
  const [searchQuery,  setSearchQuery]  = useState('');
  const [activeSearch, setActiveSearch] = useState('');
  const searchInputRef = useRef<HTMLInputElement | null>(null);
  const [modalOpen,    setModalOpen]    = useState(false);
  const [editingTask,  setEditingTask]  = useState<TaskSummaryResponse | null>(null);
  const [submitting,   setSubmitting]   = useState(false);
  const [deletingId,   setDeletingId]   = useState<string | null>(null);

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
          type: null,
          progress: 0,
          assignedUserId: d.assignedUserId ?? null,
          assignedUserName: d.assignedUserName ?? null,
          projectId: d.projectId ?? null,
          projectName: d.projectName ?? '—',
          phaseId: d.phaseId ?? null,
          phaseName: d.phaseName ?? null,
        } as TaskSummaryResponse));
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
  const openEditModal = (task: TaskSummaryResponse) => {
    setEditingTask(task);
    form.setFieldsValue({
      title:          task.title,
      description:    task.description,
      status:         task.status,
      type:           task.type ?? null,
      progress:       task.progress,
      projectId:      task.projectId,
      assignedUserId: task.assignedUserId ?? null,
    });
    refreshDropdowns();
    setModalOpen(true);
  };

  /** Submits the form — calls createTask or updateTask based on edit mode. */
  const handleSubmit = () => {
    form.validateFields().catch(() => {}).then((values) => {
      if (!values) return;
      setSubmitting(true);
      const request = {
        ...values,
        phaseId: null,
        type:    values.type ?? null,
        progress: values.progress ?? 0,
        // plannedStart/End only on create; format dayjs to ISO string
        ...(editingTask ? {} : {
          plannedStart: values.plannedStart?.toISOString(),
          plannedEnd:   values.plannedEnd?.toISOString(),
        }),
      };
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

  const columns: ColumnsType<TaskSummaryResponse> = [
    { title: t('tasks.title_field'), dataIndex: 'title',       key: 'title' },
    { title: t('common.project'),    dataIndex: 'projectName', key: 'project' },
    { title: t('tasks.assignedTo'),  key: 'user',
      render: (_: unknown, record: TaskSummaryResponse) => record.assignedUserName ?? '—',
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
          <Button size="small" onClick={() => navigate(`/tasks/${record.id}`)}>{t('tasks.view')}</Button>
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
          {/* Planned dates are only required on create */}
          {!editingTask && (
            <>
              <Form.Item name="plannedStart" label={t('tasks.plannedStart')} rules={[{ required: true, message: t('tasks.plannedStartRequired') }]}>
                <DatePicker showTime style={{ width: '100%' }} placeholder={t('tasks.selectDate')} />
              </Form.Item>
              <Form.Item name="plannedEnd" label={t('tasks.plannedEnd')} rules={[{ required: true, message: t('tasks.plannedEndRequired') }]}>
                <DatePicker showTime style={{ width: '100%' }} placeholder={t('tasks.selectDate')} />
              </Form.Item>
            </>
          )}
        </Form>
      </Modal>
    </>
  );
}
