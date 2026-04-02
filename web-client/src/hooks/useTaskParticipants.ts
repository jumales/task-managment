import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { addParticipant, removeParticipant } from '../api/taskApi';
import type { TaskParticipantResponse, TaskParticipantRole } from '../api/types';

/** Manages participant list, add-form state, and add/remove handlers for a task. */
export function useTaskParticipants(taskId: string | undefined, initialData: TaskParticipantResponse[]) {
  const { t } = useTranslation();

  const [participants, setParticipants] = useState<TaskParticipantResponse[]>(initialData);
  const [newPUserId,   setNewPUserId]   = useState<string | null>(null);
  const [newPRole,     setNewPRole]     = useState<TaskParticipantRole>('VIEWER');
  const [addingP,      setAddingP]      = useState(false);
  const [removingPId,  setRemovingPId]  = useState<string | null>(null);
  const [error,        setError]        = useState<string | null>(null);

  /** Adds a new participant with the selected user and role, then resets the user selector. */
  const handleAddParticipant = () => {
    if (!taskId || !newPUserId) return;
    setAddingP(true);
    addParticipant(taskId, { userId: newPUserId, role: newPRole })
      .then((created) => {
        setParticipants((prev) => [...prev, created]);
        setNewPUserId(null);
      })
      .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedAddParticipant')))
      .finally(() => setAddingP(false));
  };

  /** Removes a participant by id and updates local state on success. */
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
    newPUserId, setNewPUserId,
    newPRole,   setNewPRole,
    addingP,
    removingPId,
    error,
    handleAddParticipant,
    handleRemoveParticipant,
  };
}
