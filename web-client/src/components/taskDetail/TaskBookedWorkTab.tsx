import { useTranslation } from 'react-i18next';
import {
  Button, Divider, InputNumber, List, Popconfirm, Select, Space, Spin, Tag, Typography,
} from 'antd';
import type { UserResponse, WorkType } from '../../api/types';
import type { useTaskBookedWork } from '../../hooks/useTaskBookedWork';

type Props = ReturnType<typeof useTaskBookedWork> & { users: UserResponse[] };

/** Renders the booked-work list and the add/edit form. */
export function TaskBookedWorkTab({
  bookedWork, bwLoading, editingBw,
  bwUserId, setBwUserId, bwType, setBwType, bwHours, setBwHours,
  savingBw, deletingBwId,
  startEditing, resetBwForm, handleSaveBookedWork, handleDeleteBookedWork,
  users,
}: Props) {
  const { t } = useTranslation();

  const workTypeLabels: Record<WorkType, string> = {
    DEVELOPMENT:   t('tasks.workTypes.DEVELOPMENT'),
    TESTING:       t('tasks.workTypes.TESTING'),
    CODE_REVIEW:   t('tasks.workTypes.CODE_REVIEW'),
    DESIGN:        t('tasks.workTypes.DESIGN'),
    PLANNING:      t('tasks.workTypes.PLANNING'),
    DOCUMENTATION: t('tasks.workTypes.DOCUMENTATION'),
    DEPLOYMENT:    t('tasks.workTypes.DEPLOYMENT'),
    MEETING:       t('tasks.workTypes.MEETING'),
    OTHER:         t('tasks.workTypes.OTHER'),
  };

  return (
    <>
      {bwLoading ? (
        <Spin size="small" />
      ) : (
        <List
          size="small"
          dataSource={bookedWork}
          locale={{ emptyText: t('tasks.noBookedWork') }}
          renderItem={(bw) => (
            <List.Item
              key={bw.id}
              actions={[
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
      )}

      <Divider orientation="left" style={{ marginTop: 16 }}>
        {editingBw ? t('tasks.editBookedWork') : t('tasks.addBookedWork')}
      </Divider>
      <Space direction="vertical" style={{ width: '100%', maxWidth: 480 }}>
        <Select
          style={{ width: '100%' }}
          placeholder={t('tasks.selectUser')}
          value={bwUserId}
          onChange={setBwUserId}
          options={users.map((u) => ({ label: u.name, value: u.id }))}
        />
        <Select
          style={{ width: '100%' }}
          value={bwType}
          onChange={setBwType}
          options={(Object.keys(workTypeLabels) as WorkType[]).map((w) => ({ label: workTypeLabels[w], value: w }))}
        />
        <InputNumber
          min={0} step={1} precision={0}
          value={bwHours}
          onChange={(v) => setBwHours(v ?? 0)}
          addonBefore={t('tasks.booked')}
          addonAfter="h"
          style={{ width: '100%' }}
        />
        <Space>
          <Button type="primary" loading={savingBw} disabled={!bwUserId} onClick={handleSaveBookedWork}>
            {editingBw ? t('common.save') : t('common.add')}
          </Button>
          {editingBw && <Button onClick={resetBwForm}>{t('common.cancel')}</Button>}
        </Space>
      </Space>
    </>
  );
}
