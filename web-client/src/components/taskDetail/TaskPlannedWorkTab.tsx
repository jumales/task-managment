import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Button, Divider, InputNumber, List, Select, Space, Tag, Typography,
} from 'antd';
import type { TaskPhaseName, WorkType } from '../../api/types';
import type { useTaskPlannedWork } from '../../hooks/useTaskPlannedWork';
import { getWorkTypeLabels } from '../../pages/taskDetail/taskDetailConstants';

type Props = ReturnType<typeof useTaskPlannedWork> & {
  taskPhaseName: TaskPhaseName;
};

/** Renders the planned-work list and the add form (visible only when task is in the PLANNING phase). */
export function TaskPlannedWorkTab({
  plannedWork,
  pwType, setPwType, pwHours, setPwHours,
  savingPw, handleSavePlannedWork,
  taskPhaseName,
}: Props) {
  const { t } = useTranslation();

  const workTypeLabels  = useMemo(() => getWorkTypeLabels(t), [t]);
  const workTypeOptions = useMemo(() => {
    const usedTypes = new Set(plannedWork.map((pw) => pw.workType));
    return (Object.keys(workTypeLabels) as WorkType[])
      .filter((w) => !usedTypes.has(w))
      .map((w) => ({ label: workTypeLabels[w], value: w }));
  }, [workTypeLabels, plannedWork]);

  return (
    <>
      <List
        size="small"
        dataSource={plannedWork}
        locale={{ emptyText: t('tasks.noPlannedWork') }}
        renderItem={(pw) => (
          <List.Item key={pw.id}>
            <Space direction="vertical" size={0}>
              <Space>
                <Tag color="blue">{workTypeLabels[pw.workType]}</Tag>
                <Typography.Text strong>{pw.userName ?? '—'}</Typography.Text>
              </Space>
              <Typography.Text type="secondary">
                {t('tasks.planned')}: <strong>{pw.plannedHours}h</strong>
              </Typography.Text>
            </Space>
          </List.Item>
        )}
      />

      {taskPhaseName === 'PLANNING' && workTypeOptions.length > 0 && (
        <>
          <Divider orientation="left" style={{ marginTop: 16 }}>{t('tasks.addPlannedWork')}</Divider>
          <Space direction="vertical" style={{ width: '100%', maxWidth: 480 }}>
            <Select
              style={{ width: '100%' }}
              value={pwType}
              onChange={setPwType}
              options={workTypeOptions}
            />
            <InputNumber
              min={0} step={1} precision={0}
              value={pwHours}
              onChange={(v) => setPwHours(v ?? 0)}
              addonBefore={t('tasks.planned')}
              addonAfter="h"
              style={{ width: '100%' }}
            />
            <Button type="primary" loading={savingPw} onClick={handleSavePlannedWork} disabled={pwHours <= 0}>
              {t('common.add')}
            </Button>
          </Space>
        </>
      )}
    </>
  );
}
