import { useEffect, useState } from 'react';
import { getTask, getTimelines, getPlannedWork, getBookedWork, getParticipants, getTaskComments } from '../api/taskApi';
import { getUsers } from '../api/userApi';
import type {
  TaskResponse,
  UserResponse,
  TaskTimelineResponse,
  TaskPlannedWorkResponse,
  TaskBookedWorkResponse,
  TaskParticipantResponse,
  TaskCommentResponse,
} from '../api/types';

export interface TaskDetailData {
  task:         TaskResponse;
  users:        UserResponse[];
  timelines:    TaskTimelineResponse[];
  plannedWork:  TaskPlannedWorkResponse[];
  bookedWork:   TaskBookedWorkResponse[];
  participants: TaskParticipantResponse[];
  comments:     TaskCommentResponse[];
}

/**
 * Fetches the task and all tab datasets in a single parallel Promise.all call,
 * eliminating the sequential waterfall caused by each tab hook fetching independently.
 */
export function useTaskDetailData(taskId: string | undefined) {
  const [data,    setData]    = useState<TaskDetailData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error,   setError]   = useState<string | null>(null);

  useEffect(() => {
    if (!taskId) return;
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
          users:        usersPage.content,
          timelines,
          plannedWork,
          bookedWork,
          participants,
          comments,
        });
      })
      .catch((err: unknown) => {
        setError((err as { message?: string })?.message ?? 'Failed to load task');
      })
      .finally(() => setLoading(false));
  }, [taskId]);

  return { data, loading, error };
}
