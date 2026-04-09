import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import keycloak from '../auth/keycloak';

type Listener = () => void;

/**
 * Singleton STOMP client that connects to the reporting-service WebSocket endpoint.
 * Subscribers register a callback that fires when the server pushes a REPORT_UPDATED message
 * for the current user. The connection is established lazily on the first subscription.
 */
class ReportingSocket {
  private client: Client | null = null;
  private listeners = new Set<Listener>();
  private userId: string | null = null;

  /** Subscribe to report-update notifications. Returns an unsubscribe function. */
  subscribe(userId: string, onUpdate: Listener): () => void {
    this.listeners.add(onUpdate);
    this.connect(userId);
    return () => {
      this.listeners.delete(onUpdate);
      if (this.listeners.size === 0) this.disconnect();
    };
  }

  private connect(userId: string) {
    if (this.client?.active) return;
    this.userId = userId;

    this.client = new Client({
      webSocketFactory: () =>
        new SockJS(`${import.meta.env.VITE_API_URL}/ws`),
      connectHeaders: {
        Authorization: `Bearer ${keycloak.token ?? ''}`,
      },
      reconnectDelay: 5000,
      onConnect: () => {
        if (!this.userId) return;
        this.client!.subscribe(`/topic/reports/${this.userId}`, () => {
          this.listeners.forEach((fn) => fn());
        });
      },
    });

    this.client.activate();
  }

  private disconnect() {
    this.client?.deactivate();
    this.client = null;
  }
}

export const reportingSocket = new ReportingSocket();
