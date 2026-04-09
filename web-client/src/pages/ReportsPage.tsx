import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Table, Tabs, Tag, Typography, Spin, Alert, Button } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { getMyTasks, getMyTasksFiltered } from '../api/reportingApi';
import { reportingSocket } from '../realtime/reportingSocket';
import { HoursReport } from './reports/HoursReport';
import type { MyTaskReport, TaskStatus } from '../api/types';
import keycloak from '../auth/keycloak';

const { Title } = Typography;

const STATUS_COLOR: Record<TaskStatus, string> = {
  TODO:        'default',
  IN_PROGRESS: 'processing',
  DONE:        'success',
};

/** Reports page — My Tasks views (all / 5d / 30d) plus planned-vs-booked hours. */
export function ReportsPage() {
  const userId: string | undefined = (keycloak.tokenParsed as Record<string, unknown> | undefined)?.sub as string | undefined;
  const [activeTab, setActiveTab] = useState('my-tasks');

  useEffect(() => {
    if (!userId) return;
    // Re-fetch whichever tab is active when the server pushes an update
    const unsub = reportingSocket.subscribe(userId, () => {
      setActiveTab((t) => t); // triggers a re-render; child components re-fetch in their own useEffects via key trick below
      window.dispatchEvent(new CustomEvent('report-updated'));
    });
    return unsub;
  }, [userId]);

  return (
    <div>
      <Title level={3}>Reports</Title>
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          { key: 'my-tasks',    label: 'My Tasks',             children: <MyTasksTab days={null} /> },
          { key: 'my-tasks-5',  label: 'My Tasks (last 5 d)',  children: <MyTasksTab days={5} /> },
          { key: 'my-tasks-30', label: 'My Tasks (last 30 d)', children: <MyTasksTab days={30} /> },
          { key: 'hours',       label: 'Hours',                children: <HoursReport /> },
        ]}
      />
    </div>
  );
}

function MyTasksTab({ days }: { days: number | null }) {
  const navigate = useNavigate();
  const [rows, setRows] = useState<MyTaskReport[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    const fetch = days == null ? getMyTasks() : getMyTasksFiltered(days);
    fetch
      .then(setRows)
      .catch(() => setError('Failed to load tasks'))
      .finally(() => setLoading(false));
  }, [days]);

  useEffect(() => {
    load();
    // Re-fetch when a push notification arrives
    const handler = () => load();
    window.addEventListener('report-updated', handler);
    return () => window.removeEventListener('report-updated', handler);
  }, [load]);

  if (error) return <Alert type="error" message={error} />;

  return (
    <Spin spinning={loading}>
      <div style={{ marginBottom: 8, display: 'flex', justifyContent: 'flex-end' }}>
        <Button icon={<ReloadOutlined />} size="small" onClick={load}>Refresh</Button>
      </div>
      <Table
        dataSource={rows}
        rowKey="id"
        size="small"
        pagination={{ pageSize: 20 }}
        columns={[
          { title: 'Code',        dataIndex: 'taskCode',    key: 'taskCode', width: 110 },
          { title: 'Title',       dataIndex: 'title',       key: 'title' },
          { title: 'Description', dataIndex: 'description', key: 'description', ellipsis: true },
          {
            title: 'Status',
            dataIndex: 'status',
            key: 'status',
            width: 130,
            render: (s: TaskStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
          },
          {
            title: 'Planned Start',
            dataIndex: 'plannedStart',
            key: 'plannedStart',
            width: 130,
            render: (v: string | null) => v ? new Date(v).toLocaleDateString() : '—',
          },
          {
            title: 'Planned End',
            dataIndex: 'plannedEnd',
            key: 'plannedEnd',
            width: 130,
            render: (v: string | null) => v ? new Date(v).toLocaleDateString() : '—',
          },
          {
            title: '',
            key: 'action',
            width: 80,
            render: (_: unknown, row: MyTaskReport) => (
              <Button size="small" type="link" onClick={() => navigate(`/tasks/${row.id}`)}>
                Open
              </Button>
            ),
          },
        ]}
      />
    </Spin>
  );
}
