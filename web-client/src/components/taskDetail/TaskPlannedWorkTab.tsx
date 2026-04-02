import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Button, Divider, InputNumber, List, Select, Space, Spin, Tag, Typography,
} from 'antd';
import type { TaskStatus, UserResponse, WorkType } from '../../api/types';
import type { useTaskPlannedWork } from '../../hooks/useTaskPlannedWork';
import { getWorkTypeLabels } from '../../pages/taskDetail/taskDetailConstants';

type Props = ReturnType<typeof useTaskPlannedWork> & {
  taskStatus: TaskStatus;
  users: UserResponse[];
};

/** Renders the planned-work list and the add form (visible only when task status is TODO). */
export function TaskPlannedWorkTab({
  plannedWork, pwLoading,
  pwUserId, setPwUserId, pwType, setPwType, pwHours, setPwHours,
  savingPw, handleSavePlannedWork,
  taskStatus, users,
}: Props) {
  const { t } = useTranslation();

  const workTypeLabels = useMemo(() => getWorkTypeLabels(t), [t]);

  return (
    <>
      {pwLoading ? (
        <Spin size="small" />
      ) : (
        <List
          size="small"
          dataSource={plannedWork}
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

      {taskStatus === 'TODO' && (
        <>
          <Divider orientation="left" style={{ marginTop: 16 }}>{t('tasks.addPlannedWork')}</Divider>
          <Space direction="vertical" style={{ width: '100%', maxWidth: 480 }}>
            <Select
              style={{ width: '100%' }}
              placeholder={t('tasks.selectUser')}
              value={pwUserId}
              onChange={setPwUserId}
              options={users.map((u) => ({ label: u.name, value: u.id }))}
            />
            <Select
              style={{ width: '100%' }}
              value={pwType}
              onChange={setPwType}
              options={(Object.keys(workTypeLabels) as WorkType[]).map((w) => ({ label: workTypeLabels[w], value: w }))}
            />
            <InputNumber
              min={0} step={1} precision={0}
              value={pwHours}
              onChange={(v) => setPwHours(v ?? 0)}
              addonBefore={t('tasks.planned')}
              addonAfter="h"
              style={{ width: '100%' }}
            />
            <Button type="primary" loading={savingPw} disabled={!pwUserId} onClick={handleSavePlannedWork}>
              {t('common.add')}
            </Button>
          </Space>
        </>
      )}
    </>
  );
}
