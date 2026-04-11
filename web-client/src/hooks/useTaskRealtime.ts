import { useEffect } from 'react';
import { taskSocket } from '../realtime/taskSocket';
import {
  getTask,
  getTaskComments,
  getParticipants,
  getTimelines,
  getPlannedWork,
  getBookedWork,
  getAttachments,
} from '../api/taskApi';
import type {
  TaskResponse,
  TaskCommentResponse,
  TaskParticipantResponse,
  TaskTimelineResponse,
  TaskPlannedWorkResponse,
  TaskBookedWorkResponse,
  TaskAttachmentResponse,
} from '../api/types';

export interface TaskRealtimeCallbacks {
  onTaskUpdated:        (task:         TaskResponse)               => void;
  onCommentsUpdated:    (comments:     TaskCommentResponse[])      => void;
  onParticipantsUpdated:(participants: TaskParticipantResponse[])  => void;
  onTimelinesUpdated:   (timelines:    TaskTimelineResponse[])     => void;
  onPlannedUpdated:     (plannedWork:  TaskPlannedWorkResponse[])  => void;
  onBookedUpdated:      (bookedWork:   TaskBookedWorkResponse[])   => void;
  onAttachmentsUpdated: (attachments:  TaskAttachmentResponse[])   => void;
}

/**
 * Subscribes to WebSocket push notifications for a task and selectively re-fetches
 * the changed sub-resource, calling the appropriate callback with the fresh data.
 * Cleans up the STOMP subscription on unmount.
 *
 * @param taskId   the task to watch — no-op when undefined
 * @param callbacks  one callback per logical sub-resource that can change in real time
 */
export function useTaskRealtime(
  taskId: string | undefined,
  callbacks: TaskRealtimeCallbacks,
): void {
  useEffect(() => {
    if (!taskId) return;

    const unsubscribe = taskSocket.subscribe(taskId, (msg) => {
      switch (msg.changeType) {
        case 'TASK_CREATED':
        case 'TASK_UPDATED':
        case 'STATUS_CHANGED':
          getTask(taskId).then(callbacks.onTaskUpdated).catch(() => {});
          break;
        case 'PHASE_CHANGED':
          // Phase change auto-sets REAL_START / REAL_END / RELEASE_DATE, so re-fetch both
          getTask(taskId).then(callbacks.onTaskUpdated).catch(() => {});
          getTimelines(taskId).then(callbacks.onTimelinesUpdated).catch(() => {});
          break;
        case 'COMMENT_ADDED':
          getTaskComments(taskId).then(callbacks.onCommentsUpdated).catch(() => {});
          break;
        case 'ATTACHMENT_ADDED':
        case 'ATTACHMENT_DELETED':
          getAttachments(taskId).then(callbacks.onAttachmentsUpdated).catch(() => {});
          break;
        case 'PLANNED_WORK_CREATED':
          getPlannedWork(taskId).then(callbacks.onPlannedUpdated).catch(() => {});
          break;
        case 'BOOKED_WORK_CREATED':
        case 'BOOKED_WORK_UPDATED':
        case 'BOOKED_WORK_DELETED':
          getBookedWork(taskId).then(callbacks.onBookedUpdated).catch(() => {});
          break;
        case 'PARTICIPANT_ADDED':
        case 'PARTICIPANT_REMOVED':
          getParticipants(taskId).then(callbacks.onParticipantsUpdated).catch(() => {});
          break;
        case 'TIMELINE_CHANGED':
          getTimelines(taskId).then(callbacks.onTimelinesUpdated).catch(() => {});
          break;
      }
    });

    return unsubscribe;
  }, [taskId]); // eslint-disable-line react-hooks/exhaustive-deps
}
