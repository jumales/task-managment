import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Divider, Input, List, Space } from 'antd';
import type { useTaskComments } from '../../hooks/useTaskComments';

const PAGE_SIZE = 5;

type Props = ReturnType<typeof useTaskComments> & {
  /** When true, the task is fully finished (RELEASED/REJECTED) — the add-comment form is hidden. */
  finished?: boolean;
  /** When true (supervisor role), the add-comment form is hidden. */
  readOnly?: boolean;
};

/** Renders the add-comment form (top) followed by the paginated comments list. Form is hidden when the task is finished (RELEASED/REJECTED). */
export function TaskCommentsTab({
  comments, newComment, setNewComment, addingCmt, handleAddComment, finished, readOnly,
}: Props) {
  const { t } = useTranslation();

  const sorted = useMemo(
    () => [...comments].sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
    [comments],
  );

  return (
    <>
      {!finished && !readOnly && (
        <>
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
          <Divider style={{ marginBottom: 8 }} />
        </>
      )}

      <List
        dataSource={sorted}
        locale={{ emptyText: t('tasks.noComments') }}
        pagination={sorted.length > PAGE_SIZE ? { pageSize: PAGE_SIZE, size: 'small', hideOnSinglePage: true } : false}
        renderItem={(c) => (
          <List.Item key={c.id}>
            <List.Item.Meta
              title={c.content}
              description={new Date(c.createdAt).toLocaleString()}
            />
          </List.Item>
        )}
      />
    </>
  );
}
