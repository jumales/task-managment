import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Card, Col, DatePicker, Descriptions, Divider, Input, InputNumber, List, Modal, Popconfirm, Progress, Row, Select, Space, Spin, Tabs, Tag, Typography, } from 'antd';
import { ArrowLeftOutlined, CalendarOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { getTask, getTimelines, setTimeline, deleteTimeline, getWorkLogs, createWorkLog, updateWorkLog, deleteWorkLog, getParticipants, addParticipant, removeParticipant, getTaskComments, addComment, } from '../api/taskApi';
import { getUsers } from '../api/userApi';
const STATUS_COLORS = {
    TODO: 'default',
    IN_PROGRESS: 'blue',
    DONE: 'green',
};
const TYPE_COLORS = {
    FEATURE: 'purple',
    BUG_FIXING: 'red',
    TESTING: 'cyan',
    PLANNING: 'gold',
    TECHNICAL_DEBT: 'orange',
    DOCUMENTATION: 'geekblue',
    OTHER: 'default',
};
const TIMELINE_STATES = ['PLANNED_START', 'PLANNED_END', 'REAL_START', 'REAL_END'];
/** Full-page view for a single task: overview, timeline, work logs, participants, and comments. */
export function TaskDetailPage() {
    const { id } = useParams();
    const navigate = useNavigate();
    const { t } = useTranslation();
    const [task, setTask] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [users, setUsers] = useState([]);
    // ── Timeline ─────────────────────────────────────────────────────────────
    const [timelines, setTimelines] = useState([]);
    const [tlModalOpen, setTlModalOpen] = useState(false);
    const [editingState, setEditingState] = useState(null);
    const [tlUserId, setTlUserId] = useState(null);
    const [tlTimestamp, setTlTimestamp] = useState(null);
    const [savingTimeline, setSavingTimeline] = useState(false);
    const [deletingTlState, setDeletingTlState] = useState(null);
    // ── Work logs ────────────────────────────────────────────────────────────
    const [workLogs, setWorkLogs] = useState([]);
    const [wlLoading, setWlLoading] = useState(false);
    const [editingWl, setEditingWl] = useState(null);
    const [wlUserId, setWlUserId] = useState(null);
    const [wlType, setWlType] = useState('DEVELOPMENT');
    const [wlPlanned, setWlPlanned] = useState(0);
    const [wlBooked, setWlBooked] = useState(0);
    const [savingWl, setSavingWl] = useState(false);
    const [deletingWlId, setDeletingWlId] = useState(null);
    // ── Participants ─────────────────────────────────────────────────────────
    const [participants, setParticipants] = useState([]);
    const [newPUserId, setNewPUserId] = useState(null);
    const [newPRole, setNewPRole] = useState('VIEWER');
    const [addingP, setAddingP] = useState(false);
    const [removingPId, setRemovingPId] = useState(null);
    // ── Comments ─────────────────────────────────────────────────────────────
    const [comments, setComments] = useState([]);
    const [newComment, setNewComment] = useState('');
    const [addingCmt, setAddingCmt] = useState(false);
    // ── Translation maps ─────────────────────────────────────────────────────
    const typeLabels = {
        FEATURE: t('tasks.types.FEATURE'),
        BUG_FIXING: t('tasks.types.BUG_FIXING'),
        TESTING: t('tasks.types.TESTING'),
        PLANNING: t('tasks.types.PLANNING'),
        TECHNICAL_DEBT: t('tasks.types.TECHNICAL_DEBT'),
        DOCUMENTATION: t('tasks.types.DOCUMENTATION'),
        OTHER: t('tasks.types.OTHER'),
    };
    const workTypeLabels = {
        DEVELOPMENT: t('tasks.workTypes.DEVELOPMENT'),
        TESTING: t('tasks.workTypes.TESTING'),
        CODE_REVIEW: t('tasks.workTypes.CODE_REVIEW'),
        DESIGN: t('tasks.workTypes.DESIGN'),
        PLANNING: t('tasks.workTypes.PLANNING'),
        DOCUMENTATION: t('tasks.workTypes.DOCUMENTATION'),
        DEPLOYMENT: t('tasks.workTypes.DEPLOYMENT'),
        MEETING: t('tasks.workTypes.MEETING'),
        OTHER: t('tasks.workTypes.OTHER'),
    };
    // ── Initial load ─────────────────────────────────────────────────────────
    useEffect(() => {
        if (!id)
            return;
        setLoading(true);
        Promise.all([
            getTask(id),
            getTimelines(id),
            getParticipants(id),
            getTaskComments(id),
            getUsers(),
        ])
            .then(([taskData, timelinesData, participantsData, commentsData, usersPage]) => {
            setTask(taskData);
            setTimelines(timelinesData);
            setParticipants(participantsData);
            setComments(commentsData);
            setUsers(usersPage.content);
        })
            .catch((err) => setError(err?.message ?? t('tasks.failedLoad')))
            .finally(() => setLoading(false));
        setWlLoading(true);
        getWorkLogs(id)
            .then(setWorkLogs)
            .catch(() => setError(t('tasks.failedLoadWorkLogs')))
            .finally(() => setWlLoading(false));
    }, [id]);
    // ── Timeline handlers ─────────────────────────────────────────────────────
    const openTlModal = (state) => {
        const existing = timelines.find((tl) => tl.state === state);
        setEditingState(state);
        setTlUserId(existing?.setByUserId ?? null);
        setTlTimestamp(existing ? dayjs(existing.timestamp) : null);
        setTlModalOpen(true);
    };
    const handleSaveTimeline = () => {
        if (!id || !editingState || !tlUserId || !tlTimestamp)
            return;
        setSavingTimeline(true);
        setTimeline(id, editingState, { setByUserId: tlUserId, timestamp: tlTimestamp.toISOString() })
            .then((saved) => {
            setTimelines((prev) => {
                const idx = prev.findIndex((tl) => tl.state === saved.state);
                return idx >= 0
                    ? prev.map((tl, i) => (i === idx ? saved : tl))
                    : [...prev, saved];
            });
            setTlModalOpen(false);
        })
            .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedSaveTimeline')))
            .finally(() => setSavingTimeline(false));
    };
    const handleDeleteTimeline = (state) => {
        if (!id)
            return;
        setDeletingTlState(state);
        deleteTimeline(id, state)
            .then(() => setTimelines((prev) => prev.filter((tl) => tl.state !== state)))
            .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedDeleteTimeline')))
            .finally(() => setDeletingTlState(null));
    };
    // ── Work log handlers ─────────────────────────────────────────────────────
    const resetWlForm = () => {
        setEditingWl(null);
        setWlUserId(null);
        setWlType('DEVELOPMENT');
        setWlPlanned(0);
        setWlBooked(0);
    };
    const handleSaveWorkLog = () => {
        if (!id || !wlUserId)
            return;
        setSavingWl(true);
        const request = { userId: wlUserId, workType: wlType, plannedHours: wlPlanned, bookedHours: wlBooked };
        const apiCall = editingWl
            ? updateWorkLog(id, editingWl.id, request)
            : createWorkLog(id, request);
        apiCall
            .then((saved) => {
            setWorkLogs((prev) => editingWl ? prev.map((l) => (l.id === saved.id ? saved : l)) : [...prev, saved]);
            resetWlForm();
        })
            .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedSaveWorkLog')))
            .finally(() => setSavingWl(false));
    };
    const handleDeleteWorkLog = (workLogId) => {
        if (!id)
            return;
        setDeletingWlId(workLogId);
        deleteWorkLog(id, workLogId)
            .then(() => setWorkLogs((prev) => prev.filter((l) => l.id !== workLogId)))
            .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedDeleteWorkLog')))
            .finally(() => setDeletingWlId(null));
    };
    // ── Participant handlers ──────────────────────────────────────────────────
    const handleAddParticipant = () => {
        if (!id || !newPUserId)
            return;
        setAddingP(true);
        addParticipant(id, { userId: newPUserId, role: newPRole })
            .then((created) => {
            setParticipants((prev) => [...prev, created]);
            setNewPUserId(null);
        })
            .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedAddParticipant')))
            .finally(() => setAddingP(false));
    };
    const handleRemoveParticipant = (participantId) => {
        if (!id)
            return;
        setRemovingPId(participantId);
        removeParticipant(id, participantId)
            .then(() => setParticipants((prev) => prev.filter((p) => p.id !== participantId)))
            .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedRemoveParticipant')))
            .finally(() => setRemovingPId(null));
    };
    // ── Comment handlers ──────────────────────────────────────────────────────
    const handleAddComment = () => {
        if (!id || !newComment.trim())
            return;
        setAddingCmt(true);
        addComment(id, newComment.trim())
            .then((created) => {
            setComments((prev) => [...prev, created]);
            setNewComment('');
        })
            .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedAddComment')))
            .finally(() => setAddingCmt(false));
    };
    // ── Early returns ─────────────────────────────────────────────────────────
    if (loading)
        return _jsx("div", { style: { textAlign: 'center', marginTop: 80 }, children: _jsx(Spin, { size: "large" }) });
    if (error)
        return _jsx(Alert, { type: "error", message: error, style: { margin: 24 } });
    if (!task)
        return null;
    const assignedUser = task.participants.find((p) => p.role === 'ASSIGNEE');
    // ── Tab: Timeline ─────────────────────────────────────────────────────────
    const timelineTab = (_jsx(Row, { gutter: [16, 16], children: TIMELINE_STATES.map((state) => {
            const entry = timelines.find((tl) => tl.state === state);
            return (_jsx(Col, { xs: 24, sm: 12, children: _jsx(Card, { size: "small", title: _jsxs(Space, { children: [_jsx(CalendarOutlined, {}), t(`tasks.timelineStates.${state}`)] }), extra: _jsxs(Space, { size: "small", children: [_jsx(Button, { size: "small", onClick: () => openTlModal(state), children: entry ? t('common.edit') : t('tasks.setDate') }), entry && (_jsx(Popconfirm, { title: t('tasks.clearTimelineConfirm'), onConfirm: () => handleDeleteTimeline(state), okText: t('common.delete'), okButtonProps: { danger: true }, children: _jsx(Button, { danger: true, size: "small", loading: deletingTlState === state, children: t('tasks.clearDate') }) }))] }), children: entry ? (_jsxs(Descriptions, { column: 1, size: "small", children: [_jsx(Descriptions.Item, { label: t('tasks.date'), children: dayjs(entry.timestamp).format('YYYY-MM-DD HH:mm') }), _jsx(Descriptions.Item, { label: t('tasks.setBy'), children: entry.setByUserName ?? entry.setByUserId })] })) : (_jsx(Typography.Text, { type: "secondary", children: t('tasks.notSet') })) }) }, state));
        }) }));
    // ── Tab: Work Logs ────────────────────────────────────────────────────────
    const workLogsTab = (_jsxs(_Fragment, { children: [wlLoading ? (_jsx(Spin, { size: "small" })) : (_jsx(List, { size: "small", dataSource: workLogs, locale: { emptyText: t('tasks.noWorkLogs') }, renderItem: (log) => (_jsx(List.Item, { actions: [
                        _jsx(Button, { size: "small", onClick: () => { setEditingWl(log); setWlUserId(log.userId); setWlType(log.workType); setWlPlanned(Number(log.plannedHours)); setWlBooked(Number(log.bookedHours)); }, children: t('common.edit') }, "edit"),
                        _jsx(Popconfirm, { title: t('tasks.deleteWorkLog'), onConfirm: () => handleDeleteWorkLog(log.id), okText: t('common.delete'), okButtonProps: { danger: true }, children: _jsx(Button, { danger: true, size: "small", loading: deletingWlId === log.id, children: t('common.delete') }) }, "del"),
                    ], children: _jsxs(Space, { direction: "vertical", size: 0, children: [_jsxs(Space, { children: [_jsx(Tag, { color: "blue", children: workTypeLabels[log.workType] }), _jsx(Typography.Text, { strong: true, children: log.userName ?? log.userId })] }), _jsxs(Typography.Text, { type: "secondary", children: [t('tasks.planned'), ": ", _jsxs("strong", { children: [log.plannedHours, "h"] }), ' · ', t('tasks.booked'), ": ", _jsxs("strong", { children: [log.bookedHours, "h"] })] })] }) }, log.id)) })), _jsx(Divider, { orientation: "left", style: { marginTop: 16 }, children: editingWl ? t('tasks.editWorkLog') : t('tasks.addWorkLog') }), _jsxs(Space, { direction: "vertical", style: { width: '100%', maxWidth: 480 }, children: [_jsx(Select, { style: { width: '100%' }, placeholder: t('tasks.selectUser'), value: wlUserId, onChange: setWlUserId, options: users.map((u) => ({ label: u.name, value: u.id })) }), _jsx(Select, { style: { width: '100%' }, value: wlType, onChange: setWlType, options: Object.keys(workTypeLabels).map((w) => ({ label: workTypeLabels[w], value: w })) }), _jsxs(Space, { children: [!editingWl && (_jsx(InputNumber, { min: 0, step: 1, precision: 0, value: wlPlanned, onChange: (v) => setWlPlanned(v ?? 0), addonBefore: t('tasks.planned'), addonAfter: "h" })), _jsx(InputNumber, { min: 0, step: 1, precision: 0, value: wlBooked, onChange: (v) => setWlBooked(v ?? 0), addonBefore: t('tasks.booked'), addonAfter: "h" })] }), _jsxs(Space, { children: [_jsx(Button, { type: "primary", loading: savingWl, disabled: !wlUserId, onClick: handleSaveWorkLog, children: editingWl ? t('common.save') : t('common.add') }), editingWl && _jsx(Button, { onClick: resetWlForm, children: t('common.cancel') })] })] })] }));
    // ── Tab: Participants ─────────────────────────────────────────────────────
    const participantsTab = (_jsxs(_Fragment, { children: [_jsx(List, { size: "small", dataSource: participants, locale: { emptyText: t('tasks.noParticipants') }, renderItem: (p) => (_jsx(List.Item, { actions: [
                        _jsx(Popconfirm, { title: t('tasks.removeParticipant'), onConfirm: () => handleRemoveParticipant(p.id), okText: t('common.remove'), okButtonProps: { danger: true }, children: _jsx(Button, { danger: true, size: "small", loading: removingPId === p.id, children: t('common.remove') }) }, "remove"),
                    ], children: _jsxs(Space, { children: [_jsx(Tag, { color: "blue", children: p.role }), p.userName ?? p.userId] }) }, p.id)) }), _jsx(Divider, { style: { marginTop: 16 } }), _jsxs(Space.Compact, { style: { width: '100%', maxWidth: 480 }, children: [_jsx(Select, { style: { flex: 1 }, placeholder: t('tasks.selectUser'), value: newPUserId, onChange: setNewPUserId, options: users.map((u) => ({ label: u.name, value: u.id })) }), _jsx(Select, { style: { width: 130 }, value: newPRole, onChange: setNewPRole, options: ['ASSIGNEE', 'VIEWER', 'REVIEWER'].map((r) => ({ label: r, value: r })) }), _jsx(Button, { type: "primary", loading: addingP, disabled: !newPUserId, onClick: handleAddParticipant, children: t('common.add') })] })] }));
    // ── Tab: Comments ─────────────────────────────────────────────────────────
    const commentsTab = (_jsxs(_Fragment, { children: [_jsx(List, { dataSource: comments, locale: { emptyText: t('tasks.noComments') }, renderItem: (c) => (_jsx(List.Item, { children: _jsx(List.Item.Meta, { title: c.content, description: new Date(c.createdAt).toLocaleString() }) }, c.id)) }), _jsx(Divider, { style: { marginTop: 8 } }), _jsxs(Space, { direction: "vertical", style: { width: '100%', maxWidth: 600 }, children: [_jsx(Input.TextArea, { rows: 3, value: newComment, onChange: (e) => setNewComment(e.target.value), placeholder: t('tasks.addCommentPlaceholder') }), _jsx(Button, { type: "primary", loading: addingCmt, disabled: !newComment.trim(), onClick: handleAddComment, children: t('tasks.addComment') })] })] }));
    return (_jsxs("div", { style: { padding: 24, maxWidth: 1000, margin: '0 auto' }, children: [_jsx(Button, { icon: _jsx(ArrowLeftOutlined, {}), onClick: () => navigate('/tasks'), style: { marginBottom: 16 }, children: t('tasks.backToTasks') }), _jsxs(Card, { style: { marginBottom: 24 }, children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 8 }, children: [_jsx(Typography.Title, { level: 4, style: { margin: 0 }, children: task.title }), _jsxs(Space, { children: [_jsx(Tag, { color: STATUS_COLORS[task.status], children: t(`tasks.statuses.${task.status}`) }), task.type && _jsx(Tag, { color: TYPE_COLORS[task.type], children: typeLabels[task.type] })] })] }), _jsxs(Descriptions, { column: { xs: 1, sm: 2 }, style: { marginTop: 16 }, size: "small", children: [_jsx(Descriptions.Item, { label: t('common.description'), span: 2, children: task.description || '—' }), _jsx(Descriptions.Item, { label: t('common.project'), children: task.project?.name ?? '—' }), _jsx(Descriptions.Item, { label: t('tasks.phase'), children: task.phase?.name ?? '—' }), _jsx(Descriptions.Item, { label: t('tasks.assignedTo'), children: assignedUser?.userName ?? assignedUser?.userId ?? '—' }), _jsx(Descriptions.Item, { label: t('tasks.progress'), children: _jsx(Progress, { percent: task.progress, strokeColor: task.progress === 100 ? '#52c41a' : undefined, style: { maxWidth: 300 } }) })] })] }), _jsx(Tabs, { items: [
                    { key: 'timeline', label: t('tasks.timeline'), children: timelineTab },
                    { key: 'worklogs', label: t('tasks.workLogs'), children: workLogsTab },
                    { key: 'participants', label: t('tasks.participants'), children: participantsTab },
                    { key: 'comments', label: t('tasks.comments'), children: commentsTab },
                ] }), _jsx(Modal, { title: editingState ? t(`tasks.timelineStates.${editingState}`) : '', open: tlModalOpen, onOk: handleSaveTimeline, onCancel: () => setTlModalOpen(false), okText: t('common.save'), confirmLoading: savingTimeline, okButtonProps: { disabled: !tlUserId || !tlTimestamp }, children: _jsxs(Space, { direction: "vertical", style: { width: '100%', marginTop: 16 }, children: [_jsx(DatePicker, { showTime: true, style: { width: '100%' }, value: tlTimestamp, onChange: setTlTimestamp, placeholder: t('tasks.selectDate') }), _jsx(Select, { style: { width: '100%' }, placeholder: t('tasks.selectUser'), value: tlUserId, onChange: setTlUserId, options: users.map((u) => ({ label: u.name, value: u.id })) })] }) })] }));
}
