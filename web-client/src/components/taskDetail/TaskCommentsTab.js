import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useTranslation } from 'react-i18next';
import { Button, Divider, Input, List, Space } from 'antd';
/** Renders the comments list and the add-comment form. */
export function TaskCommentsTab({ comments, newComment, setNewComment, addingCmt, handleAddComment, }) {
    const { t } = useTranslation();
    return (_jsxs(_Fragment, { children: [_jsx(List, { dataSource: comments, locale: { emptyText: t('tasks.noComments') }, renderItem: (c) => (_jsx(List.Item, { children: _jsx(List.Item.Meta, { title: c.content, description: new Date(c.createdAt).toLocaleString() }) }, c.id)) }), _jsx(Divider, { style: { marginTop: 8 } }), _jsxs(Space, { direction: "vertical", style: { width: '100%', maxWidth: 600 }, children: [_jsx(Input.TextArea, { rows: 3, value: newComment, onChange: (e) => setNewComment(e.target.value), placeholder: t('tasks.addCommentPlaceholder') }), _jsx(Button, { type: "primary", loading: addingCmt, disabled: !newComment.trim(), onClick: handleAddComment, children: t('tasks.addComment') })] })] }));
}
