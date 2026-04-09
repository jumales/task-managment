import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Button, Card, Col, DatePicker, Descriptions, Modal, Popconfirm, Row, Select, Space, Typography,
} from 'antd';
import { CalendarOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { TaskPhaseName, TimelineState, UserResponse } from '../../api/types';

const PAIR: Partial<Record<TimelineState, TimelineState>> = {
  PLANNED_END:   'PLANNED_START',
  REAL_END:      'REAL_START',
  PLANNED_START: 'PLANNED_END',
  REAL_START:    'REAL_END',
};
import { TIMELINE_STATES } from '../../pages/taskDetail/taskDetailConstants';
import type { useTaskTimeline } from '../../hooks/useTaskTimeline';

type Props = ReturnType<typeof useTaskTimeline> & {
  users: UserResponse[];
  taskPhaseName: TaskPhaseName;
};

const PLANNED_STATES = new Set<TimelineState>(['PLANNED_START', 'PLANNED_END']);

/** Renders the timeline cards for all four timeline states plus the set/edit modal. */
export function TaskTimelineTab({
  timelines, deletingTlState, openTlModal, handleDeleteTimeline,
  tlModalOpen, setTlModalOpen, editingState,
  tlUserId, setTlUserId, tlTimestamp, setTlTimestamp,
  savingTimeline, handleSaveTimeline,
  users, taskPhaseName,
}: Props) {
  const { t } = useTranslation();

  const userOptions = useMemo(() => users.map((u) => ({ label: u.name, value: u.id })), [users]);

  const orderError = useMemo(() => {
    if (!editingState || !tlTimestamp) return null;
    const pairedState = PAIR[editingState];
    if (!pairedState) return null;
    const paired = timelines.find((tl) => tl.state === pairedState);
    if (!paired) return null;
    const isEndState = editingState.endsWith('_END');
    const pairedDayjs = dayjs(paired.timestamp);
    if (isEndState && !tlTimestamp.isAfter(pairedDayjs)) return t('tasks.endMustBeAfterStart');
    if (!isEndState && !tlTimestamp.isBefore(pairedDayjs)) return t('tasks.startMustBeBeforeEnd');
    return null;
  }, [editingState, tlTimestamp, timelines, t]);

  return (
    <>
      <Row gutter={[16, 16]}>
        {TIMELINE_STATES.map((state) => {
          const entry = timelines.find((tl) => tl.state === state);
          return (
            <Col xs={24} sm={12} key={state}>
              <Card
                size="small"
                title={
                  <Space>
                    <CalendarOutlined />
                    {t(`tasks.timelineStates.${state}`)}
                  </Space>
                }
                extra={
                  // Planned dates are locked once the task leaves the PLANNING phase
                  (!PLANNED_STATES.has(state) || taskPhaseName === 'PLANNING') && (
                    <Space size="small">
                      <Button size="small" onClick={() => openTlModal(state)}>
                        {entry ? t('common.edit') : t('tasks.setDate')}
                      </Button>
                      {entry && (
                        <Popconfirm
                          title={t('tasks.clearTimelineConfirm')}
                          onConfirm={() => handleDeleteTimeline(state)}
                          okText={t('common.delete')}
                          okButtonProps={{ danger: true }}
                        >
                          <Button danger size="small" loading={deletingTlState === state}>
                            {t('tasks.clearDate')}
                          </Button>
                        </Popconfirm>
                      )}
                    </Space>
                  )
                }
              >
                {entry ? (
                  <Descriptions column={1} size="small">
                    <Descriptions.Item label={t('tasks.date')}>
                      {dayjs(entry.timestamp).format('YYYY-MM-DD HH:mm')}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('tasks.setBy')}>
                      {entry.setByUserName ?? entry.setByUserId}
                    </Descriptions.Item>
                  </Descriptions>
                ) : (
                  <Typography.Text type="secondary">{t('tasks.notSet')}</Typography.Text>
                )}
              </Card>
            </Col>
          );
        })}
      </Row>

      <Modal
        title={editingState ? t(`tasks.timelineStates.${editingState}`) : ''}
        open={tlModalOpen}
        onOk={handleSaveTimeline}
        onCancel={() => setTlModalOpen(false)}
        okText={t('common.save')}
        confirmLoading={savingTimeline}
        okButtonProps={{ disabled: !tlUserId || !tlTimestamp || !!orderError }}
      >
        <Space direction="vertical" style={{ width: '100%', marginTop: 16 }}>
          <DatePicker
            showTime
            style={{ width: '100%' }}
            value={tlTimestamp}
            onChange={setTlTimestamp}
            placeholder={t('tasks.selectDate')}
          />
          {orderError && (
            <Typography.Text type="danger" style={{ display: 'block', marginTop: 4 }}>
              {orderError}
            </Typography.Text>
          )}
          <Select
            style={{ width: '100%' }}
            placeholder={t('tasks.selectUser')}
            value={tlUserId}
            onChange={setTlUserId}
            options={userOptions}
          />
        </Space>
      </Modal>
    </>
  );
}
