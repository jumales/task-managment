import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Divider, List, Popconfirm, Select, Space, Tag } from 'antd';
import type { TaskParticipantRole, UserResponse } from '../../api/types';
import type { useTaskParticipants } from '../../hooks/useTaskParticipants';

// Static — roles never change at runtime
const ROLE_OPTIONS = (['ASSIGNEE', 'VIEWER', 'REVIEWER'] as TaskParticipantRole[]).map((r) => ({ label: r, value: r }));

type Props = ReturnType<typeof useTaskParticipants> & { users: UserResponse[] };

/** Renders the participants list and the add-participant form. */
export function TaskParticipantsTab({
  participants, removingPId, handleRemoveParticipant,
  newPUserId, setNewPUserId, newPRole, setNewPRole,
  addingP, handleAddParticipant,
  users,
}: Props) {
  const { t } = useTranslation();

  const userOptions = useMemo(() => users.map((u) => ({ label: u.name, value: u.id })), [users]);

  return (
    <>
      <List
        size="small"
        dataSource={participants}
        locale={{ emptyText: t('tasks.noParticipants') }}
        renderItem={(p) => (
          <List.Item
            key={p.id}
            actions={[
              <Popconfirm
                key="remove"
                title={t('tasks.removeParticipant')}
                onConfirm={() => handleRemoveParticipant(p.id)}
                okText={t('common.remove')}
                okButtonProps={{ danger: true }}
              >
                <Button danger size="small" loading={removingPId === p.id}>{t('common.remove')}</Button>
              </Popconfirm>,
            ]}
          >
            <Space>
              <Tag color="blue">{p.role}</Tag>
              {p.userName ?? p.userEmail ?? '—'}
            </Space>
          </List.Item>
        )}
      />
      <Divider style={{ marginTop: 16 }} />
      <Space.Compact style={{ width: '100%', maxWidth: 480 }}>
        <Select
          style={{ flex: 1 }}
          placeholder={t('tasks.selectUser')}
          value={newPUserId}
          onChange={setNewPUserId}
          options={userOptions}
        />
        <Select
          style={{ width: 130 }}
          value={newPRole}
          onChange={setNewPRole}
          options={ROLE_OPTIONS}
        />
        <Button type="primary" loading={addingP} disabled={!newPUserId} onClick={handleAddParticipant}>
          {t('common.add')}
        </Button>
      </Space.Compact>
    </>
  );
}
