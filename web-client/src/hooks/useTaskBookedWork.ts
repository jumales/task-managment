import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getBookedWork, createBookedWork, updateBookedWork, deleteBookedWork } from '../api/taskApi';
import type { TaskBookedWorkResponse, WorkType } from '../api/types';

/** Manages booked work list, add/edit-form state, and CRUD handlers for a task. */
export function useTaskBookedWork(taskId: string | undefined, setError: (msg: string) => void) {
  const { t } = useTranslation();

  const [bookedWork,    setBookedWork]    = useState<TaskBookedWorkResponse[]>([]);
  const [bwLoading,     setBwLoading]     = useState(false);
  const [editingBw,     setEditingBw]     = useState<TaskBookedWorkResponse | null>(null);
  const [bwUserId,      setBwUserId]      = useState<string | null>(null);
  const [bwType,        setBwType]        = useState<WorkType>('DEVELOPMENT');
  const [bwHours,       setBwHours]       = useState(0);
  const [savingBw,      setSavingBw]      = useState(false);
  const [deletingBwId,  setDeletingBwId]  = useState<string | null>(null);

  useEffect(() => {
    if (!taskId) return;
    setBwLoading(true);
    getBookedWork(taskId)
      .then(setBookedWork)
      .catch(() => setError(t('tasks.failedLoadBookedWork')))
      .finally(() => setBwLoading(false));
  }, [taskId]);

  /** Populates the edit form with an existing booked-work entry. */
  const startEditing = (bw: TaskBookedWorkResponse) => {
    setEditingBw(bw);
    setBwUserId(bw.userId);
    setBwType(bw.workType);
    setBwHours(Number(bw.bookedHours));
  };

  /** Resets the add/edit form back to its default empty state. */
  const resetBwForm = () => {
    setEditingBw(null);
    setBwUserId(null);
    setBwType('DEVELOPMENT');
    setBwHours(0);
  };

  /** Creates or updates a booked-work entry and resets the form on success. */
  const handleSaveBookedWork = () => {
    if (!taskId || !bwUserId) return;
    setSavingBw(true);
    const request = { userId: bwUserId, workType: bwType, bookedHours: bwHours };
    const apiCall = editingBw
      ? updateBookedWork(taskId, editingBw.id, request)
      : createBookedWork(taskId, request);
    apiCall
      .then((saved: TaskBookedWorkResponse) => {
        setBookedWork((prev) =>
          editingBw ? prev.map((b) => (b.id === saved.id ? saved : b)) : [...prev, saved],
        );
        resetBwForm();
      })
      .catch((err: unknown) =>
        setError(
          (err as { response?: { data?: { message?: string } } })?.response?.data?.message
            ?? t('tasks.failedSaveBookedWork'),
        ),
      )
      .finally(() => setSavingBw(false));
  };

  /** Deletes a booked-work entry by id and removes it from local state on success. */
  const handleDeleteBookedWork = (bookedWorkId: string) => {
    if (!taskId) return;
    setDeletingBwId(bookedWorkId);
    deleteBookedWork(taskId, bookedWorkId)
      .then(() => setBookedWork((prev) => prev.filter((b) => b.id !== bookedWorkId)))
      .catch((err: unknown) =>
        setError(
          (err as { response?: { data?: { message?: string } } })?.response?.data?.message
            ?? t('tasks.failedDeleteBookedWork'),
        ),
      )
      .finally(() => setDeletingBwId(null));
  };

  return {
    bookedWork,
    bwLoading,
    editingBw,
    bwUserId, setBwUserId,
    bwType,   setBwType,
    bwHours,  setBwHours,
    savingBw,
    deletingBwId,
    startEditing,
    resetBwForm,
    handleSaveBookedWork,
    handleDeleteBookedWork,
  };
}
