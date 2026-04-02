import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Card, Descriptions, Progress, Space, Spin, Tabs, Tag, Typography } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { getTask } from '../api/taskApi';
import { getUsers } from '../api/userApi';
import type { TaskType, UserResponse, TaskResponse } from '../api/types';
import { STATUS_COLORS, TYPE_COLORS } from './taskDetail/taskDetailConstants';
import { useTaskTimeline }     from '../hooks/useTaskTimeline';
import { useTaskPlannedWork }  from '../hooks/useTaskPlannedWork';
import { useTaskBookedWork }   from '../hooks/useTaskBookedWork';
import { useTaskParticipants } from '../hooks/useTaskParticipants';
import { useTaskComments }     from '../hooks/useTaskComments';
import { TaskTimelineTab }     from '../components/taskDetail/TaskTimelineTab';
import { TaskPlannedWorkTab }  from '../components/taskDetail/TaskPlannedWorkTab';
import { TaskBookedWorkTab }   from '../components/taskDetail/TaskBookedWorkTab';
import { TaskParticipantsTab } from '../components/taskDetail/TaskParticipantsTab';
import { TaskCommentsTab }     from '../components/taskDetail/TaskCommentsTab';

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

  const typeLabels: Record<TaskType, string> = {
    FEATURE:        t('tasks.types.FEATURE'),
    BUG_FIXING:     t('tasks.types.BUG_FIXING'),
    TESTING:        t('tasks.types.TESTING'),
    PLANNING:       t('tasks.types.PLANNING'),
    TECHNICAL_DEBT: t('tasks.types.TECHNICAL_DEBT'),
    DOCUMENTATION:  t('tasks.types.DOCUMENTATION'),
    OTHER:          t('tasks.types.OTHER'),
  };

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

  if (loading) return <div style={{ textAlign: 'center', marginTop: 80 }}><Spin size="large" /></div>;
  if (error)   return <Alert type="error" message={error} style={{ margin: 24 }} />;
  if (!task)   return null;

  const assignedUser = task.participants.find((p) => p.role === 'ASSIGNEE');

  return (
    <div style={{ padding: 24, maxWidth: 1000, margin: '0 auto' }}>
      <Button
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate('/tasks')}
        style={{ marginBottom: 16 }}
      >
        {t('tasks.backToTasks')}
      </Button>

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

      <Tabs
        items={[
          { key: 'timeline',     label: t('tasks.timeline'),     children: <TaskTimelineTab     {...timeline}     users={users} /> },
          { key: 'plannedwork',  label: t('tasks.plannedWork'),  children: <TaskPlannedWorkTab  {...plannedWork}  users={users} taskStatus={task.status} /> },
          { key: 'bookedwork',   label: t('tasks.bookedWork'),   children: <TaskBookedWorkTab   {...bookedWork}   users={users} /> },
          { key: 'participants', label: t('tasks.participants'), children: <TaskParticipantsTab {...participants} users={users} /> },
          { key: 'comments',     label: t('tasks.comments'),     children: <TaskCommentsTab     {...comments} /> },
        ]}
      />
    </div>
  );
}
