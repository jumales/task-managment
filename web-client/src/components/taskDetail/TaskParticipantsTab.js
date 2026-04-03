import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Divider, List, Popconfirm, Select, Space, Tag } from 'antd';
// Static — roles never change at runtime
const ROLE_OPTIONS = ['ASSIGNEE', 'VIEWER', 'REVIEWER'].map((r) => ({ label: r, value: r }));
/** Renders the participants list and the add-participant form. */
export function TaskParticipantsTab({ participants, removingPId, handleRemoveParticipant, newPUserId, setNewPUserId, newPRole, setNewPRole, addingP, handleAddParticipant, users, }) {
    const { t } = useTranslation();
    const userOptions = useMemo(() => users.map((u) => ({ label: u.name, value: u.id })), [users]);
    return (_jsxs(_Fragment, { children: [_jsx(List, { size: "small", dataSource: participants, locale: { emptyText: t('tasks.noParticipants') }, renderItem: (p) => (_jsx(List.Item, { actions: [
                        _jsx(Popconfirm, { title: t('tasks.removeParticipant'), onConfirm: () => handleRemoveParticipant(p.id), okText: t('common.remove'), okButtonProps: { danger: true }, children: _jsx(Button, { danger: true, size: "small", loading: removingPId === p.id, children: t('common.remove') }) }, "remove"),
                    ], children: _jsxs(Space, { children: [_jsx(Tag, { color: "blue", children: p.role }), p.userName ?? p.userId] }) }, p.id)) }), _jsx(Divider, { style: { marginTop: 16 } }), _jsxs(Space.Compact, { style: { width: '100%', maxWidth: 480 }, children: [_jsx(Select, { style: { flex: 1 }, placeholder: t('tasks.selectUser'), value: newPUserId, onChange: setNewPUserId, options: userOptions }), _jsx(Select, { style: { width: 130 }, value: newPRole, onChange: setNewPRole, options: ROLE_OPTIONS }), _jsx(Button, { type: "primary", loading: addingP, disabled: !newPUserId, onClick: handleAddParticipant, children: t('common.add') })] })] }));
}
