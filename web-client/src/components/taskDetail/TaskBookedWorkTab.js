import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Divider, InputNumber, List, Popconfirm, Select, Space, Tag, Typography, } from 'antd';
import { getWorkTypeLabels } from '../../pages/taskDetail/taskDetailConstants';
/** Renders the booked-work list and the add/edit form. */
export function TaskBookedWorkTab({ bookedWork, editingBw, bwUserId, setBwUserId, bwType, setBwType, bwHours, setBwHours, savingBw, deletingBwId, startEditing, resetBwForm, handleSaveBookedWork, handleDeleteBookedWork, users, }) {
    const { t } = useTranslation();
    const workTypeLabels = useMemo(() => getWorkTypeLabels(t), [t]);
    const userOptions = useMemo(() => users.map((u) => ({ label: u.name, value: u.id })), [users]);
    const workTypeOptions = useMemo(() => Object.keys(workTypeLabels).map((w) => ({ label: workTypeLabels[w], value: w })), [workTypeLabels]);
    return (_jsxs(_Fragment, { children: [_jsx(List, { size: "small", dataSource: bookedWork, locale: { emptyText: t('tasks.noBookedWork') }, renderItem: (bw) => (_jsx(List.Item, { actions: [
                        _jsx(Button, { size: "small", onClick: () => startEditing(bw), children: t('common.edit') }, "edit"),
                        _jsx(Popconfirm, { title: t('tasks.deleteBookedWork'), onConfirm: () => handleDeleteBookedWork(bw.id), okText: t('common.delete'), okButtonProps: { danger: true }, children: _jsx(Button, { danger: true, size: "small", loading: deletingBwId === bw.id, children: t('common.delete') }) }, "del"),
                    ], children: _jsxs(Space, { direction: "vertical", size: 0, children: [_jsxs(Space, { children: [_jsx(Tag, { color: "green", children: workTypeLabels[bw.workType] }), _jsx(Typography.Text, { strong: true, children: bw.userName ?? bw.userId })] }), _jsxs(Typography.Text, { type: "secondary", children: [t('tasks.booked'), ": ", _jsxs("strong", { children: [bw.bookedHours, "h"] })] })] }) }, bw.id)) }), _jsx(Divider, { orientation: "left", style: { marginTop: 16 }, children: editingBw ? t('tasks.editBookedWork') : t('tasks.addBookedWork') }), _jsxs(Space, { direction: "vertical", style: { width: '100%', maxWidth: 480 }, children: [_jsx(Select, { style: { width: '100%' }, placeholder: t('tasks.selectUser'), value: bwUserId, onChange: setBwUserId, options: userOptions }), _jsx(Select, { style: { width: '100%' }, value: bwType, onChange: setBwType, options: workTypeOptions }), _jsx(InputNumber, { min: 0, step: 1, precision: 0, value: bwHours, onChange: (v) => setBwHours(v ?? 0), addonBefore: t('tasks.booked'), addonAfter: "h", style: { width: '100%' } }), _jsxs(Space, { children: [_jsx(Button, { type: "primary", loading: savingBw, disabled: !bwUserId, onClick: handleSaveBookedWork, children: editingBw ? t('common.save') : t('common.add') }), editingBw && _jsx(Button, { onClick: resetBwForm, children: t('common.cancel') })] })] })] }));
}
