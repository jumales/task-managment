import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Descriptions, Input, InputNumber, Progress, Select, Space } from 'antd';
import { EditOutlined, SwapOutlined } from '@ant-design/icons';
import type { TaskResponse, UserResponse } from '../../api/types';
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
  /** When true (supervisor role), all edit and phase-change controls are hidden. */
  readOnly?:      boolean;
}

/** Renders the task metadata panel: phase, project, assignee, progress, and description. Supports inline editing. Edit is locked in DONE/RELEASED/REJECTED phases; phase change is locked in RELEASED/REJECTED. Title and status/type tags are rendered by the parent page header. */
export function TaskOverviewCard({ task, users, onSave, saving, onChangePhase, readOnly }: Props) {
  const { t } = useTranslation();

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
    <div>
      {!editing && !fieldsLocked && !readOnly && (
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 8 }}>
          <Button type="text" size="small" icon={<EditOutlined />} onClick={startEditing}>
            {t('common.edit')}
          </Button>
        </div>
      )}

      {editing && (
        <div style={{ marginBottom: 8 }}>
          <div style={{ fontSize: 12, color: 'rgba(0,0,0,0.45)', marginBottom: 4 }}>{t('tasks.title_field')}</div>
          <Input value={title} onChange={(e) => setTitle(e.target.value)} />
        </div>
      )}

      <Descriptions column={1} size="small">
        <Descriptions.Item label={t('tasks.phase')}>
          <Space>
            {resolvePhaseLabel(task.phase)}
            {onChangePhase && !finished && !readOnly && (
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
        <Descriptions.Item label={t('common.project')}>
          {task.project?.name ?? '—'}
        </Descriptions.Item>
        <Descriptions.Item label={t('tasks.assignedTo')}>
          {editing
            ? (
              <Select
                value={assignedUserId}
                onChange={setAssignedUserId}
                options={users.map((u) => ({ label: u.name || u.email, value: u.id }))}
                style={{ width: '100%' }}
              />
            )
            : (assignedParticipant?.userName ?? assignedParticipant?.userEmail ?? '—')
          }
        </Descriptions.Item>
        <Descriptions.Item label={t('tasks.progress')}>
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
              />
            )
          }
        </Descriptions.Item>
        <Descriptions.Item label={t('common.description')}>
          {editing
            ? <Input.TextArea rows={4} value={description} onChange={(e) => setDescription(e.target.value)} />
            : (task.description || '—')
          }
        </Descriptions.Item>
      </Descriptions>

      {editing && (
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 12 }}>
          <Button onClick={cancelEditing}>{t('common.cancel')}</Button>
          <Button type="primary" loading={saving} onClick={handleSave}>{t('common.save')}</Button>
        </div>
      )}
    </div>
  );
}
