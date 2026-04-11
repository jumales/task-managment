import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import keycloak from '../auth/keycloak';
import type { TaskPushMessage } from '../api/types';

type PushListener = (msg: TaskPushMessage) => void;

/**
 * Singleton STOMP client that connects to the notification-service WebSocket endpoint.
 * Manages per-taskId STOMP subscriptions over a single shared connection.
 * The connection is established lazily on the first subscription and torn down
 * when the last subscription is removed.
 */
class TaskSocket {
  private client: Client | null = null;
  private listeners = new Map<string, Set<PushListener>>();
  private stompSubs = new Map<string, { unsubscribe: () => void }>();

  /**
   * Subscribe to push notifications for the given task.
   * Returns an unsubscribe function that cleans up the listener and the STOMP
   * subscription when this is the last listener for the task.
   */
  subscribe(taskId: string, onPush: PushListener): () => void {
    if (!this.listeners.has(taskId)) {
      this.listeners.set(taskId, new Set());
    }
    this.listeners.get(taskId)!.add(onPush);
    this.ensureConnected(taskId);

    return () => {
      const set = this.listeners.get(taskId);
      if (set) {
        set.delete(onPush);
        if (set.size === 0) {
          this.listeners.delete(taskId);
          this.stompSubs.get(taskId)?.unsubscribe();
          this.stompSubs.delete(taskId);
        }
      }
      if (this.listeners.size === 0) this.disconnect();
    };
  }

  private ensureConnected(taskId: string) {
    if (this.client?.active) {
      // Already connected — subscribe to the topic immediately if not yet subscribed
      if (!this.stompSubs.has(taskId)) {
        this.subscribeToTopic(taskId);
      }
      return;
    }

    this.client = new Client({
      webSocketFactory: () =>
        new SockJS(`${import.meta.env.VITE_API_URL}/ws/tasks`),
      connectHeaders: {
        Authorization: `Bearer ${keycloak.token ?? ''}`,
      },
      reconnectDelay: 5000,
      onConnect: () => {
        // Subscribe to all currently-registered task IDs on (re)connect
        for (const id of this.listeners.keys()) {
          if (!this.stompSubs.has(id)) {
            this.subscribeToTopic(id);
          }
        }
      },
    });

    this.client.activate();
  }

  private subscribeToTopic(taskId: string) {
    if (!this.client) return;
    const sub = this.client.subscribe(`/topic/tasks/${taskId}`, (frame) => {
      const msg: TaskPushMessage = JSON.parse(frame.body);
      this.listeners.get(taskId)?.forEach((fn) => fn(msg));
    });
    this.stompSubs.set(taskId, sub);
  }

  private disconnect() {
    this.client?.deactivate();
    this.client = null;
    this.stompSubs.clear();
  }
}

export const taskSocket = new TaskSocket();
