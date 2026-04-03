import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Card, Descriptions, Progress, Space, Tag, Typography } from 'antd';
import dayjs from 'dayjs';
import type { TaskResponse, TaskTimelineResponse } from '../../api/types';
import { STATUS_COLORS, TYPE_COLORS, getTypeLabels } from '../../pages/taskDetail/taskDetailConstants';
import { resolvePhaseLabel } from '../../utils/phaseUtils';

interface Props {
  task: TaskResponse;
  timelines: TaskTimelineResponse[];
}

/** Renders the task overview card: title, status/type tags, and key metadata fields. */
export function TaskOverviewCard({ task, timelines }: Props) {
  const { t } = useTranslation();

  const typeLabels = useMemo(() => getTypeLabels(t), [t]);

  const assignedUser = task.participants.find((p) => p.role === 'ASSIGNEE');

  const plannedStart = timelines.find((tl) => tl.state === 'PLANNED_START');
  const plannedEnd   = timelines.find((tl) => tl.state === 'PLANNED_END');

  return (
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
          {resolvePhaseLabel(task.phase)}
        </Descriptions.Item>
        <Descriptions.Item label={t('tasks.assignedTo')}>
          {assignedUser?.userName ?? assignedUser?.userId ?? '—'}
        </Descriptions.Item>
        <Descriptions.Item label={t('tasks.plannedStart')}>
          {plannedStart ? dayjs(plannedStart.timestamp).format('YYYY-MM-DD HH:mm') : '—'}
        </Descriptions.Item>
        <Descriptions.Item label={t('tasks.plannedEnd')}>
          {plannedEnd ? dayjs(plannedEnd.timestamp).format('YYYY-MM-DD HH:mm') : '—'}
        </Descriptions.Item>
        <Descriptions.Item label={t('tasks.progress')} span={2}>
          <Progress
            percent={task.progress}
            strokeColor={task.progress === 100 ? '#52c41a' : undefined}
            style={{ maxWidth: 300 }}
          />
        </Descriptions.Item>
      </Descriptions>
    </Card>
  );
}
