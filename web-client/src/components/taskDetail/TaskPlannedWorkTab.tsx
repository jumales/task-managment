import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Button, Divider, InputNumber, List, Select, Space, Tag, Typography,
} from 'antd';
import type { TaskPhaseName, WorkType } from '../../api/types';
import type { useTaskPlannedWork } from '../../hooks/useTaskPlannedWork';
import { getWorkTypeLabels } from '../../pages/taskDetail/taskDetailConstants';

const PAGE_SIZE = 5;

type Props = ReturnType<typeof useTaskPlannedWork> & {
  taskPhaseName: TaskPhaseName;
};

/** Renders the add-planned-work form (top, PLANNING phase only) followed by the paginated planned-work list. */
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

  const showForm = taskPhaseName === 'PLANNING' && workTypeOptions.length > 0;

  return (
    <>
      {showForm && (
        <>
          <Space direction="vertical" style={{ width: '100%', maxWidth: 480 }}>
            <Select
              style={{ width: '100%' }}
              value={pwType}
              onChange={setPwType}
              options={workTypeOptions}
            />
            <Space align="center">
              <Typography.Text type="secondary">{t('tasks.planned')}</Typography.Text>
              <InputNumber
                min={0} step={1} precision={0}
                value={pwHours}
                onChange={(v) => setPwHours(v ?? 0)}
                suffix="h"
                style={{ width: 120 }}
              />
            </Space>
            <Button type="primary" loading={savingPw} onClick={handleSavePlannedWork} disabled={pwHours <= 0}>
              {t('common.add')}
            </Button>
          </Space>
          <Divider style={{ marginBottom: 8 }} />
        </>
      )}

      <List
        size="small"
        dataSource={plannedWork}
        locale={{ emptyText: t('tasks.noPlannedWork') }}
        pagination={plannedWork.length > PAGE_SIZE ? { pageSize: PAGE_SIZE, size: 'small', hideOnSinglePage: true } : false}
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
    </>
  );
}
