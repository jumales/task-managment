import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Divider, List, Popconfirm, Space, Tag, Tooltip } from 'antd';
import { BellOutlined } from '@ant-design/icons';
import type { useTaskParticipants } from '../../hooks/useTaskParticipants';

const PAGE_SIZE = 5;

type Props = ReturnType<typeof useTaskParticipants> & {
  /** When true (supervisor role), the unwatch action is hidden. */
  readOnly?: boolean;
};

/** Renders a paginated list of task participants. */
export function TaskParticipantsTab({
  participants,
  currentUserId,
  removingPId,
  error,
  handleRemoveParticipant,
  readOnly,
}: Props) {
  const { t } = useTranslation();

  const sorted = useMemo(() => [...participants].reverse(), [participants]);

  return (
    <>
      {error && <Alert type="error" message={error} style={{ marginBottom: 8 }} />}

      <Divider style={{ marginTop: 4, marginBottom: 8 }} />

      <List
        size="small"
        dataSource={sorted}
        locale={{ emptyText: t('tasks.noParticipants') }}
        pagination={sorted.length > PAGE_SIZE ? { pageSize: PAGE_SIZE, size: 'small', hideOnSinglePage: true } : false}
        renderItem={(p) => {
          const isOwnWatcher = p.role === 'WATCHER' && p.userId === currentUserId;
          return (
            <List.Item
              key={p.id}
              actions={
                isOwnWatcher && !readOnly
                  ? [
                      <Popconfirm
                        key="remove"
                        title={t('tasks.unwatchConfirm')}
                        onConfirm={() => handleRemoveParticipant(p.id)}
                        okText={t('tasks.unwatch')}
                        okButtonProps={{ danger: true }}
                      >
                        <Tooltip title={t('tasks.unwatch')}>
                          <Button danger size="small" type="text" icon={<BellOutlined />} loading={removingPId === p.id} />
                        </Tooltip>
                      </Popconfirm>,
                    ]
                  : []
              }
            >
              <Space>
                <Tag color="blue">{p.role}</Tag>
                {p.userName ?? p.userEmail ?? '—'}
              </Space>
            </List.Item>
          );
        }}
      />
    </>
  );
}
