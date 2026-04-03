import { useEffect, useState } from 'react';
import { getTask, getTimelines, getPlannedWork, getBookedWork, getParticipants, getTaskComments } from '../api/taskApi';
import { getUsers } from '../api/userApi';
/**
 * Fetches the task and all tab datasets in a single parallel Promise.all call,
 * eliminating the sequential waterfall caused by each tab hook fetching independently.
 */
export function useTaskDetailData(taskId) {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    useEffect(() => {
        if (!taskId)
            return;
        setLoading(true);
        setError(null);
        Promise.all([
            getTask(taskId),
            getUsers(),
            getTimelines(taskId),
            getPlannedWork(taskId),
            getBookedWork(taskId),
            getParticipants(taskId),
            getTaskComments(taskId),
        ])
            .then(([task, usersPage, timelines, plannedWork, bookedWork, participants, comments]) => {
            setData({
                task,
                users: usersPage.content,
                timelines,
                plannedWork,
                bookedWork,
                participants,
                comments,
            });
        })
            .catch((err) => {
            setError(err?.message ?? 'Failed to load task');
        })
            .finally(() => setLoading(false));
    }, [taskId]);
    return { data, loading, error };
}
