import { useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Card, Descriptions, Input, InputNumber, Progress, Select, Space, Tag, Typography } from 'antd';
import { EditOutlined, SwapOutlined } from '@ant-design/icons';
import type { TaskResponse, UserResponse } from '../../api/types';
import { STATUS_COLORS, TYPE_COLORS, getTypeLabels } from '../../pages/taskDetail/taskDetailConstants';
import { resolvePhaseLabel, isTaskFinished, isTaskFieldsLocked } from '../../utils/phaseUtils';

interface EditPatch {
  title:          string;
  description:    string;
  assignedUserId: string;
  progress:       number;
}

interface Props {
  task:           TaskResponse;
  users:          UserResponse[];
  onSave:         (patch: EditPatch) => Promise<void>;
  saving?:        boolean;
  onChangePhase?: () => void;
}

/** Renders the task overview card: title, status/type tags, and key metadata fields. Supports inline editing of title, description, assignee, and progress. Edit is locked in DONE/RELEASED/REJECTED phases; phase change is locked in RELEASED/REJECTED. */
export function TaskOverviewCard({ task, users, onSave, saving, onChangePhase }: Props) {
  const { t } = useTranslation();

  const typeLabels = useMemo(() => getTypeLabels(t), [t]);

  const finished     = isTaskFinished(task.phase.name);
  const fieldsLocked = isTaskFieldsLocked(task.phase.name);

  const assignedParticipant = task.participants.find((p) => p.role === 'ASSIGNEE');

  const [editing, setEditing] = useState(false);
  const [title,          setTitle]          = useState('');
  const [description,    setDescription]    = useState('');
  const [assignedUserId, setAssignedUserId] = useState('');
  const [progress,       setProgress]       = useState(0);

  /** Opens edit mode pre-filled with current task values. */
  const startEditing = () => {
    setTitle(task.title);
    setDescription(task.description ?? '');
    setAssignedUserId(assignedParticipant?.userId ?? '');
    setProgress(task.progress);
    setEditing(true);
  };

  const cancelEditing = () => setEditing(false);

  const handleSave = () => {
    onSave({ title, description, assignedUserId, progress })
      .then(() => setEditing(false));
  };

  return (
    <Card style={{ marginBottom: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 8 }}>
        {editing
          ? <Input value={title} onChange={(e) => setTitle(e.target.value)} style={{ flex: 1, maxWidth: 500, fontWeight: 600, fontSize: 16 }} />
          : <Typography.Title level={4} style={{ margin: 0 }}>{task.title}</Typography.Title>
        }
        <Space>
          <Tag color={STATUS_COLORS[task.status]}>{t(`tasks.statuses.${task.status}`)}</Tag>
          {task.type && <Tag color={TYPE_COLORS[task.type]}>{typeLabels[task.type]}</Tag>}
          {finished    && <Tag color="red">Finished</Tag>}
          {!finished && task.phase.name === 'DONE' && <Tag color="orange">Dev Finished</Tag>}
          {!editing && !fieldsLocked && (
            <Button
              type="text"
              size="small"
              icon={<EditOutlined />}
              onClick={startEditing}
            />
          )}
        </Space>
      </div>

      <Descriptions column={2} style={{ marginTop: 16 }} size="small">
        <Descriptions.Item label={t('common.description')} span={2}>
          {editing
            ? <Input.TextArea rows={3} value={description} onChange={(e) => setDescription(e.target.value)} />
            : (task.description || '—')
          }
        </Descriptions.Item>
        <Descriptions.Item label={t('common.project')}>
          {task.project?.name ?? '—'}
        </Descriptions.Item>
        <Descriptions.Item label={t('tasks.phase')}>
          <Space>
            {resolvePhaseLabel(task.phase)}
            {onChangePhase && !finished && (
              <Button
                type="link"
                size="small"
                icon={<SwapOutlined />}
                onClick={onChangePhase}
                style={{ padding: 0, height: 'auto' }}
              >
                {t('tasks.changePhase')}
              </Button>
            )}
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label={t('tasks.assignedTo')} span={2}>
          {editing
            ? (
              <Select
                value={assignedUserId}
                onChange={setAssignedUserId}
                options={users.map((u) => ({ label: u.name || u.email, value: u.id }))}
                style={{ width: '100%', maxWidth: 300 }}
              />
            )
            : (assignedParticipant?.userName ?? assignedParticipant?.userEmail ?? '—')
          }
        </Descriptions.Item>
        <Descriptions.Item label={t('tasks.progress')} span={2}>
          {editing
            ? (
              <InputNumber
                min={0}
                max={100}
                value={progress}
                onChange={(v) => setProgress(v ?? 0)}
                suffix="%"
                style={{ width: 120 }}
              />
            )
            : (
              <Progress
                percent={task.progress}
                strokeColor={task.progress === 100 ? '#52c41a' : undefined}
                style={{ maxWidth: 300 }}
              />
            )
          }
        </Descriptions.Item>
      </Descriptions>

      {editing && (
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 12 }}>
          <Button onClick={cancelEditing}>{t('common.cancel')}</Button>
          <Button type="primary" loading={saving} onClick={handleSave}>{t('common.save')}</Button>
        </div>
      )}
    </Card>
  );
}
