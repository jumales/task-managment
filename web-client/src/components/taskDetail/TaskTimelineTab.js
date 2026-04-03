import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Card, Col, DatePicker, Descriptions, Modal, Popconfirm, Row, Select, Space, Typography, } from 'antd';
import { CalendarOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
const PAIR = {
    PLANNED_END: 'PLANNED_START',
    REAL_END: 'REAL_START',
    PLANNED_START: 'PLANNED_END',
    REAL_START: 'REAL_END',
};
import { TIMELINE_STATES } from '../../pages/taskDetail/taskDetailConstants';
/** Renders the timeline cards for all four timeline states plus the set/edit modal. */
export function TaskTimelineTab({ timelines, deletingTlState, openTlModal, handleDeleteTimeline, tlModalOpen, setTlModalOpen, editingState, tlUserId, setTlUserId, tlTimestamp, setTlTimestamp, savingTimeline, handleSaveTimeline, users, }) {
    const { t } = useTranslation();
    const userOptions = useMemo(() => users.map((u) => ({ label: u.name, value: u.id })), [users]);
    const orderError = useMemo(() => {
        if (!editingState || !tlTimestamp)
            return null;
        const pairedState = PAIR[editingState];
        if (!pairedState)
            return null;
        const paired = timelines.find((tl) => tl.state === pairedState);
        if (!paired)
            return null;
        const isEndState = editingState.endsWith('_END');
        const pairedDayjs = dayjs(paired.timestamp);
        if (isEndState && !tlTimestamp.isAfter(pairedDayjs))
            return t('tasks.endMustBeAfterStart');
        if (!isEndState && !tlTimestamp.isBefore(pairedDayjs))
            return t('tasks.startMustBeBeforeEnd');
        return null;
    }, [editingState, tlTimestamp, timelines, t]);
    return (_jsxs(_Fragment, { children: [_jsx(Row, { gutter: [16, 16], children: TIMELINE_STATES.map((state) => {
                    const entry = timelines.find((tl) => tl.state === state);
                    return (_jsx(Col, { xs: 24, sm: 12, children: _jsx(Card, { size: "small", title: _jsxs(Space, { children: [_jsx(CalendarOutlined, {}), t(`tasks.timelineStates.${state}`)] }), extra: _jsxs(Space, { size: "small", children: [_jsx(Button, { size: "small", onClick: () => openTlModal(state), children: entry ? t('common.edit') : t('tasks.setDate') }), entry && (_jsx(Popconfirm, { title: t('tasks.clearTimelineConfirm'), onConfirm: () => handleDeleteTimeline(state), okText: t('common.delete'), okButtonProps: { danger: true }, children: _jsx(Button, { danger: true, size: "small", loading: deletingTlState === state, children: t('tasks.clearDate') }) }))] }), children: entry ? (_jsxs(Descriptions, { column: 1, size: "small", children: [_jsx(Descriptions.Item, { label: t('tasks.date'), children: dayjs(entry.timestamp).format('YYYY-MM-DD HH:mm') }), _jsx(Descriptions.Item, { label: t('tasks.setBy'), children: entry.setByUserName ?? entry.setByUserId })] })) : (_jsx(Typography.Text, { type: "secondary", children: t('tasks.notSet') })) }) }, state));
                }) }), _jsx(Modal, { title: editingState ? t(`tasks.timelineStates.${editingState}`) : '', open: tlModalOpen, onOk: handleSaveTimeline, onCancel: () => setTlModalOpen(false), okText: t('common.save'), confirmLoading: savingTimeline, okButtonProps: { disabled: !tlUserId || !tlTimestamp || !!orderError }, children: _jsxs(Space, { direction: "vertical", style: { width: '100%', marginTop: 16 }, children: [_jsx(DatePicker, { showTime: true, style: { width: '100%' }, value: tlTimestamp, onChange: setTlTimestamp, placeholder: t('tasks.selectDate') }), orderError && (_jsx(Typography.Text, { type: "danger", style: { display: 'block', marginTop: 4 }, children: orderError })), _jsx(Select, { style: { width: '100%' }, placeholder: t('tasks.selectUser'), value: tlUserId, onChange: setTlUserId, options: userOptions })] }) })] }));
}
