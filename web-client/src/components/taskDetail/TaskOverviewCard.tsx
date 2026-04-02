import { useTranslation } from 'react-i18next';
import { Card, Descriptions, Progress, Space, Tag, Typography } from 'antd';
import type { TaskResponse, TaskType } from '../../api/types';
import { STATUS_COLORS, TYPE_COLORS } from '../../pages/taskDetail/taskDetailConstants';

interface Props {
  task: TaskResponse;
}

/** Renders the task overview card: title, status/type tags, and key metadata fields. */
export function TaskOverviewCard({ task }: Props) {
  const { t } = useTranslation();

  const typeLabels: Record<TaskType, string> = {
    FEATURE:        t('tasks.types.FEATURE'),
    BUG_FIXING:     t('tasks.types.BUG_FIXING'),
    TESTING:        t('tasks.types.TESTING'),
    PLANNING:       t('tasks.types.PLANNING'),
    TECHNICAL_DEBT: t('tasks.types.TECHNICAL_DEBT'),
    DOCUMENTATION:  t('tasks.types.DOCUMENTATION'),
    OTHER:          t('tasks.types.OTHER'),
  };

  const assignedUser = task.participants.find((p) => p.role === 'ASSIGNEE');

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
  );
}
