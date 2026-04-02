import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  Alert, Button, Card, Col, DatePicker, Descriptions, Divider, Input, InputNumber,
  List, Modal, Popconfirm, Progress, Row, Select, Space, Spin, Tabs, Tag, Typography,
} from 'antd';
import { ArrowLeftOutlined, CalendarOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { getTask } from '../api/taskApi';
import { getUsers } from '../api/userApi';
import type { TaskResponse, TaskType, WorkType, UserResponse } from '../api/types';
import { STATUS_COLORS, TYPE_COLORS, TIMELINE_STATES } from './taskDetail/taskDetailConstants';
import { useTaskTimeline }     from '../hooks/useTaskTimeline';
import { useTaskPlannedWork }  from '../hooks/useTaskPlannedWork';
import { useTaskBookedWork }   from '../hooks/useTaskBookedWork';
import { useTaskParticipants } from '../hooks/useTaskParticipants';
import { useTaskComments }     from '../hooks/useTaskComments';

/** Full-page view for a single task: overview, timeline, work logs, participants, and comments. */
export function TaskDetailPage() {
  const { id }   = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { t }    = useTranslation();

  const [task,    setTask]    = useState<TaskResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error,   setError]   = useState<string | null>(null);
  const [users,   setUsers]   = useState<UserResponse[]>([]);

  const timeline     = useTaskTimeline(id, setError);
  const plannedWork  = useTaskPlannedWork(id, setError);
  const bookedWork   = useTaskBookedWork(id, setError);
  const participants = useTaskParticipants(id, setError);
  const comments     = useTaskComments(id, setError);

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
    Promise.all([getTask(id), getUsers()])
      .then(([taskData, usersPage]) => {
        setTask(taskData);
        setUsers(usersPage.content);
      })
      .catch((err) => setError(err?.message ?? t('tasks.failedLoad')))
      .finally(() => setLoading(false));
  }, [id]);

  // ── Early returns ─────────────────────────────────────────────────────────
  if (loading) return <div style={{ textAlign: 'center', marginTop: 80 }}><Spin size="large" /></div>;
  if (error)   return <Alert type="error" message={error} style={{ margin: 24 }} />;
  if (!task)   return null;

  const assignedUser = task.participants.find((p) => p.role === 'ASSIGNEE');

  // ── Tab: Timeline ─────────────────────────────────────────────────────────
  const timelineTab = (
    <Row gutter={[16, 16]}>
      {TIMELINE_STATES.map((state) => {
        const entry = timeline.timelines.find((tl) => tl.state === state);
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
                  <Button size="small" onClick={() => timeline.openTlModal(state)}>
                    {entry ? t('common.edit') : t('tasks.setDate')}
                  </Button>
                  {entry && (
                    <Popconfirm
                      title={t('tasks.clearTimelineConfirm')}
                      onConfirm={() => timeline.handleDeleteTimeline(state)}
                      okText={t('common.delete')}
                      okButtonProps={{ danger: true }}
                    >
                      <Button danger size="small" loading={timeline.deletingTlState === state}>
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

  // ── Tab: Planned Work ─────────────────────────────────────────────────────
  const plannedWorkTab = (
    <>
      {plannedWork.pwLoading ? (
        <Spin size="small" />
      ) : (
        <List
          size="small"
          dataSource={plannedWork.plannedWork}
          locale={{ emptyText: t('tasks.noPlannedWork') }}
          renderItem={(pw) => (
            <List.Item key={pw.id}>
              <Space direction="vertical" size={0}>
                <Space>
                  <Tag color="blue">{workTypeLabels[pw.workType]}</Tag>
                  <Typography.Text strong>{pw.userName ?? pw.userId}</Typography.Text>
                </Space>
                <Typography.Text type="secondary">
                  {t('tasks.planned')}: <strong>{pw.plannedHours}h</strong>
                </Typography.Text>
              </Space>
            </List.Item>
          )}
        />
      )}

      {task.status === 'TODO' && (
        <>
          <Divider orientation="left" style={{ marginTop: 16 }}>{t('tasks.addPlannedWork')}</Divider>
          <Space direction="vertical" style={{ width: '100%', maxWidth: 480 }}>
            <Select
              style={{ width: '100%' }}
              placeholder={t('tasks.selectUser')}
              value={plannedWork.pwUserId}
              onChange={plannedWork.setPwUserId}
              options={users.map((u) => ({ label: u.name, value: u.id }))}
            />
            <Select
              style={{ width: '100%' }}
              value={plannedWork.pwType}
              onChange={plannedWork.setPwType}
              options={(Object.keys(workTypeLabels) as WorkType[]).map((w) => ({ label: workTypeLabels[w], value: w }))}
            />
            <InputNumber
              min={0} step={1} precision={0}
              value={plannedWork.pwHours}
              onChange={(v) => plannedWork.setPwHours(v ?? 0)}
              addonBefore={t('tasks.planned')}
              addonAfter="h"
              style={{ width: '100%' }}
            />
            <Button type="primary" loading={plannedWork.savingPw} disabled={!plannedWork.pwUserId} onClick={plannedWork.handleSavePlannedWork}>
              {t('common.add')}
            </Button>
          </Space>
        </>
      )}
    </>
  );

  // ── Tab: Booked Work ──────────────────────────────────────────────────────
  const bookedWorkTab = (
    <>
      {bookedWork.bwLoading ? (
        <Spin size="small" />
      ) : (
        <List
          size="small"
          dataSource={bookedWork.bookedWork}
          locale={{ emptyText: t('tasks.noBookedWork') }}
          renderItem={(bw) => (
            <List.Item
              key={bw.id}
              actions={[
                <Button key="edit" size="small" onClick={() => bookedWork.startEditing(bw)}>
                  {t('common.edit')}
                </Button>,
                <Popconfirm
                  key="del"
                  title={t('tasks.deleteBookedWork')}
                  onConfirm={() => bookedWork.handleDeleteBookedWork(bw.id)}
                  okText={t('common.delete')}
                  okButtonProps={{ danger: true }}
                >
                  <Button danger size="small" loading={bookedWork.deletingBwId === bw.id}>{t('common.delete')}</Button>
                </Popconfirm>,
              ]}
            >
              <Space direction="vertical" size={0}>
                <Space>
                  <Tag color="green">{workTypeLabels[bw.workType]}</Tag>
                  <Typography.Text strong>{bw.userName ?? bw.userId}</Typography.Text>
                </Space>
                <Typography.Text type="secondary">
                  {t('tasks.booked')}: <strong>{bw.bookedHours}h</strong>
                </Typography.Text>
              </Space>
            </List.Item>
          )}
        />
      )}

      <Divider orientation="left" style={{ marginTop: 16 }}>
        {bookedWork.editingBw ? t('tasks.editBookedWork') : t('tasks.addBookedWork')}
      </Divider>
      <Space direction="vertical" style={{ width: '100%', maxWidth: 480 }}>
        <Select
          style={{ width: '100%' }}
          placeholder={t('tasks.selectUser')}
          value={bookedWork.bwUserId}
          onChange={bookedWork.setBwUserId}
          options={users.map((u) => ({ label: u.name, value: u.id }))}
        />
        <Select
          style={{ width: '100%' }}
          value={bookedWork.bwType}
          onChange={bookedWork.setBwType}
          options={(Object.keys(workTypeLabels) as WorkType[]).map((w) => ({ label: workTypeLabels[w], value: w }))}
        />
        <InputNumber
          min={0} step={1} precision={0}
          value={bookedWork.bwHours}
          onChange={(v) => bookedWork.setBwHours(v ?? 0)}
          addonBefore={t('tasks.booked')}
          addonAfter="h"
          style={{ width: '100%' }}
        />
        <Space>
          <Button type="primary" loading={bookedWork.savingBw} disabled={!bookedWork.bwUserId} onClick={bookedWork.handleSaveBookedWork}>
            {bookedWork.editingBw ? t('common.save') : t('common.add')}
          </Button>
          {bookedWork.editingBw && <Button onClick={bookedWork.resetBwForm}>{t('common.cancel')}</Button>}
        </Space>
      </Space>
    </>
  );

  // ── Tab: Participants ─────────────────────────────────────────────────────
  const participantsTab = (
    <>
      <List
        size="small"
        dataSource={participants.participants}
        locale={{ emptyText: t('tasks.noParticipants') }}
        renderItem={(p) => (
          <List.Item
            key={p.id}
            actions={[
              <Popconfirm
                key="remove"
                title={t('tasks.removeParticipant')}
                onConfirm={() => participants.handleRemoveParticipant(p.id)}
                okText={t('common.remove')}
                okButtonProps={{ danger: true }}
              >
                <Button danger size="small" loading={participants.removingPId === p.id}>{t('common.remove')}</Button>
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
          value={participants.newPUserId}
          onChange={participants.setNewPUserId}
          options={users.map((u) => ({ label: u.name, value: u.id }))}
        />
        <Select
          style={{ width: 130 }}
          value={participants.newPRole}
          onChange={participants.setNewPRole}
          options={(['ASSIGNEE', 'VIEWER', 'REVIEWER'] as import('../api/types').TaskParticipantRole[]).map((r) => ({ label: r, value: r }))}
        />
        <Button type="primary" loading={participants.addingP} disabled={!participants.newPUserId} onClick={participants.handleAddParticipant}>
          {t('common.add')}
        </Button>
      </Space.Compact>
    </>
  );

  // ── Tab: Comments ─────────────────────────────────────────────────────────
  const commentsTab = (
    <>
      <List
        dataSource={comments.comments}
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
          value={comments.newComment}
          onChange={(e) => comments.setNewComment(e.target.value)}
          placeholder={t('tasks.addCommentPlaceholder')}
        />
        <Button
          type="primary"
          loading={comments.addingCmt}
          disabled={!comments.newComment.trim()}
          onClick={comments.handleAddComment}
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
          { key: 'plannedwork',  label: t('tasks.plannedWork'),  children: plannedWorkTab  },
          { key: 'bookedwork',   label: t('tasks.bookedWork'),   children: bookedWorkTab   },
          { key: 'participants', label: t('tasks.participants'), children: participantsTab },
          { key: 'comments',     label: t('tasks.comments'),     children: commentsTab     },
        ]}
      />

      {/* Timeline set/edit modal */}
      <Modal
        title={timeline.editingState ? t(`tasks.timelineStates.${timeline.editingState}`) : ''}
        open={timeline.tlModalOpen}
        onOk={timeline.handleSaveTimeline}
        onCancel={() => timeline.setTlModalOpen(false)}
        okText={t('common.save')}
        confirmLoading={timeline.savingTimeline}
        okButtonProps={{ disabled: !timeline.tlUserId || !timeline.tlTimestamp }}
      >
        <Space direction="vertical" style={{ width: '100%', marginTop: 16 }}>
          <DatePicker
            showTime
            style={{ width: '100%' }}
            value={timeline.tlTimestamp}
            onChange={timeline.setTlTimestamp}
            placeholder={t('tasks.selectDate')}
          />
          <Select
            style={{ width: '100%' }}
            placeholder={t('tasks.selectUser')}
            value={timeline.tlUserId}
            onChange={timeline.setTlUserId}
            options={users.map((u) => ({ label: u.name, value: u.id }))}
          />
        </Space>
      </Modal>
    </div>
  );
}
