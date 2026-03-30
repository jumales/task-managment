import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  Alert, Button, Card, Col, DatePicker, Descriptions, Divider, Input, InputNumber,
  List, Modal, Popconfirm, Progress, Row, Select, Space, Spin, Tabs, Tag, Typography,
} from 'antd';
import { ArrowLeftOutlined, CalendarOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import {
  getTask, getTimelines, setTimeline, deleteTimeline,
  getWorkLogs, createWorkLog, updateWorkLog, deleteWorkLog,
  getParticipants, addParticipant, removeParticipant,
  getTaskComments, addComment,
} from '../api/taskApi';
import { getUsers } from '../api/userApi';
import type {
  TaskResponse, TaskTimelineResponse, TimelineState,
  TaskWorkLogResponse, TaskParticipantResponse, TaskParticipantRole,
  TaskCommentResponse, TaskStatus, TaskType, WorkType, UserResponse,
} from '../api/types';

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

const TIMELINE_STATES: TimelineState[] = ['PLANNED_START', 'PLANNED_END', 'REAL_START', 'REAL_END'];

/** Full-page view for a single task: overview, timeline, work logs, participants, and comments. */
export function TaskDetailPage() {
  const { id }   = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { t }    = useTranslation();

  const [task,    setTask]    = useState<TaskResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error,   setError]   = useState<string | null>(null);
  const [users,   setUsers]   = useState<UserResponse[]>([]);

  // ── Timeline ─────────────────────────────────────────────────────────────
  const [timelines,        setTimelines]        = useState<TaskTimelineResponse[]>([]);
  const [tlModalOpen,      setTlModalOpen]      = useState(false);
  const [editingState,     setEditingState]     = useState<TimelineState | null>(null);
  const [tlUserId,         setTlUserId]         = useState<string | null>(null);
  const [tlTimestamp,      setTlTimestamp]      = useState<Dayjs | null>(null);
  const [savingTimeline,   setSavingTimeline]   = useState(false);
  const [deletingTlState,  setDeletingTlState]  = useState<TimelineState | null>(null);

  // ── Work logs ────────────────────────────────────────────────────────────
  const [workLogs,       setWorkLogs]       = useState<TaskWorkLogResponse[]>([]);
  const [wlLoading,      setWlLoading]      = useState(false);
  const [editingWl,      setEditingWl]      = useState<TaskWorkLogResponse | null>(null);
  const [wlUserId,       setWlUserId]       = useState<string | null>(null);
  const [wlType,         setWlType]         = useState<WorkType>('DEVELOPMENT');
  const [wlPlanned,      setWlPlanned]      = useState(0);
  const [wlBooked,       setWlBooked]       = useState(0);
  const [savingWl,       setSavingWl]       = useState(false);
  const [deletingWlId,   setDeletingWlId]   = useState<string | null>(null);

  // ── Participants ─────────────────────────────────────────────────────────
  const [participants,    setParticipants]    = useState<TaskParticipantResponse[]>([]);
  const [newPUserId,      setNewPUserId]      = useState<string | null>(null);
  const [newPRole,        setNewPRole]        = useState<TaskParticipantRole>('VIEWER');
  const [addingP,         setAddingP]         = useState(false);
  const [removingPId,     setRemovingPId]     = useState<string | null>(null);

  // ── Comments ─────────────────────────────────────────────────────────────
  const [comments,     setComments]     = useState<TaskCommentResponse[]>([]);
  const [newComment,   setNewComment]   = useState('');
  const [addingCmt,    setAddingCmt]    = useState(false);

  // ── Translation maps ─────────────────────────────────────────────────────
  const typeLabels: Record<TaskType, string> = {
    FEATURE:        t('tasks.types.FEATURE'),
    BUG_FIXING:     t('tasks.types.BUG_FIXING'),
    TESTING:        t('tasks.types.TESTING'),
    PLANNING:       t('tasks.types.PLANNING'),
    TECHNICAL_DEBT: t('tasks.types.TECHNICAL_DEBT'),
    DOCUMENTATION:  t('tasks.types.DOCUMENTATION'),
    OTHER:          t('tasks.types.OTHER'),
  };

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

  // ── Initial load ─────────────────────────────────────────────────────────
  useEffect(() => {
    if (!id) return;
    setLoading(true);
    Promise.all([
      getTask(id),
      getTimelines(id),
      getParticipants(id),
      getTaskComments(id),
      getUsers(),
    ])
      .then(([taskData, timelinesData, participantsData, commentsData, usersPage]) => {
        setTask(taskData);
        setTimelines(timelinesData);
        setParticipants(participantsData);
        setComments(commentsData);
        setUsers(usersPage.content);
      })
      .catch((err) => setError(err?.message ?? t('tasks.failedLoad')))
      .finally(() => setLoading(false));

    setWlLoading(true);
    getWorkLogs(id)
      .then(setWorkLogs)
      .catch(() => setError(t('tasks.failedLoadWorkLogs')))
      .finally(() => setWlLoading(false));
  }, [id]);

  // ── Timeline handlers ─────────────────────────────────────────────────────
  const openTlModal = (state: TimelineState) => {
    const existing = timelines.find((tl) => tl.state === state);
    setEditingState(state);
    setTlUserId(existing?.setByUserId ?? null);
    setTlTimestamp(existing ? dayjs(existing.timestamp) : null);
    setTlModalOpen(true);
  };

  const handleSaveTimeline = () => {
    if (!id || !editingState || !tlUserId || !tlTimestamp) return;
    setSavingTimeline(true);
    setTimeline(id, editingState, { setByUserId: tlUserId, timestamp: tlTimestamp.toISOString() })
      .then((saved) => {
        setTimelines((prev) => {
          const idx = prev.findIndex((tl) => tl.state === saved.state);
          return idx >= 0
            ? prev.map((tl, i) => (i === idx ? saved : tl))
            : [...prev, saved];
        });
        setTlModalOpen(false);
      })
      .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedSaveTimeline')))
      .finally(() => setSavingTimeline(false));
  };

  const handleDeleteTimeline = (state: TimelineState) => {
    if (!id) return;
    setDeletingTlState(state);
    deleteTimeline(id, state)
      .then(() => setTimelines((prev) => prev.filter((tl) => tl.state !== state)))
      .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedDeleteTimeline')))
      .finally(() => setDeletingTlState(null));
  };

  // ── Work log handlers ─────────────────────────────────────────────────────
  const resetWlForm = () => {
    setEditingWl(null);
    setWlUserId(null);
    setWlType('DEVELOPMENT');
    setWlPlanned(0);
    setWlBooked(0);
  };

  const handleSaveWorkLog = () => {
    if (!id || !wlUserId) return;
    setSavingWl(true);
    const request = { userId: wlUserId, workType: wlType, plannedHours: wlPlanned, bookedHours: wlBooked };
    const apiCall = editingWl
      ? updateWorkLog(id, editingWl.id, request)
      : createWorkLog(id, request);
    apiCall
      .then((saved) => {
        setWorkLogs((prev) =>
          editingWl ? prev.map((l) => (l.id === saved.id ? saved : l)) : [...prev, saved]
        );
        resetWlForm();
      })
      .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedSaveWorkLog')))
      .finally(() => setSavingWl(false));
  };

  const handleDeleteWorkLog = (workLogId: string) => {
    if (!id) return;
    setDeletingWlId(workLogId);
    deleteWorkLog(id, workLogId)
      .then(() => setWorkLogs((prev) => prev.filter((l) => l.id !== workLogId)))
      .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedDeleteWorkLog')))
      .finally(() => setDeletingWlId(null));
  };

  // ── Participant handlers ──────────────────────────────────────────────────
  const handleAddParticipant = () => {
    if (!id || !newPUserId) return;
    setAddingP(true);
    addParticipant(id, { userId: newPUserId, role: newPRole })
      .then((created) => {
        setParticipants((prev) => [...prev, created]);
        setNewPUserId(null);
      })
      .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedAddParticipant')))
      .finally(() => setAddingP(false));
  };

  const handleRemoveParticipant = (participantId: string) => {
    if (!id) return;
    setRemovingPId(participantId);
    removeParticipant(id, participantId)
      .then(() => setParticipants((prev) => prev.filter((p) => p.id !== participantId)))
      .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedRemoveParticipant')))
      .finally(() => setRemovingPId(null));
  };

  // ── Comment handlers ──────────────────────────────────────────────────────
  const handleAddComment = () => {
    if (!id || !newComment.trim()) return;
    setAddingCmt(true);
    addComment(id, newComment.trim())
      .then((created) => {
        setComments((prev) => [...prev, created]);
        setNewComment('');
      })
      .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedAddComment')))
      .finally(() => setAddingCmt(false));
  };

  // ── Early returns ─────────────────────────────────────────────────────────
  if (loading) return <div style={{ textAlign: 'center', marginTop: 80 }}><Spin size="large" /></div>;
  if (error)   return <Alert type="error" message={error} style={{ margin: 24 }} />;
  if (!task)   return null;

  const assignedUser = task.participants.find((p) => p.role === 'ASSIGNEE');

  // ── Tab: Timeline ─────────────────────────────────────────────────────────
  const timelineTab = (
    <Row gutter={[16, 16]}>
      {TIMELINE_STATES.map((state) => {
        const entry = timelines.find((tl) => tl.state === state);
        return (
          <Col xs={24} sm={12} key={state}>
            <Card
              size="small"
              title={
                <Space>
                  <CalendarOutlined />
                  {t(`tasks.timelineStates.${state}`)}
                </Space>
              }
              extra={
                <Space size="small">
                  <Button size="small" onClick={() => openTlModal(state)}>
                    {entry ? t('common.edit') : t('tasks.setDate')}
                  </Button>
                  {entry && (
                    <Popconfirm
                      title={t('tasks.clearTimelineConfirm')}
                      onConfirm={() => handleDeleteTimeline(state)}
                      okText={t('common.delete')}
                      okButtonProps={{ danger: true }}
                    >
                      <Button danger size="small" loading={deletingTlState === state}>
                        {t('tasks.clearDate')}
                      </Button>
                    </Popconfirm>
                  )}
                </Space>
              }
            >
              {entry ? (
                <Descriptions column={1} size="small">
                  <Descriptions.Item label={t('tasks.date')}>
                    {dayjs(entry.timestamp).format('YYYY-MM-DD HH:mm')}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('tasks.setBy')}>
                    {entry.setByUserName ?? entry.setByUserId}
                  </Descriptions.Item>
                </Descriptions>
              ) : (
                <Typography.Text type="secondary">{t('tasks.notSet')}</Typography.Text>
              )}
            </Card>
          </Col>
        );
      })}
    </Row>
  );

  // ── Tab: Work Logs ────────────────────────────────────────────────────────
  const workLogsTab = (
    <>
      {wlLoading ? (
        <Spin size="small" />
      ) : (
        <List
          size="small"
          dataSource={workLogs}
          locale={{ emptyText: t('tasks.noWorkLogs') }}
          renderItem={(log) => (
            <List.Item
              key={log.id}
              actions={[
                <Button key="edit" size="small" onClick={() => { setEditingWl(log); setWlUserId(log.userId); setWlType(log.workType); setWlPlanned(Number(log.plannedHours)); setWlBooked(Number(log.bookedHours)); }}>
                  {t('common.edit')}
                </Button>,
                <Popconfirm
                  key="del"
                  title={t('tasks.deleteWorkLog')}
                  onConfirm={() => handleDeleteWorkLog(log.id)}
                  okText={t('common.delete')}
                  okButtonProps={{ danger: true }}
                >
                  <Button danger size="small" loading={deletingWlId === log.id}>{t('common.delete')}</Button>
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
      )}

      <Divider orientation="left" style={{ marginTop: 16 }}>
        {editingWl ? t('tasks.editWorkLog') : t('tasks.addWorkLog')}
      </Divider>
      <Space direction="vertical" style={{ width: '100%', maxWidth: 480 }}>
        <Select
          style={{ width: '100%' }}
          placeholder={t('tasks.selectUser')}
          value={wlUserId}
          onChange={setWlUserId}
          options={users.map((u) => ({ label: u.name, value: u.id }))}
        />
        <Select
          style={{ width: '100%' }}
          value={wlType}
          onChange={setWlType}
          options={(Object.keys(workTypeLabels) as WorkType[]).map((w) => ({ label: workTypeLabels[w], value: w }))}
        />
        <Space>
          {!editingWl && (
            <InputNumber
              min={0} step={1} precision={0}
              value={wlPlanned}
              onChange={(v) => setWlPlanned(v ?? 0)}
              addonBefore={t('tasks.planned')}
              addonAfter="h"
            />
          )}
          <InputNumber
            min={0} step={1} precision={0}
            value={wlBooked}
            onChange={(v) => setWlBooked(v ?? 0)}
            addonBefore={t('tasks.booked')}
            addonAfter="h"
          />
        </Space>
        <Space>
          <Button type="primary" loading={savingWl} disabled={!wlUserId} onClick={handleSaveWorkLog}>
            {editingWl ? t('common.save') : t('common.add')}
          </Button>
          {editingWl && <Button onClick={resetWlForm}>{t('common.cancel')}</Button>}
        </Space>
      </Space>
    </>
  );

  // ── Tab: Participants ─────────────────────────────────────────────────────
  const participantsTab = (
    <>
      <List
        size="small"
        dataSource={participants}
        locale={{ emptyText: t('tasks.noParticipants') }}
        renderItem={(p) => (
          <List.Item
            key={p.id}
            actions={[
              <Popconfirm
                key="remove"
                title={t('tasks.removeParticipant')}
                onConfirm={() => handleRemoveParticipant(p.id)}
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
      <Divider style={{ marginTop: 16 }} />
      <Space.Compact style={{ width: '100%', maxWidth: 480 }}>
        <Select
          style={{ flex: 1 }}
          placeholder={t('tasks.selectUser')}
          value={newPUserId}
          onChange={setNewPUserId}
          options={users.map((u) => ({ label: u.name, value: u.id }))}
        />
        <Select
          style={{ width: 130 }}
          value={newPRole}
          onChange={setNewPRole}
          options={(['ASSIGNEE', 'VIEWER', 'REVIEWER'] as TaskParticipantRole[]).map((r) => ({ label: r, value: r }))}
        />
        <Button type="primary" loading={addingP} disabled={!newPUserId} onClick={handleAddParticipant}>
          {t('common.add')}
        </Button>
      </Space.Compact>
    </>
  );

  // ── Tab: Comments ─────────────────────────────────────────────────────────
  const commentsTab = (
    <>
      <List
        dataSource={comments}
        locale={{ emptyText: t('tasks.noComments') }}
        renderItem={(c) => (
          <List.Item key={c.id}>
            <List.Item.Meta
              title={c.content}
              description={new Date(c.createdAt).toLocaleString()}
            />
          </List.Item>
        )}
      />
      <Divider style={{ marginTop: 8 }} />
      <Space direction="vertical" style={{ width: '100%', maxWidth: 600 }}>
        <Input.TextArea
          rows={3}
          value={newComment}
          onChange={(e) => setNewComment(e.target.value)}
          placeholder={t('tasks.addCommentPlaceholder')}
        />
        <Button
          type="primary"
          loading={addingCmt}
          disabled={!newComment.trim()}
          onClick={handleAddComment}
        >
          {t('tasks.addComment')}
        </Button>
      </Space>
    </>
  );

  return (
    <div style={{ padding: 24, maxWidth: 1000, margin: '0 auto' }}>
      {/* Back navigation */}
      <Button
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate('/tasks')}
        style={{ marginBottom: 16 }}
      >
        {t('tasks.backToTasks')}
      </Button>

      {/* Task overview */}
      <Card style={{ marginBottom: 24 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 8 }}>
          <Typography.Title level={4} style={{ margin: 0 }}>{task.title}</Typography.Title>
          <Space>
            <Tag color={STATUS_COLORS[task.status]}>{t(`tasks.statuses.${task.status}`)}</Tag>
            {task.type && <Tag color={TYPE_COLORS[task.type]}>{typeLabels[task.type]}</Tag>}
          </Space>
        </div>

        <Descriptions column={{ xs: 1, sm: 2 }} style={{ marginTop: 16 }} size="small">
          <Descriptions.Item label={t('common.description')} span={2}>
            {task.description || '—'}
          </Descriptions.Item>
          <Descriptions.Item label={t('common.project')}>
            {task.project?.name ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label={t('tasks.phase')}>
            {task.phase?.name ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label={t('tasks.assignedTo')}>
            {assignedUser?.userName ?? assignedUser?.userId ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label={t('tasks.progress')}>
            <Progress
              percent={task.progress}
              strokeColor={task.progress === 100 ? '#52c41a' : undefined}
              style={{ maxWidth: 300 }}
            />
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {/* Content tabs */}
      <Tabs
        items={[
          { key: 'timeline',     label: t('tasks.timeline'),     children: timelineTab     },
          { key: 'worklogs',     label: t('tasks.workLogs'),     children: workLogsTab     },
          { key: 'participants', label: t('tasks.participants'), children: participantsTab },
          { key: 'comments',     label: t('tasks.comments'),     children: commentsTab     },
        ]}
      />

      {/* Timeline set/edit modal */}
      <Modal
        title={editingState ? t(`tasks.timelineStates.${editingState}`) : ''}
        open={tlModalOpen}
        onOk={handleSaveTimeline}
        onCancel={() => setTlModalOpen(false)}
        okText={t('common.save')}
        confirmLoading={savingTimeline}
        okButtonProps={{ disabled: !tlUserId || !tlTimestamp }}
      >
        <Space direction="vertical" style={{ width: '100%', marginTop: 16 }}>
          <DatePicker
            showTime
            style={{ width: '100%' }}
            value={tlTimestamp}
            onChange={setTlTimestamp}
            placeholder={t('tasks.selectDate')}
          />
          <Select
            style={{ width: '100%' }}
            placeholder={t('tasks.selectUser')}
            value={tlUserId}
            onChange={setTlUserId}
            options={users.map((u) => ({ label: u.name, value: u.id }))}
          />
        </Space>
      </Modal>
    </div>
  );
}
