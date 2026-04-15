import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Button, InputNumber, List, Modal, Popconfirm, Select, Space, Tag, Typography, Tooltip,
} from 'antd';
import { EditOutlined, DeleteOutlined } from '@ant-design/icons';
import type { TaskPhaseName, WorkType } from '../../api/types';
import type { useTaskBookedWork } from '../../hooks/useTaskBookedWork';
import { getWorkTypeLabels } from '../../pages/taskDetail/taskDetailConstants';

const PAGE_SIZE = 5;

type Props = ReturnType<typeof useTaskBookedWork> & {
  taskPhaseName: TaskPhaseName;
  /** When true, the task is fully finished (RELEASED/REJECTED) — add/edit/delete actions are hidden. */
  finished?: boolean;
  /** When true (supervisor role), add/edit/delete actions are hidden. */
  readOnly?: boolean;
};

/** Renders the booked-work list with an "Add" button and a modal dialog for add/edit. */
export function TaskBookedWorkTab({
  bookedWork, editingBw,
  bwType, setBwType, bwHours, setBwHours,
  dialogOpen,
  plannedHoursForType, bookedHoursForType,
  savingBw, deletingBwId,
  openAddDialog, startEditing, resetBwForm, handleSaveBookedWork, handleDeleteBookedWork,
  taskPhaseName, finished, readOnly,
}: Props) {
  const { t } = useTranslation();

  const workTypeLabels  = useMemo(() => getWorkTypeLabels(t), [t]);
  const sorted          = useMemo(
    () => [...bookedWork].sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
    [bookedWork],
  );
  const workTypeOptions = useMemo(
    () => (Object.keys(workTypeLabels) as WorkType[]).map((w) => ({ label: workTypeLabels[w], value: w })),
    [workTypeLabels],
  );

  const showActions = taskPhaseName !== 'PLANNING' && !finished && !readOnly;

  return (
    <>
      {showActions && (
        <div style={{ marginBottom: 12 }}>
          <Button type="primary" onClick={openAddDialog}>
            {t('tasks.addBookedWork')}
          </Button>
        </div>
      )}

      <Modal
        open={dialogOpen}
        title={editingBw ? t('tasks.editBookedWork') : t('tasks.addBookedWork')}
        onCancel={resetBwForm}
        onOk={handleSaveBookedWork}
        okText={editingBw ? t('common.save') : t('common.add')}
        confirmLoading={savingBw}
        destroyOnClose
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Select
            style={{ width: '100%' }}
            value={bwType}
            onChange={setBwType}
            options={workTypeOptions}
          />

          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {t('tasks.planned')}: <strong>{plannedHoursForType}h</strong>
            {'  |  '}
            {t('tasks.booked')}: <strong>{bookedHoursForType}h</strong>
          </Typography.Text>

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
        </Space>
      </Modal>

      <List
        size="small"
        dataSource={sorted}
        locale={{ emptyText: t('tasks.noBookedWork') }}
        pagination={sorted.length > PAGE_SIZE ? { pageSize: PAGE_SIZE, size: 'small', hideOnSinglePage: true } : false}
        renderItem={(bw) => (
          <List.Item
            key={bw.id}
            actions={showActions ? [
              <Tooltip key="edit" title={t('common.edit')}>
                <Button size="small" type="text" icon={<EditOutlined />} onClick={() => startEditing(bw)} />
              </Tooltip>,
              <Popconfirm
                key="del"
                title={t('tasks.deleteBookedWork')}
                onConfirm={() => handleDeleteBookedWork(bw.id)}
                okText={t('common.delete')}
                okButtonProps={{ danger: true }}
              >
                <Tooltip title={t('common.delete')}>
                  <Button danger size="small" type="text" icon={<DeleteOutlined />} loading={deletingBwId === bw.id} />
                </Tooltip>
              </Popconfirm>,
            ] : []}
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
