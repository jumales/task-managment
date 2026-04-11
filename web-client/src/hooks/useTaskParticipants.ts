import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { watchTask, removeParticipant } from '../api/taskApi';
import { useAuth } from '../auth/AuthProvider';
import type { TaskParticipantResponse } from '../api/types';

/** Manages participant list and Watch/Unwatch actions for a task. */
export function useTaskParticipants(taskId: string | undefined, initialData: TaskParticipantResponse[]) {
  const { t } = useTranslation();
  const { userId: currentUserId } = useAuth();

  const [participants, setParticipants] = useState<TaskParticipantResponse[]>(initialData);
  useEffect(() => { setParticipants(initialData); }, [initialData]);

  const [watching,    setWatching]    = useState(false);
  const [removingPId, setRemovingPId] = useState<string | null>(null);
  const [error,       setError]       = useState<string | null>(null);

  /** Returns the current user's WATCHER entry, or undefined if they are not watching. */
  const myWatcherEntry = participants.find(
    (p) => p.userId === currentUserId && p.role === 'WATCHER'
  );

  const NON_WATCHER_ROLES = ['CREATOR', 'ASSIGNEE', 'CONTRIBUTOR'] as const;

  /** True when the current user already has an active role; the Watch button should be hidden. */
  const isAlreadyActiveParticipant = participants.some(
    (p) => p.userId === currentUserId && (NON_WATCHER_ROLES as readonly string[]).includes(p.role)
  );

  /** Adds the authenticated user as a WATCHER. */
  const handleWatch = () => {
    if (!taskId) return;
    setWatching(true);
    watchTask(taskId)
      .then((created) => setParticipants((prev) => {
        // Replace existing entry for this user if already present, otherwise append
        const exists = prev.some((p) => p.id === created.id);
        return exists ? prev : [...prev, created];
      }))
      .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedWatch')))
      .finally(() => setWatching(false));
  };

  /** Removes own WATCHER entry and updates local state on success. */
  const handleRemoveParticipant = (participantId: string) => {
    if (!taskId) return;
    setRemovingPId(participantId);
    removeParticipant(taskId, participantId)
      .then(() => setParticipants((prev) => prev.filter((p) => p.id !== participantId)))
      .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedRemoveParticipant')))
      .finally(() => setRemovingPId(null));
  };

  return {
    participants,
    currentUserId,
    myWatcherEntry,
    isAlreadyActiveParticipant,
    watching,
    removingPId,
    error,
    handleWatch,
    handleRemoveParticipant,
  };
}
