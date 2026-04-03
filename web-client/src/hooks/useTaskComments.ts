import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { addComment } from '../api/taskApi';
import type { TaskCommentResponse } from '../api/types';

/** Manages comment list, new-comment input state, and the add-comment handler for a task. */
export function useTaskComments(taskId: string | undefined, initialData: TaskCommentResponse[]) {
  const { t } = useTranslation();

  const [comments,   setComments]   = useState<TaskCommentResponse[]>(initialData);
  useEffect(() => { setComments(initialData); }, [initialData]);
  const [newComment, setNewComment] = useState('');
  const [addingCmt,  setAddingCmt]  = useState(false);
  const [error,      setError]      = useState<string | null>(null);

  /** Posts a new comment and appends it to the local list on success. */
  const handleAddComment = () => {
    if (!taskId || !newComment.trim()) return;
    setAddingCmt(true);
    addComment(taskId, newComment.trim())
      .then((created) => {
        setComments((prev) => [...prev, created]);
        setNewComment('');
      })
      .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedAddComment')))
      .finally(() => setAddingCmt(false));
  };

  return {
    comments,
    newComment, setNewComment,
    addingCmt,
    error,
    handleAddComment,
  };
}
