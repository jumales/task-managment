import { useMemo, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Col, Divider, Row, Spin, Space, Tabs, Tag, Typography } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { useTaskDetailData } from '../hooks/useTaskDetailData';
import { updateTask } from '../api/taskApi';
import type { TaskRequest } from '../api/types';
import { isTaskFinished } from '../utils/phaseUtils';
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
import { STATUS_COLORS, TYPE_COLORS, getTypeLabels } from './taskDetail/taskDetailConstants';

/** Full-page view for a single task: header with code+title+tags, left column with metadata and timeline, right column with tabs. */
export function TaskDetailPage() {
  const { id }   = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { t }    = useTranslation();

  const { data, setData, loading, error } = useTaskDetailData(id);

  const [saving, setSaving] = useState(false);

  const typeLabels = useMemo(() => getTypeLabels(t), [t]);

  /** Persists edits to title, description, assignee, and progress without leaving the detail page. */
  const handleOverviewSave = (patch: { title: string; description: string; assignedUserId: string; progress: number }) => {
    if (!data || !id) return Promise.resolve();
    setSaving(true);
    const req: TaskRequest = {
      title:          patch.title,
      description:    patch.description,
      status:         data.task.status,
      type:           data.task.type ?? null,
      progress:       patch.progress,
      assignedUserId: patch.assignedUserId,
      projectId:      data.task.project.id,
      phaseId:        data.task.phase.id,
    };
    return updateTask(id, req)
      .then((updated) => setData((prev) => prev ? { ...prev, task: updated } : null))
      .finally(() => setSaving(false));
  };

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
  const bookedWork   = useTaskBookedWork(id,    bookedWorkData, plannedWorkData);
  const participants = useTaskParticipants(id,  participantsData);
  const comments     = useTaskComments(id,      commentsData);
  const attachments  = useTaskAttachments(id,   attachmentsData);

  if (loading) return <div style={{ textAlign: 'center', marginTop: 80 }}><Spin size="large" /></div>;
  if (error)   return <Alert type="error" message={error} style={{ margin: 24 }} />;
  if (!data)   return null;

  const finished = isTaskFinished(data.task.phase.name);

  return (
    <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
      <Button
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate('/tasks')}
        style={{ marginBottom: 16 }}
      >
        {t('tasks.backToTasks')}
      </Button>

      {/* Top header: task code + title + status/type/completion tags */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 8, marginBottom: 24 }}>
        <div>
          {data.task.taskCode && (
            <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 2 }}>
              {data.task.taskCode}
            </Typography.Text>
          )}
          <Typography.Title level={3} style={{ margin: 0 }}>{data.task.title}</Typography.Title>
        </div>
        <Space wrap>
          <Tag color={STATUS_COLORS[data.task.status]}>{t(`tasks.statuses.${data.task.status}`)}</Tag>
          {data.task.type && <Tag color={TYPE_COLORS[data.task.type]}>{typeLabels[data.task.type]}</Tag>}
          {finished && <Tag color="red">Finished</Tag>}
          {!finished && data.task.phase.name === 'DONE' && <Tag color="orange">Dev Finished</Tag>}
        </Space>
      </div>

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

      <Row gutter={[24, 24]}>
        {/* Left column: metadata + timeline */}
        <Col xs={24} md={10}>
          <TaskOverviewCard
            task={data.task}
            users={data.users}
            onSave={handleOverviewSave}
            saving={saving}
            onChangePhase={phaseChange.openModal}
          />
          <Divider orientation="left" style={{ marginTop: 24 }}>{t('tasks.timeline')}</Divider>
          <TaskTimelineTab {...timeline} users={data.users} taskPhaseName={data.task.phase.name} />
        </Col>

        {/* Right column: collaborative tabs */}
        <Col xs={24} md={14}>
          <Tabs
            items={[
              {
                key: 'comments',
                label: t('tasks.comments'),
                children: (
                  <>
                    <TaskCommentsTab {...comments} finished={finished} />
                    <Divider />
                    <TaskAttachmentsTab {...attachments} />
                  </>
                ),
              },
              { key: 'plannedwork',  label: t('tasks.plannedWork'),  children: <TaskPlannedWorkTab  {...plannedWork}  taskPhaseName={data.task.phase.name} /> },
              { key: 'bookedwork',   label: t('tasks.bookedWork'),   children: <TaskBookedWorkTab   {...bookedWork}   taskPhaseName={data.task.phase.name} finished={finished} /> },
              { key: 'participants', label: t('tasks.participants'), children: <TaskParticipantsTab {...participants} users={data.users} /> },
            ]}
          />
        </Col>
      </Row>
    </div>
  );
}
