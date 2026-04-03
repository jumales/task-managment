import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Spin, Tabs } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { useTaskDetailData } from '../hooks/useTaskDetailData';
import { useTaskTimeline } from '../hooks/useTaskTimeline';
import { useTaskPlannedWork } from '../hooks/useTaskPlannedWork';
import { useTaskBookedWork } from '../hooks/useTaskBookedWork';
import { useTaskParticipants } from '../hooks/useTaskParticipants';
import { useTaskComments } from '../hooks/useTaskComments';
import { TaskOverviewCard } from '../components/taskDetail/TaskOverviewCard';
import { TaskTimelineTab } from '../components/taskDetail/TaskTimelineTab';
import { TaskPlannedWorkTab } from '../components/taskDetail/TaskPlannedWorkTab';
import { TaskBookedWorkTab } from '../components/taskDetail/TaskBookedWorkTab';
import { TaskParticipantsTab } from '../components/taskDetail/TaskParticipantsTab';
import { TaskCommentsTab } from '../components/taskDetail/TaskCommentsTab';
/** Full-page view for a single task: overview, timeline, work logs, participants, and comments. */
export function TaskDetailPage() {
    const { id } = useParams();
    const navigate = useNavigate();
    const { t } = useTranslation();
    const { data, loading, error } = useTaskDetailData(id);
    const timeline = useTaskTimeline(id, data?.timelines ?? []);
    const plannedWork = useTaskPlannedWork(id, data?.plannedWork ?? []);
    const bookedWork = useTaskBookedWork(id, data?.bookedWork ?? []);
    const participants = useTaskParticipants(id, data?.participants ?? []);
    const comments = useTaskComments(id, data?.comments ?? []);
    if (loading)
        return _jsx("div", { style: { textAlign: 'center', marginTop: 80 }, children: _jsx(Spin, { size: "large" }) });
    if (error)
        return _jsx(Alert, { type: "error", message: error, style: { margin: 24 } });
    if (!data)
        return null;
    return (_jsxs("div", { style: { padding: 24, maxWidth: 1000, margin: '0 auto' }, children: [_jsx(Button, { icon: _jsx(ArrowLeftOutlined, {}), onClick: () => navigate('/tasks'), style: { marginBottom: 16 }, children: t('tasks.backToTasks') }), _jsx(TaskOverviewCard, { task: data.task }), _jsx(Tabs, { items: [
                    { key: 'timeline', label: t('tasks.timeline'), children: _jsx(TaskTimelineTab, { ...timeline, users: data.users, phaseName: data.task.phase?.name }) },
                    { key: 'plannedwork', label: t('tasks.plannedWork'), children: _jsx(TaskPlannedWorkTab, { ...plannedWork, users: data.users, taskStatus: data.task.status }) },
                    { key: 'bookedwork', label: t('tasks.bookedWork'), children: _jsx(TaskBookedWorkTab, { ...bookedWork, users: data.users }) },
                    { key: 'participants', label: t('tasks.participants'), children: _jsx(TaskParticipantsTab, { ...participants, users: data.users }) },
                    { key: 'comments', label: t('tasks.comments'), children: _jsx(TaskCommentsTab, { ...comments }) },
                ] })] }));
}
