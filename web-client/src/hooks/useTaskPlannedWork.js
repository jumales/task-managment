import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { createPlannedWork } from '../api/taskApi';
/** Manages planned work list, add-form state, and save handler for a task. */
export function useTaskPlannedWork(taskId, initialData) {
    const { t } = useTranslation();
    const [plannedWork, setPlannedWork] = useState(initialData);
    useEffect(() => { setPlannedWork(initialData); }, [initialData]);
    const [pwUserId, setPwUserId] = useState(null);
    const [pwType, setPwType] = useState('DEVELOPMENT');
    const [pwHours, setPwHours] = useState(0);
    const [savingPw, setSavingPw] = useState(false);
    const [error, setError] = useState(null);
    /** Creates a new planned-work entry and resets the form on success. */
    const handleSavePlannedWork = () => {
        if (!taskId || !pwUserId)
            return;
        setSavingPw(true);
        createPlannedWork(taskId, { userId: pwUserId, workType: pwType, plannedHours: pwHours })
            .then((saved) => {
            setPlannedWork((prev) => [...prev, saved]);
            setPwUserId(null);
            setPwType('DEVELOPMENT');
            setPwHours(0);
        })
            .catch((err) => setError(err?.response?.data?.message
            ?? t('tasks.failedSavePlannedWork')))
            .finally(() => setSavingPw(false));
    };
    return {
        plannedWork,
        pwUserId, setPwUserId,
        pwType, setPwType,
        pwHours, setPwHours,
        savingPw,
        error,
        handleSavePlannedWork,
    };
}
