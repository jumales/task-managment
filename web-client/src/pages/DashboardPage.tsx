import { useEffect, useState } from 'react';
import { Typography, Spin, Alert } from 'antd';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
} from 'recharts';
import { getHoursByProject } from '../api/reportingApi';
import type { ProjectHoursRow } from '../api/types';

/** Dashboard — landing page showing a planned-vs-booked hours chart per project. */
export function DashboardPage() {
  const [rows, setRows] = useState<ProjectHoursRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getHoursByProject()
      .then(setRows)
      .catch(() => setError('Could not load hours data'))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div>
      <Typography.Title level={3}>Dashboard</Typography.Title>

      <Typography.Title level={5} style={{ marginTop: 24 }}>Planned vs Booked Hours — by Project</Typography.Title>

      {error && <Alert type="warning" message={error} style={{ marginBottom: 16 }} />}

      <Spin spinning={loading}>
        {rows.length === 0 && !loading && !error && (
          <Typography.Text type="secondary">No hours data yet.</Typography.Text>
        )}
        {rows.length > 0 && (
          <ResponsiveContainer width="100%" height={320}>
            <BarChart
              data={rows.map((r) => ({
                name: r.projectName ?? r.projectId,
                Planned: r.plannedHours,
                Booked: r.bookedHours,
              }))}
              margin={{ top: 8, right: 24, left: 0, bottom: 8 }}
            >
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="name" tick={{ fontSize: 12 }} />
              <YAxis unit="h" />
              <Tooltip formatter={(v) => [`${v}h`]} />
              <Legend />
              <Bar dataKey="Planned" fill="#1677ff" radius={[4, 4, 0, 0]} />
              <Bar dataKey="Booked"  fill="#52c41a" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </Spin>
    </div>
  );
}
