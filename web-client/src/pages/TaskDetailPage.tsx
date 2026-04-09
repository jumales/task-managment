import { useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Divider, Spin, Tabs } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { useTaskDetailData } from '../hooks/useTaskDetailData';
import { useTaskTimeline }     from '../hooks/useTaskTimeline';
import { useTaskPlannedWork }  from '../hooks/useTaskPlannedWork';
import { useTaskBookedWork }   from '../hooks/useTaskBookedWork';
import { useTaskParticipants } from '../hooks/useTaskParticipants';
import { useTaskComments }     from '../hooks/useTaskComments';
import { useTaskPhaseChange }    from '../hooks/useTaskPhaseChange';
import { useTaskAttachments }    from '../hooks/useTaskAttachments';
import { TaskOverviewCard }      from '../components/taskDetail/TaskOverviewCard';
import { TaskPhaseChangeModal }  from '../components/taskDetail/TaskPhaseChangeModal';
import { TaskTimelineTab }       from '../components/taskDetail/TaskTimelineTab';
import { TaskPlannedWorkTab }    from '../components/taskDetail/TaskPlannedWorkTab';
import { TaskBookedWorkTab }     from '../components/taskDetail/TaskBookedWorkTab';
import { TaskParticipantsTab }   from '../components/taskDetail/TaskParticipantsTab';
import { TaskCommentsTab }       from '../components/taskDetail/TaskCommentsTab';
import { TaskAttachmentsTab }    from '../components/taskDetail/TaskAttachmentsTab';

/** Full-page view for a single task: overview, timeline, work logs, participants, and comments. */
export function TaskDetailPage() {
  const { id }   = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { t }    = useTranslation();

  const { data, setData, loading, error } = useTaskDetailData(id);

  const phaseChange = useTaskPhaseChange(id, data?.task ?? null, (updated) => {
    setData((prev) => (prev ? { ...prev, task: updated } : null));
  });

  // Stabilize array references so the sync useEffect inside each hook only fires
  // when data actually changes, not on every render due to a new [] reference.
  const timelinesData    = useMemo(() => data?.timelines    ?? [], [data]);
  const plannedWorkData  = useMemo(() => data?.plannedWork  ?? [], [data]);
  const bookedWorkData   = useMemo(() => data?.bookedWork   ?? [], [data]);
  const participantsData = useMemo(() => data?.participants ?? [], [data]);
  const commentsData     = useMemo(() => data?.comments     ?? [], [data]);
  const attachmentsData  = useMemo(() => data?.attachments  ?? [], [data]);

  const timeline     = useTaskTimeline(id,      timelinesData);
  const plannedWork  = useTaskPlannedWork(id,   plannedWorkData);
  const bookedWork   = useTaskBookedWork(id,    bookedWorkData);
  const participants = useTaskParticipants(id,  participantsData);
  const comments     = useTaskComments(id,      commentsData);
  const attachments  = useTaskAttachments(id,   attachmentsData);

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

      <TaskOverviewCard task={data.task} onChangePhase={phaseChange.openModal} />
      <TaskPhaseChangeModal
        open={phaseChange.open}
        onClose={() => phaseChange.setOpen(false)}
        phases={phaseChange.phases}
        loadingPhases={phaseChange.loadingPhases}
        selectedPhaseId={phaseChange.selectedPhaseId}
        onSelectPhase={phaseChange.setSelectedPhaseId}
        saving={phaseChange.saving}
        onSave={phaseChange.handleSave}
        error={phaseChange.error}
      />

      <Tabs
        items={[
          {
            key: 'comments',
            label: t('tasks.comments'),
            children: (
              <>
                <TaskCommentsTab {...comments} />
                <Divider />
                <TaskAttachmentsTab {...attachments} />
              </>
            ),
          },
          { key: 'timeline',     label: t('tasks.timeline'),     children: <TaskTimelineTab     {...timeline}     users={data.users} /> },
          { key: 'plannedwork',  label: t('tasks.plannedWork'),  children: <TaskPlannedWorkTab  {...plannedWork}  taskStatus={data.task.status} /> },
          { key: 'bookedwork',   label: t('tasks.bookedWork'),   children: <TaskBookedWorkTab   {...bookedWork}   users={data.users} taskPhaseName={data.task.phase.name} /> },
          { key: 'participants', label: t('tasks.participants'), children: <TaskParticipantsTab {...participants} users={data.users} /> },
        ]}
      />
    </div>
  );
}
