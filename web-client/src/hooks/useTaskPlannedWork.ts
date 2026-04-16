import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { createPlannedWork } from '../api/taskApi';
import type { TaskPlannedWorkResponse, WorkType } from '../api/types';
import { getApiErrorMessage } from '../utils/apiError';

/** Manages planned work list, add-form state, and save handler for a task. */
export function useTaskPlannedWork(taskId: string | undefined, initialData: TaskPlannedWorkResponse[]) {
  const { t } = useTranslation();

  const [plannedWork, setPlannedWork] = useState<TaskPlannedWorkResponse[]>(initialData);
  useEffect(() => { setPlannedWork(initialData); }, [initialData]);
  const [pwType,   setPwType]   = useState<WorkType>('DEVELOPMENT');
  const [pwHours,  setPwHours]  = useState(0);
  const [savingPw, setSavingPw] = useState(false);
  const [error,    setError]    = useState<string | null>(null);

  /** Creates a new planned-work entry and resets the form on success. */
  const handleSavePlannedWork = () => {
    if (!taskId) return;
    setSavingPw(true);
    createPlannedWork(taskId, { workType: pwType, plannedHours: pwHours })
      .then((saved) => {
        setPlannedWork((prev) => [...prev, saved]);
        setPwType('DEVELOPMENT');
        setPwHours(0);
      })
      .catch((err) => setError(getApiErrorMessage(err, t('tasks.failedSavePlannedWork'))))
      .finally(() => setSavingPw(false));
  };

  return {
    plannedWork,
    pwType,  setPwType,
    pwHours, setPwHours,
    savingPw,
    error,
    handleSavePlannedWork,
  };
}
