import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Spin, Tabs } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { useTaskDetailData } from '../hooks/useTaskDetailData';
import { useTaskTimeline }     from '../hooks/useTaskTimeline';
import { useTaskPlannedWork }  from '../hooks/useTaskPlannedWork';
import { useTaskBookedWork }   from '../hooks/useTaskBookedWork';
import { useTaskParticipants } from '../hooks/useTaskParticipants';
import { useTaskComments }     from '../hooks/useTaskComments';
import { TaskOverviewCard }    from '../components/taskDetail/TaskOverviewCard';
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

  const { data, loading, error } = useTaskDetailData(id);

  const timeline     = useTaskTimeline(id,     data?.timelines    ?? []);
  const plannedWork  = useTaskPlannedWork(id,  data?.plannedWork  ?? []);
  const bookedWork   = useTaskBookedWork(id,   data?.bookedWork   ?? []);
  const participants = useTaskParticipants(id, data?.participants ?? []);
  const comments     = useTaskComments(id,     data?.comments     ?? []);

  if (loading) return <div style={{ textAlign: 'center', marginTop: 80 }}><Spin size="large" /></div>;
  if (error)   return <Alert type="error" message={error} style={{ margin: 24 }} />;
  if (!data)   return null;

  return (
    <div style={{ padding: 24, maxWidth: 1000, margin: '0 auto' }}>
      <Button
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate('/tasks')}
        style={{ marginBottom: 16 }}
      >
        {t('tasks.backToTasks')}
      </Button>

      <TaskOverviewCard task={data.task} />

      <Tabs
        items={[
          { key: 'timeline',     label: t('tasks.timeline'),     children: <TaskTimelineTab     {...timeline}     users={data.users} /> },
          { key: 'plannedwork',  label: t('tasks.plannedWork'),  children: <TaskPlannedWorkTab  {...plannedWork}  taskStatus={data.task.status} /> },
          { key: 'bookedwork',   label: t('tasks.bookedWork'),   children: <TaskBookedWorkTab   {...bookedWork}   users={data.users} /> },
          { key: 'participants', label: t('tasks.participants'), children: <TaskParticipantsTab {...participants} users={data.users} /> },
          { key: 'comments',     label: t('tasks.comments'),     children: <TaskCommentsTab     {...comments} /> },
        ]}
      />
    </div>
  );
}
