import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getPhases, updateTask } from '../api/taskApi';
import type { TaskResponse, TaskPhaseResponse } from '../api/types';

/** Manages the phase-change modal: fetches project phases, tracks selection, and persists the update. */
export function useTaskPhaseChange(
  taskId: string | undefined,
  task: TaskResponse | null,
  onTaskUpdated: (task: TaskResponse) => void,
) {
  const { t } = useTranslation();

  const [open,            setOpen]            = useState(false);
  const [phases,          setPhases]          = useState<TaskPhaseResponse[]>([]);
  const [loadingPhases,   setLoadingPhases]   = useState(false);
  const [selectedPhaseId, setSelectedPhaseId] = useState<string | null>(null);
  const [saving,          setSaving]          = useState(false);
  const [error,           setError]           = useState<string | null>(null);

  /** Opens the modal and loads available phases for the task's project. */
  const openModal = () => {
    if (!task) return;
    setSelectedPhaseId(task.phase?.id ?? null);
    setError(null);
    setOpen(true);
    setLoadingPhases(true);
    getPhases(task.project.id)
      .then(setPhases)
      .catch(() => setError(t('tasks.failedLoadPhases')))
      .finally(() => setLoadingPhases(false));
  };

  /** Builds the full task request from the current task, replacing only the phase. */
  const buildRequest = (phaseId: string) => {
    const assignee = task!.participants.find((p) => p.role === 'ASSIGNEE');
    return {
      title:          task!.title,
      description:    task!.description,
      status:         task!.status,
      type:           task!.type,
      progress:       task!.progress,
      assignedUserId: assignee?.userId ?? '',
      projectId:      task!.project.id,
      phaseId,
    };
  };

  /** Persists the selected phase and notifies the parent on success. */
  const handleSave = () => {
    if (!taskId || !task || !selectedPhaseId) return;
    setSaving(true);
    updateTask(taskId, buildRequest(selectedPhaseId))
      .then((updated) => {
        onTaskUpdated(updated);
        setOpen(false);
      })
      .catch((err: { response?: { data?: { message?: string } } }) =>
        setError(err?.response?.data?.message ?? t('tasks.failedSavePhase')),
      )
      .finally(() => setSaving(false));
  };

  return {
    open,            setOpen,
    phases,
    loadingPhases,
    selectedPhaseId, setSelectedPhaseId,
    saving,
    error,
    openModal,
    handleSave,
  };
}
