import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import {
  Table, Tag, Typography, Alert, Spin, Button, Modal, Form, Input, Select,
  Space, Popconfirm, Progress, DatePicker, Steps,
} from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import type { InputRef } from 'antd';
import { getTasks, createTask, deleteTask, getProjects } from '../api/taskApi';
import { getUsers } from '../api/userApi';
import { searchTasks } from '../api/searchApi';
import type { TaskSummaryResponse, TaskStatus, TaskType, TaskProjectResponse, UserResponse } from '../api/types';
import { getTypeLabels } from './taskDetail/taskDetailConstants';

import { useAuth } from '../auth/AuthProvider';

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
  const { t }        = useTranslation();
  const navigate     = useNavigate();
  const { username } = useAuth();

  const [tasks,        setTasks]        = useState<TaskSummaryResponse[]>([]);
  const [totalTasks,   setTotalTasks]   = useState(0);
  const [currentPage,  setCurrentPage]  = useState(1);
  const [pageSize,     setPageSize]     = useState(20);
  const [projects,     setProjects]     = useState<TaskProjectResponse[]>([]);
  const [users,        setUsers]        = useState<UserResponse[]>([]);
  const [loading,      setLoading]      = useState(true);
  const [error,        setError]        = useState<string | null>(null);
  const [modalError,   setModalError]   = useState<string | null>(null);
  const [searchQuery,  setSearchQuery]  = useState('');
  const [activeSearch, setActiveSearch] = useState('');
  const searchInputRef = useRef<InputRef | null>(null);
  const [modalOpen,    setModalOpen]    = useState(false);
  const [wizardStep,   setWizardStep]   = useState(0);
  const [submitting,   setSubmitting]   = useState(false);
  const [deletingId,   setDeletingId]   = useState<string | null>(null);

  const [form] = Form.useForm();

  // Derived translation maps — recomputed on language change only
  const statusOptions = useMemo(() => [
    { label: t('tasks.statuses.TODO'),        value: 'TODO' as TaskStatus },
    { label: t('tasks.statuses.IN_PROGRESS'), value: 'IN_PROGRESS' as TaskStatus },
    { label: t('tasks.statuses.DONE'),        value: 'DONE' as TaskStatus },
  ], [t]);

  const typeLabels = useMemo(() => getTypeLabels(t), [t]);

  const typeOptions = useMemo(
    () => (Object.keys(typeLabels) as TaskType[]).map((k) => ({ label: typeLabels[k], value: k })),
    [typeLabels],
  );

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
          taskCode: null,
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
    setWizardStep(0);
    setModalError(null);
    form.resetFields();
    Promise.all([getProjects(), getUsers()])
      .then(([fetchedProjects, fetchedUsersPage]) => {
        const fetchedUsers = fetchedUsersPage.content;
        setProjects(fetchedProjects);
        setUsers(fetchedUsers);
        if (fetchedProjects.length === 1) {
          const projectId = fetchedProjects[0].id;
          form.setFieldValue('projectId', projectId);
        }
        const currentUser = fetchedUsers.find((u) => u.username === username);
        if (currentUser) form.setFieldValue('assignedUserId', currentUser.id);
        else if (fetchedUsers.length === 1) form.setFieldValue('assignedUserId', fetchedUsers[0].id);
      })
      .catch(() => setError(t('tasks.failedOptions')));
    setModalOpen(true);
  };

  /** Submits the create form. */
  const handleSubmit = () => {
    form.validateFields().catch(() => {}).then((values) => {
      if (!values) return;
      setSubmitting(true);
      const request = {
        ...values,
        type:        values.type ?? null,
        progress:    0,
        plannedStart: values.plannedStart?.toISOString(),
        plannedEnd:   values.plannedEnd?.toISOString(),
      };
      createTask(request)
        .then(() => {
          setModalOpen(false);
          loadTasks();
        })
        .catch((err) => {
          const message = err?.response?.data?.message ?? err?.message ?? t('tasks.failedCreate');
          setError(`${t('tasks.failedCreate')}: ${message}`);
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
        if (err?.response?.status === 409) {
          setError(t('tasks.deleteBlockedByRelations'));
        } else {
          const message = err?.response?.data?.message ?? err?.message ?? t('tasks.failedDelete');
          setError(`${t('tasks.failedDelete')}: ${message}`);
        }
      })
      .finally(() => setDeletingId(null));
  };

  const columns: ColumnsType<TaskSummaryResponse> = useMemo(() => [
    { title: t('tasks.taskCode'), dataIndex: 'taskCode', key: 'taskCode', width: 110,
      render: (taskCode: string | null) => taskCode ?? '—' },
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
  // eslint-disable-next-line react-hooks/exhaustive-deps
  ], [t, navigate, typeLabels, deletingId]);

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

      {/* Create Modal */}
      <Modal
        title={t('tasks.createTask')}
        open={modalOpen}
        onCancel={() => { setModalOpen(false); setWizardStep(0); setModalError(null); }}
        footer={null}
        width={560}
        forceRender
      >
        {modalError && <Alert type="error" message={modalError} style={{ marginBottom: 12 }} />}

        <Steps
          current={wizardStep}
          size="small"
          style={{ marginTop: 16, marginBottom: 24 }}
          items={[
            { title: t('tasks.wizardStep1') },
            { title: t('tasks.wizardStep2') },
            { title: t('tasks.wizardStep3') },
          ]}
        />
        <Form form={form} layout="vertical">
          {/* Step 1 — Title & Description */}
          <div style={{ display: wizardStep === 0 ? 'block' : 'none' }}>
            <Form.Item name="title" label={t('tasks.title_field')} rules={[{ required: true, message: t('tasks.titleRequired') }]}>
              <Input />
            </Form.Item>
            <Form.Item name="description" label={t('common.description')}>
              <Input.TextArea rows={3} />
            </Form.Item>
          </div>

          {/* Step 2 — Project, Type & Status */}
          <div style={{ display: wizardStep === 1 ? 'block' : 'none' }}>
            <Form.Item name="projectId" label={t('common.project')} rules={[{ required: true, message: t('tasks.projectRequired') }]}>
              <Select
                options={projects.map((p) => ({ label: p.name, value: p.id }))}
                placeholder={t('tasks.selectProject')}
              />
            </Form.Item>
            <Form.Item name="type" label={t('tasks.type')} rules={[{ required: true, message: t('tasks.typeRequired') }]}>
              <Select options={typeOptions} placeholder={t('tasks.selectType')} />
            </Form.Item>
            <Form.Item name="status" label={t('common.status')} initialValue="TODO" rules={[{ required: true }]}>
              <Select options={statusOptions} />
            </Form.Item>
          </div>

          {/* Step 3 — Assignee & Dates */}
          <div style={{ display: wizardStep === 2 ? 'block' : 'none' }}>
            <Form.Item name="assignedUserId" label={t('tasks.assignedTo')} rules={[{ required: true, message: t('tasks.userRequired') }]}>
              <Select options={users.map((u) => ({ label: u.name, value: u.id }))} placeholder={t('tasks.selectUser')} />
            </Form.Item>
            <Form.Item name="plannedStart" label={t('tasks.plannedStart')} rules={[{ required: true, message: t('tasks.plannedStartRequired') }]}>
              <DatePicker style={{ width: '100%' }} placeholder={t('tasks.selectDate')} />
            </Form.Item>
            <Form.Item
              name="plannedEnd"
              label={t('tasks.plannedEnd')}
              dependencies={['plannedStart']}
              rules={[
                { required: true, message: t('tasks.plannedEndRequired') },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    const start = getFieldValue('plannedStart');
                    if (!value || !start || value.isAfter(start)) return Promise.resolve();
                    return Promise.reject(new Error(t('tasks.plannedEndMustBeAfterStart')));
                  },
                }),
              ]}
            >
              <DatePicker style={{ width: '100%' }} placeholder={t('tasks.selectDate')} />
            </Form.Item>
          </div>
        </Form>

        {/* Wizard navigation */}
        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 8 }}>
          <Button
            disabled={wizardStep === 0}
            onClick={() => setWizardStep((s) => s - 1)}
          >
            {t('common.back')}
          </Button>
          <Space>
            <Button onClick={() => { setModalOpen(false); setWizardStep(0); }}>{t('common.cancel')}</Button>
            {wizardStep < 2 ? (
              <Button
                type="primary"
                onClick={() => {
                  const fieldsForStep = [
                    ['title'],
                    ['projectId', 'type', 'status'],
                  ][wizardStep];
                  form.validateFields(fieldsForStep)
                    .then(() => setWizardStep((s) => s + 1))
                    .catch(() => {});
                }}
              >
                {t('common.next')}
              </Button>
            ) : (
              <Button type="primary" loading={submitting} onClick={handleSubmit}>
                {t('common.create')}
              </Button>
            )}
          </Space>
        </div>
      </Modal>
    </>
  );
}
