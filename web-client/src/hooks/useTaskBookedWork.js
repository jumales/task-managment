import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { createBookedWork, updateBookedWork, deleteBookedWork } from '../api/taskApi';
/** Manages booked work list, add/edit-form state, and CRUD handlers for a task. */
export function useTaskBookedWork(taskId, initialData) {
    const { t } = useTranslation();
    const [bookedWork, setBookedWork] = useState(initialData);
    useEffect(() => { setBookedWork(initialData); }, [initialData]);
    const [editingBw, setEditingBw] = useState(null);
    const [bwUserId, setBwUserId] = useState(null);
    const [bwType, setBwType] = useState('DEVELOPMENT');
    const [bwHours, setBwHours] = useState(0);
    const [savingBw, setSavingBw] = useState(false);
    const [deletingBwId, setDeletingBwId] = useState(null);
    const [error, setError] = useState(null);
    /** Populates the edit form with an existing booked-work entry. */
    const startEditing = (bw) => {
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
        if (!taskId || !bwUserId)
            return;
        setSavingBw(true);
        const request = { userId: bwUserId, workType: bwType, bookedHours: bwHours };
        const apiCall = editingBw
            ? updateBookedWork(taskId, editingBw.id, request)
            : createBookedWork(taskId, request);
        apiCall
            .then((saved) => {
            setBookedWork((prev) => editingBw ? prev.map((b) => (b.id === saved.id ? saved : b)) : [...prev, saved]);
            resetBwForm();
        })
            .catch((err) => setError(err?.response?.data?.message
            ?? t('tasks.failedSaveBookedWork')))
            .finally(() => setSavingBw(false));
    };
    /** Deletes a booked-work entry by id and removes it from local state on success. */
    const handleDeleteBookedWork = (bookedWorkId) => {
        if (!taskId)
            return;
        setDeletingBwId(bookedWorkId);
        deleteBookedWork(taskId, bookedWorkId)
            .then(() => setBookedWork((prev) => prev.filter((b) => b.id !== bookedWorkId)))
            .catch((err) => setError(err?.response?.data?.message
            ?? t('tasks.failedDeleteBookedWork')))
            .finally(() => setDeletingBwId(null));
    };
    return {
        bookedWork,
        editingBw,
        bwUserId, setBwUserId,
        bwType, setBwType,
        bwHours, setBwHours,
        savingBw,
        deletingBwId,
        error,
        startEditing,
        resetBwForm,
        handleSaveBookedWork,
        handleDeleteBookedWork,
    };
}
