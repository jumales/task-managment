import { useEffect, useState, useCallback } from 'react';
import { Table, Tabs, Alert, Spin, Input, Button } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { getHoursByTask, getHoursByProject } from '../../api/reportingApi';
import type { TaskHoursRow, ProjectHoursRow } from '../../api/types';

/** Planned vs booked hours report with by-task and by-project sub-views. */
export function HoursReport() {
  return (
    <Tabs
      items={[
        { key: 'by-task',    label: 'By Task',    children: <HoursByTask /> },
        { key: 'by-project', label: 'By Project', children: <HoursByProject /> },
      ]}
    />
  );
}

function HoursByTask() {
  const [rows, setRows] = useState<TaskHoursRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [projectFilter, setProjectFilter] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    getHoursByTask()
      .then(setRows)
      .catch(() => setError('Failed to load hours by task'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load();
    const handler = () => load();
    window.addEventListener('report-updated', handler);
    return () => window.removeEventListener('report-updated', handler);
  }, [load]);

  if (error) return <Alert type="error" message={error} />;

  const filtered = projectFilter
    ? rows.filter((r) => r.title?.toLowerCase().includes(projectFilter.toLowerCase()) ||
                         r.taskCode?.toLowerCase().includes(projectFilter.toLowerCase()))
    : rows;

  return (
    <Spin spinning={loading}>
      <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        <Input.Search
          placeholder="Filter by task code or title"
          value={projectFilter}
          onChange={(e) => setProjectFilter(e.target.value)}
          style={{ maxWidth: 320 }}
          allowClear
        />
        <Button icon={<ReloadOutlined />} size="small" onClick={load}>Refresh</Button>
      </div>
      <Table
        dataSource={filtered}
        rowKey="taskId"
        size="small"
        pagination={{ pageSize: 20 }}
        columns={[
          { title: 'Code',    dataIndex: 'taskCode',     key: 'taskCode', width: 100,
            render: (code: string | null) => code ?? '—' },
          { title: 'Task',    dataIndex: 'title',        key: 'title' },
          { title: 'Planned (h)', dataIndex: 'plannedHours', key: 'planned', width: 120, align: 'right' },
          { title: 'Booked (h)',  dataIndex: 'bookedHours',  key: 'booked',  width: 120, align: 'right' },
          {
            title: 'Δ',
            key: 'delta',
            width: 100,
            align: 'right',
            render: (_: unknown, r: TaskHoursRow) => {
              const diff = r.bookedHours - r.plannedHours;
              const color = diff > 0 ? '#cf1322' : diff < 0 ? '#389e0d' : undefined;
              return <span style={{ color }}>{diff > 0 ? `+${diff}` : diff}</span>;
            },
          },
        ]}
      />
    </Spin>
  );
}

function HoursByProject() {
  const [rows, setRows] = useState<ProjectHoursRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    getHoursByProject()
      .then(setRows)
      .catch(() => setError('Failed to load hours by project'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load();
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
        rowKey="projectId"
        size="small"
        pagination={{ pageSize: 20 }}
        columns={[
          { title: 'Project',      dataIndex: 'projectName',  key: 'project' },
          { title: 'Planned (h)',  dataIndex: 'plannedHours', key: 'planned', width: 130, align: 'right' },
          { title: 'Booked (h)',   dataIndex: 'bookedHours',  key: 'booked',  width: 130, align: 'right' },
          {
            title: 'Δ',
            key: 'delta',
            width: 100,
            align: 'right',
            render: (_: unknown, r: ProjectHoursRow) => {
              const diff = r.bookedHours - r.plannedHours;
              const color = diff > 0 ? '#cf1322' : diff < 0 ? '#389e0d' : undefined;
              return <span style={{ color }}>{diff > 0 ? `+${diff}` : diff}</span>;
            },
          },
        ]}
      />
    </Spin>
  );
}
