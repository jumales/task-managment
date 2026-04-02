import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { createPlannedWork } from '../api/taskApi';
import type { TaskPlannedWorkResponse, WorkType } from '../api/types';

/** Manages planned work list, add-form state, and save handler for a task. */
export function useTaskPlannedWork(taskId: string | undefined, initialData: TaskPlannedWorkResponse[]) {
  const { t } = useTranslation();

  const [plannedWork, setPlannedWork] = useState<TaskPlannedWorkResponse[]>(initialData);
  const [pwUserId,    setPwUserId]    = useState<string | null>(null);
  const [pwType,      setPwType]      = useState<WorkType>('DEVELOPMENT');
  const [pwHours,     setPwHours]     = useState(0);
  const [savingPw,    setSavingPw]    = useState(false);
  const [error,       setError]       = useState<string | null>(null);

  /** Creates a new planned-work entry and resets the form on success. */
  const handleSavePlannedWork = () => {
    if (!taskId || !pwUserId) return;
    setSavingPw(true);
    createPlannedWork(taskId, { userId: pwUserId, workType: pwType, plannedHours: pwHours })
      .then((saved) => {
        setPlannedWork((prev) => [...prev, saved]);
        setPwUserId(null);
        setPwType('DEVELOPMENT');
        setPwHours(0);
      })
      .catch((err: unknown) =>
        setError(
          (err as { response?: { data?: { message?: string } } })?.response?.data?.message
            ?? t('tasks.failedSavePlannedWork'),
        ),
      )
      .finally(() => setSavingPw(false));
  };

  return {
    plannedWork,
    pwUserId, setPwUserId,
    pwType,   setPwType,
    pwHours,  setPwHours,
    savingPw,
    error,
    handleSavePlannedWork,
  };
}
