import { useState, useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { createBookedWork, updateBookedWork, deleteBookedWork, joinTask } from '../api/taskApi';
import type { TaskBookedWorkResponse, TaskPlannedWorkResponse, WorkType } from '../api/types';
import { getApiErrorMessage } from '../utils/apiError';

/** Manages booked work list, dialog state, add/edit-form state, and CRUD handlers for a task. */
export function useTaskBookedWork(
  taskId: string | undefined,
  initialData: TaskBookedWorkResponse[],
  plannedWork: TaskPlannedWorkResponse[],
) {
  const { t } = useTranslation();

  const [bookedWork,   setBookedWork]   = useState<TaskBookedWorkResponse[]>(initialData);
  useEffect(() => { setBookedWork(initialData); }, [initialData]);
  const [editingBw,    setEditingBw]    = useState<TaskBookedWorkResponse | null>(null);
  const [bwType,       setBwType]       = useState<WorkType>('DEVELOPMENT');
  const [bwHours,      setBwHours]      = useState(0);
  const [dialogOpen,   setDialogOpen]   = useState(false);
  const [savingBw,     setSavingBw]     = useState(false);
  const [deletingBwId, setDeletingBwId] = useState<string | null>(null);
  const [error,        setError]        = useState<string | null>(null);

  /** Planned hours for the currently selected work type (0 if no plan entry exists). */
  const plannedHoursForType = useMemo(
    () => Number(plannedWork.find((p) => p.workType === bwType)?.plannedHours ?? 0),
    [plannedWork, bwType],
  );

  /** Total already-booked hours for the selected work type, excluding the entry being edited. */
  const bookedHoursForType = useMemo(
    () => bookedWork
      .filter((b) => b.workType === bwType && b.id !== editingBw?.id)
      .reduce((sum, b) => sum + Number(b.bookedHours), 0),
    [bookedWork, bwType, editingBw],
  );

  const openAddDialog = () => {
    setEditingBw(null);
    setBwType('DEVELOPMENT');
    setBwHours(0);
    setDialogOpen(true);
  };

  const startEditing = (bw: TaskBookedWorkResponse) => {
    setEditingBw(bw);
    setBwType(bw.workType);
    setBwHours(Number(bw.bookedHours));
    setDialogOpen(true);
  };

  const resetBwForm = () => {
    setEditingBw(null);
    setBwType('DEVELOPMENT');
    setBwHours(0);
    setDialogOpen(false);
  };

  const handleSaveBookedWork = () => {
    if (!taskId) return;
    setSavingBw(true);
    const request = { workType: bwType, bookedHours: bwHours };
    const apiCall = editingBw
      ? updateBookedWork(taskId, editingBw.id, request)
      : createBookedWork(taskId, request);
    apiCall
      .then((saved: TaskBookedWorkResponse) => {
        setBookedWork((prev) =>
          editingBw ? prev.map((b) => (b.id === saved.id ? saved : b)) : [...prev, saved],
        );
        if (!editingBw) joinTask(taskId);
        resetBwForm();
      })
      .catch((err) => setError(getApiErrorMessage(err, t('tasks.failedSaveBookedWork'))))
      .finally(() => setSavingBw(false));
  };

  const handleDeleteBookedWork = (bookedWorkId: string) => {
    if (!taskId) return;
    setDeletingBwId(bookedWorkId);
    deleteBookedWork(taskId, bookedWorkId)
      .then(() => setBookedWork((prev) => prev.filter((b) => b.id !== bookedWorkId)))
      .catch((err) => setError(getApiErrorMessage(err, t('tasks.failedDeleteBookedWork'))))
      .finally(() => setDeletingBwId(null));
  };

  return {
    bookedWork,
    editingBw,
    bwType,   setBwType,
    bwHours,  setBwHours,
    dialogOpen, setDialogOpen,
    plannedHoursForType,
    bookedHoursForType,
    savingBw,
    deletingBwId,
    error,
    openAddDialog,
    startEditing,
    resetBwForm,
    handleSaveBookedWork,
    handleDeleteBookedWork,
  };
}
