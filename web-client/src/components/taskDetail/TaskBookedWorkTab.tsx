import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Button, Divider, InputNumber, List, Popconfirm, Select, Space, Tag, Typography,
} from 'antd';
import type { TaskPhaseName, UserResponse, WorkType } from '../../api/types';
import type { useTaskBookedWork } from '../../hooks/useTaskBookedWork';
import { getWorkTypeLabels } from '../../pages/taskDetail/taskDetailConstants';

const PAGE_SIZE = 5;

type Props = ReturnType<typeof useTaskBookedWork> & {
  users: UserResponse[];
  taskPhaseName: TaskPhaseName;
  /** When true, the task is fully finished (RELEASED/REJECTED) — add/edit/delete actions are hidden. */
  finished?: boolean;
};

/** Renders the add/edit booked-work form (top, hidden in PLANNING or finished) followed by the paginated booked-work list. */
export function TaskBookedWorkTab({
  bookedWork, editingBw,
  bwUserId, setBwUserId, bwType, setBwType, bwHours, setBwHours,
  savingBw, deletingBwId,
  startEditing, resetBwForm, handleSaveBookedWork, handleDeleteBookedWork,
  users, taskPhaseName, finished,
}: Props) {
  const { t } = useTranslation();

  const workTypeLabels  = useMemo(() => getWorkTypeLabels(t), [t]);
  const userOptions     = useMemo(() => users.map((u) => ({ label: u.name, value: u.id })), [users]);
  const workTypeOptions = useMemo(
    () => (Object.keys(workTypeLabels) as WorkType[]).map((w) => ({ label: workTypeLabels[w], value: w })),
    [workTypeLabels],
  );

  const showForm = taskPhaseName !== 'PLANNING' && !finished;

  return (
    <>
      {showForm && (
        <>
          <Divider orientation="left" style={{ marginTop: 0 }}>
            {editingBw ? t('tasks.editBookedWork') : t('tasks.addBookedWork')}
          </Divider>
          <Space direction="vertical" style={{ width: '100%', maxWidth: 480, marginBottom: 16 }}>
            <Select
              style={{ width: '100%' }}
              placeholder={t('tasks.selectUser')}
              value={bwUserId}
              onChange={setBwUserId}
              options={userOptions}
            />
            <Select
              style={{ width: '100%' }}
              value={bwType}
              onChange={setBwType}
              options={workTypeOptions}
            />
            <Space align="center">
              <Typography.Text type="secondary">{t('tasks.booked')}</Typography.Text>
              <InputNumber
                min={0} step={1} precision={0}
                value={bwHours}
                onChange={(v) => setBwHours(v ?? 0)}
                suffix="h"
                style={{ width: 120 }}
              />
            </Space>
            <Space>
              <Button type="primary" loading={savingBw} disabled={!bwUserId} onClick={handleSaveBookedWork}>
                {editingBw ? t('common.save') : t('common.add')}
              </Button>
              {editingBw && <Button onClick={resetBwForm}>{t('common.cancel')}</Button>}
            </Space>
          </Space>
          <Divider style={{ marginTop: 0, marginBottom: 8 }} />
        </>
      )}

      <List
        size="small"
        dataSource={bookedWork}
        locale={{ emptyText: t('tasks.noBookedWork') }}
        pagination={bookedWork.length > PAGE_SIZE ? { pageSize: PAGE_SIZE, size: 'small', hideOnSinglePage: true } : false}
        renderItem={(bw) => (
          <List.Item
            key={bw.id}
            actions={finished ? [] : [
              <Button key="edit" size="small" onClick={() => startEditing(bw)}>
                {t('common.edit')}
              </Button>,
              <Popconfirm
                key="del"
                title={t('tasks.deleteBookedWork')}
                onConfirm={() => handleDeleteBookedWork(bw.id)}
                okText={t('common.delete')}
                okButtonProps={{ danger: true }}
              >
                <Button danger size="small" loading={deletingBwId === bw.id}>{t('common.delete')}</Button>
              </Popconfirm>,
            ]}
          >
            <Space direction="vertical" size={0}>
              <Space>
                <Tag color="green">{workTypeLabels[bw.workType]}</Tag>
                <Typography.Text strong>{bw.userName ?? bw.userId}</Typography.Text>
              </Space>
              <Typography.Text type="secondary">
                {t('tasks.booked')}: <strong>{bw.bookedHours}h</strong>
              </Typography.Text>
            </Space>
          </List.Item>
        )}
      />
    </>
  );
}
