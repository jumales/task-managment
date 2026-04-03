import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import dayjs from 'dayjs';
import { setTimeline, deleteTimeline } from '../api/taskApi';
/** Manages timeline state, modal, and CRUD handlers for a task's timeline entries. */
export function useTaskTimeline(taskId, initialData) {
    const { t } = useTranslation();
    const [timelines, setTimelines] = useState(initialData);
    const [tlModalOpen, setTlModalOpen] = useState(false);
    const [editingState, setEditingState] = useState(null);
    const [tlUserId, setTlUserId] = useState(null);
    const [tlTimestamp, setTlTimestamp] = useState(null);
    const [savingTimeline, setSavingTimeline] = useState(false);
    const [deletingTlState, setDeletingTlState] = useState(null);
    const [error, setError] = useState(null);
    /** Opens the set/edit modal pre-populated with any existing entry for the given state. */
    const openTlModal = (state) => {
        const existing = timelines.find((tl) => tl.state === state);
        setEditingState(state);
        setTlUserId(existing?.setByUserId ?? null);
        setTlTimestamp(existing ? dayjs(existing.timestamp) : null);
        setTlModalOpen(true);
    };
    /** Persists the timeline entry being edited and updates local state on success. */
    const handleSaveTimeline = () => {
        if (!taskId || !editingState || !tlUserId || !tlTimestamp)
            return;
        setSavingTimeline(true);
        setTimeline(taskId, editingState, { setByUserId: tlUserId, timestamp: tlTimestamp.toISOString() })
            .then((saved) => {
            setTimelines((prev) => {
                const idx = prev.findIndex((tl) => tl.state === saved.state);
                return idx >= 0
                    ? prev.map((tl, i) => (i === idx ? saved : tl))
                    : [...prev, saved];
            });
            setTlModalOpen(false);
        })
            .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedSaveTimeline')))
            .finally(() => setSavingTimeline(false));
    };
    /** Removes a timeline entry by state and updates local state on success. */
    const handleDeleteTimeline = (state) => {
        if (!taskId)
            return;
        setDeletingTlState(state);
        deleteTimeline(taskId, state)
            .then(() => setTimelines((prev) => prev.filter((tl) => tl.state !== state)))
            .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedDeleteTimeline')))
            .finally(() => setDeletingTlState(null));
    };
    return {
        timelines,
        tlModalOpen, setTlModalOpen,
        editingState,
        tlUserId, setTlUserId,
        tlTimestamp, setTlTimestamp,
        savingTimeline,
        deletingTlState,
        error,
        openTlModal,
        handleSaveTimeline,
        handleDeleteTimeline,
    };
}
