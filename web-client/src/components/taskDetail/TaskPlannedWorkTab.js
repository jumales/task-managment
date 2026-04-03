import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Divider, InputNumber, List, Select, Space, Tag, Typography, } from 'antd';
import { getWorkTypeLabels } from '../../pages/taskDetail/taskDetailConstants';
/** Renders the planned-work list and the add form (visible only when task status is TODO). */
export function TaskPlannedWorkTab({ plannedWork, pwUserId, setPwUserId, pwType, setPwType, pwHours, setPwHours, savingPw, handleSavePlannedWork, taskStatus, users, }) {
    const { t } = useTranslation();
    const workTypeLabels = useMemo(() => getWorkTypeLabels(t), [t]);
    const userOptions = useMemo(() => users.map((u) => ({ label: u.name, value: u.id })), [users]);
    const workTypeOptions = useMemo(() => {
        const usedTypes = new Set(plannedWork.map((pw) => pw.workType));
        return Object.keys(workTypeLabels)
            .filter((w) => !usedTypes.has(w))
            .map((w) => ({ label: workTypeLabels[w], value: w }));
    }, [workTypeLabels, plannedWork]);
    return (_jsxs(_Fragment, { children: [_jsx(List, { size: "small", dataSource: plannedWork, locale: { emptyText: t('tasks.noPlannedWork') }, renderItem: (pw) => (_jsx(List.Item, { children: _jsxs(Space, { direction: "vertical", size: 0, children: [_jsxs(Space, { children: [_jsx(Tag, { color: "blue", children: workTypeLabels[pw.workType] }), _jsx(Typography.Text, { strong: true, children: pw.userName ?? pw.userId })] }), _jsxs(Typography.Text, { type: "secondary", children: [t('tasks.planned'), ": ", _jsxs("strong", { children: [pw.plannedHours, "h"] })] })] }) }, pw.id)) }), taskStatus === 'TODO' && workTypeOptions.length > 0 && (_jsxs(_Fragment, { children: [_jsx(Divider, { orientation: "left", style: { marginTop: 16 }, children: t('tasks.addPlannedWork') }), _jsxs(Space, { direction: "vertical", style: { width: '100%', maxWidth: 480 }, children: [_jsx(Select, { style: { width: '100%' }, placeholder: t('tasks.selectUser'), value: pwUserId, onChange: setPwUserId, options: userOptions }), _jsx(Select, { style: { width: '100%' }, value: pwType, onChange: setPwType, options: workTypeOptions }), _jsx(InputNumber, { min: 0, step: 1, precision: 0, value: pwHours, onChange: (v) => setPwHours(v ?? 0), addonBefore: t('tasks.planned'), addonAfter: "h", style: { width: '100%' } }), _jsx(Button, { type: "primary", loading: savingPw, disabled: !pwUserId, onClick: handleSavePlannedWork, children: t('common.add') })] })] }))] }));
}
