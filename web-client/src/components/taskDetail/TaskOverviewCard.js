import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Card, Descriptions, Progress, Space, Tag, Typography } from 'antd';
import { STATUS_COLORS, TYPE_COLORS, getTypeLabels } from '../../pages/taskDetail/taskDetailConstants';
import { resolvePhaseLabel } from '../../utils/phaseUtils';
/** Renders the task overview card: title, status/type tags, and key metadata fields. */
export function TaskOverviewCard({ task }) {
    const { t } = useTranslation();
    const typeLabels = useMemo(() => getTypeLabels(t), [t]);
    const assignedUser = task.participants.find((p) => p.role === 'ASSIGNEE');
    return (_jsxs(Card, { style: { marginBottom: 24 }, children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 8 }, children: [_jsx(Typography.Title, { level: 4, style: { margin: 0 }, children: task.title }), _jsxs(Space, { children: [_jsx(Tag, { color: STATUS_COLORS[task.status], children: t(`tasks.statuses.${task.status}`) }), task.type && _jsx(Tag, { color: TYPE_COLORS[task.type], children: typeLabels[task.type] })] })] }), _jsxs(Descriptions, { column: { xs: 1, sm: 2 }, style: { marginTop: 16 }, size: "small", children: [_jsx(Descriptions.Item, { label: t('common.description'), span: 2, children: task.description || '—' }), _jsx(Descriptions.Item, { label: t('common.project'), children: task.project?.name ?? '—' }), _jsx(Descriptions.Item, { label: t('tasks.phase'), children: resolvePhaseLabel(task.phase) }), _jsx(Descriptions.Item, { label: t('tasks.assignedTo'), children: assignedUser?.userName ?? assignedUser?.userId ?? '—' }), _jsx(Descriptions.Item, { label: t('tasks.progress'), children: _jsx(Progress, { percent: task.progress, strokeColor: task.progress === 100 ? '#52c41a' : undefined, style: { maxWidth: 300 } }) })] })] }));
}
