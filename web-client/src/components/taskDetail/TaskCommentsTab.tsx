import { useTranslation } from 'react-i18next';
import { Button, Divider, Input, List, Space } from 'antd';
import type { useTaskComments } from '../../hooks/useTaskComments';

type Props = ReturnType<typeof useTaskComments> & {
  /** When true, the task is fully finished (RELEASED/REJECTED) — the add-comment form is hidden. */
  finished?: boolean;
};

/** Renders the comments list and the add-comment form. Form is hidden when the task is finished (RELEASED/REJECTED). */
export function TaskCommentsTab({
  comments, newComment, setNewComment, addingCmt, handleAddComment, finished,
}: Props) {
  const { t } = useTranslation();

  return (
    <>
      <List
        dataSource={comments}
        locale={{ emptyText: t('tasks.noComments') }}
        renderItem={(c) => (
          <List.Item key={c.id}>
            <List.Item.Meta
              title={c.content}
              description={new Date(c.createdAt).toLocaleString()}
            />
          </List.Item>
        )}
      />
      {!finished && (
        <>
          <Divider style={{ marginTop: 8 }} />
          <Space direction="vertical" style={{ width: '100%', maxWidth: 600 }}>
            <Input.TextArea
              rows={3}
              value={newComment}
              onChange={(e) => setNewComment(e.target.value)}
              placeholder={t('tasks.addCommentPlaceholder')}
            />
            <Button
              type="primary"
              loading={addingCmt}
              disabled={!newComment.trim()}
              onClick={handleAddComment}
            >
              {t('tasks.addComment')}
            </Button>
          </Space>
        </>
      )}
    </>
  );
}
