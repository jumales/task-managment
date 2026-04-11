# Plan: Real-Time Task Change Push to Frontend

## Context
Users currently see stale task data unless they manually refresh. When any task field, comment, attachment, participant, timeline entry, or work entry changes, all users currently on `TaskDetailPage` for that task should receive updated data automatically. The fix is a WebSocket push from `notification-service` (which already consumes every `TaskChangedEvent` from Kafka) → frontend hook in `TaskDetailPage` that selectively re-fetches the changed sub-resource.

The same pattern already works for reporting: `reporting-service` has a full STOMP+SockJS WebSocket that pushes `/topic/reports/{userId}` after Kafka events. We mirror that pattern for tasks using `/topic/tasks/{taskId}`.

---

## Architecture
```
task-service  ──outbox──▶  Kafka (task-changed)
                                │
                    notification-service (already consumes)
                                │
                         TaskPushService
                                │
                    STOMP /topic/tasks/{taskId}
                                │
                    API Gateway /ws/tasks/** → lb:ws://notification-service
                                │
                    Frontend: taskSocket.ts singleton
                                │
                    useTaskRealtime(taskId) hook
                                │
                    setData(...) in TaskDetailPage
```

---

## Phase 1 — Missing Kafka events (common + task-service)

### 1.1 `TaskChangeType.java` — add 4 new values
`common/src/main/java/com/demo/common/event/TaskChangeType.java`
```
TASK_UPDATED        // PUT /tasks/{id} — title, description, assignee, progress, type
PARTICIPANT_ADDED
PARTICIPANT_REMOVED
TIMELINE_CHANGED    // NOT fired for automatic timelines during phase change (already has PHASE_CHANGED)
```

### 1.2 `TaskChangedEvent.java` — add 4 factory methods
`common/src/main/java/com/demo/common/event/TaskChangedEvent.java`

Add static factory methods following the existing pattern:
- `taskUpdated(taskId, assignedUserId, projectId, title)`
- `participantAdded(taskId, userId)`
- `participantRemoved(taskId, userId)`
- `timelineChanged(taskId)` — taskId only, no extra fields needed

### 1.3 `TaskPushMessage.java` — new DTO in common
`common/src/main/java/com/demo/common/event/TaskPushMessage.java`
```java
@Data @AllArgsConstructor @NoArgsConstructor
public class TaskPushMessage {
    private UUID taskId;
    private TaskChangeType changeType;
}
```

### 1.4 `TaskService.java` — add TASK_UPDATED outbox write
`task-service/.../service/TaskService.java`

In `update()`, after the existing `publishOutboxEvents(...)` call (STATUS_CHANGED is already published there), add an unconditional outbox write:
```java
outboxWriter.write(TaskChangedEvent.taskUpdated(saved.getId(), saved.getAssignedUserId(), saved.getProjectId(), saved.getTitle()));
```

### 1.5 `TaskParticipantService.java` — add PARTICIPANT outbox writes
`task-service/.../service/TaskParticipantService.java`

Inject `OutboxWriter`. After `repository.save()` in `watch()`:
```java
outboxWriter.write(TaskChangedEvent.participantAdded(taskId, userId));
```
After `repository.deleteById()` in `remove()`:
```java
outboxWriter.write(TaskChangedEvent.participantRemoved(taskId, participant.getUserId()));
```

### 1.6 `TaskTimelineService.java` — add TIMELINE_CHANGED outbox writes
`task-service/.../service/TaskTimelineService.java`

Inject `OutboxWriter`. In `setState()` after save and in `deleteState()` after delete, add:
```java
outboxWriter.write(TaskChangedEvent.timelineChanged(taskId));
```
**Do NOT add** to `setAutomaticIfAbsent` / `upsertAutomatic` — those run inside the phase-change transaction that already emits `PHASE_CHANGED`.

---

## Phase 2 — notification-service WebSocket

### 2.1 `pom.xml` — add dependency
`notification-service/pom.xml`
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

### 2.2 `WebSocketConfig.java` — new
`notification-service/.../config/WebSocketConfig.java`

Mirror of `reporting-service/.../config/WebSocketConfig.java` with endpoint changed to `/ws/tasks`:
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/tasks").setAllowedOriginPatterns("*").withSockJS();
    }
}
```

### 2.3 `WebSocketSecurityConfig.java` — new
`notification-service/.../config/WebSocketSecurityConfig.java`

Exact copy of `reporting-service/.../config/WebSocketSecurityConfig.java`. Uses `JwtDecoder` already on classpath.

### 2.4 `TaskPushService.java` — new
`notification-service/.../service/TaskPushService.java`
```java
@Service
public class TaskPushService {
    private static final String TOPIC_PREFIX = "/topic/tasks/";
    private final SimpMessagingTemplate messagingTemplate;

    /** Pushes a task change signal to all STOMP clients subscribed to /topic/tasks/{taskId}. */
    public void push(UUID taskId, TaskChangeType changeType) {
        if (taskId == null) return;
        messagingTemplate.convertAndSend(TOPIC_PREFIX + taskId, new TaskPushMessage(taskId, changeType));
    }
}
```

### 2.5 `TaskEventNotificationConsumer.java` — wire in push
`notification-service/.../consumer/TaskEventNotificationConsumer.java`

Inject `TaskPushService`. After `notificationService.notify(event)`:
```java
taskPushService.push(event.getTaskId(), event.getChangeType());
```
`notify()` is `@Async` so this executes immediately in the consumer thread — no blocking risk.

### 2.6 `NotificationService.java` — add switch cases
`notification-service/.../service/NotificationService.java`

In `buildDefaultContent()` switch, add cases for the 4 new `TaskChangeType` values. In `resolveRecipientId()`, the default `assignedUserId` branch already covers all new types — no change needed.

---

## Phase 3 — API Gateway

### 3.1 `application.yml` — add 2 routes
`api-gateway/src/main/resources/application.yml`

Add **before** the existing `reporting-service-ws` route:
```yaml
- id: notification-service-ws
  uri: lb:ws://notification-service
  predicates:
    - Path=/ws/tasks/**
  filters:
    - DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_FIRST

- id: notification-service-notifications
  uri: lb://notification-service
  predicates:
    - Path=/api/v1/notifications/**
```

### 3.2 `SecurityConfig.java` — no change needed
`api-gateway/.../config/SecurityConfig.java`

The existing `.pathMatchers("/ws/**").permitAll()` already covers `/ws/tasks/**`.

---

## Phase 4 — Frontend

### 4.1 `types.ts` — extend `TaskChangeType` + add `TaskPushMessage`
`web-client/src/api/types.ts`

Add 4 new values to `TaskChangeType` union type, and add:
```typescript
export interface TaskPushMessage {
  taskId: string;
  changeType: TaskChangeType;
}
```

### 4.2 `taskSocket.ts` — new singleton
`web-client/src/realtime/taskSocket.ts`

Mirror of `reportingSocket.ts`. Key differences:
- Connects to `${VITE_API_URL}/ws/tasks`
- `subscribe(taskId, listener)` not `subscribe(userId, listener)`
- Manages per-taskId STOMP subscriptions over one connection
- Disconnects when last subscription removed

```typescript
class TaskSocket {
  private client: Client | null = null;
  private listeners = new Map<string, Set<(msg: TaskPushMessage) => void>>();
  private stompSubs = new Map<string, { unsubscribe: () => void }>();

  subscribe(taskId: string, onPush: (msg: TaskPushMessage) => void): () => void { ... }
  private ensureConnected() { ... }
  private subscribeToTopic(taskId: string) { ... }
  private disconnect() { ... }
}
export const taskSocket = new TaskSocket();
```

### 4.3 `useTaskRealtime.ts` — new hook
`web-client/src/hooks/useTaskRealtime.ts`

Subscribes to `taskSocket` and maps `changeType` → targeted re-fetch:

| changeType | Re-fetch | setData call |
|---|---|---|
| TASK_CREATED, TASK_UPDATED, STATUS_CHANGED, PHASE_CHANGED | `getTask(id)` | `{...prev, task: updated}` |
| COMMENT_ADDED | `getTaskComments(id)` | `{...prev, comments: updated}` |
| ATTACHMENT_ADDED/DELETED | `getAttachments(id)` | `{...prev, attachments: updated}` |
| PLANNED_WORK_CREATED | `getPlannedWork(id)` | `{...prev, plannedWork: updated}` |
| BOOKED_WORK_CREATED/UPDATED/DELETED | `getBookedWork(id)` | `{...prev, bookedWork: updated}` |
| PARTICIPANT_ADDED/REMOVED | `getParticipants(id)` | `{...prev, participants: updated}` |
| TIMELINE_CHANGED | `getTimelines(id)` | `{...prev, timelines: updated}` |

### 4.4 `TaskDetailPage.tsx` — wire hook
`web-client/src/pages/TaskDetailPage.tsx`

After the existing hook declarations (around line 76), add:
```typescript
useTaskRealtime(id, {
  onTaskUpdated:        (t)  => setData(prev => prev ? { ...prev, task: t }         : null),
  onTimelinesUpdated:   (ts) => setData(prev => prev ? { ...prev, timelines: ts }    : null),
  onPlannedUpdated:     (pw) => setData(prev => prev ? { ...prev, plannedWork: pw }  : null),
  onBookedUpdated:      (bw) => setData(prev => prev ? { ...prev, bookedWork: bw }   : null),
  onParticipantsUpdated:(ps) => setData(prev => prev ? { ...prev, participants: ps } : null),
  onCommentsUpdated:    (cs) => setData(prev => prev ? { ...prev, comments: cs }     : null),
  onAttachmentsUpdated: (at) => setData(prev => prev ? { ...prev, attachments: at }  : null),
});
```
All child hooks sync from their `useMemo(() => data?.xxx ?? [], [data])` refs — no changes to individual tab hooks needed.

---

## Implementation Order
1. `common` — `TaskChangeType` + `TaskPushMessage` + factory methods in `TaskChangedEvent`
2. `task-service` — outbox writes in `TaskService`, `TaskParticipantService`, `TaskTimelineService`
3. `notification-service` — pom, `WebSocketConfig`, `WebSocketSecurityConfig`, `TaskPushService`, wire consumer + switch cases
4. `api-gateway` — new routes in `application.yml`
5. Frontend — `types.ts`, `taskSocket.ts`, `useTaskRealtime.ts`, `TaskDetailPage.tsx`

---

## Verification
1. Run `mvn clean install -DskipTests=true` from root — confirms no compilation errors
2. `scripts/start-dev.sh` — spin up full stack
3. Open two browser tabs on the same task's detail page
4. In tab 1: add a comment → tab 2 comments section updates automatically
5. In tab 1: change task status → tab 2 status tag updates automatically
6. In tab 1: add a watcher → tab 2 participants tab updates automatically
7. Check CI: `gh run watch` after push

### Integration test additions
- `notification-service`: Add `StompClient` test in `NotificationConsumerIT` — publish `TaskChangedEvent` via `KafkaTemplate`, assert STOMP frame arrives on `/topic/tasks/{taskId}` within 10s using `Awaitility`
- `task-service`: Assert new `TASK_UPDATED` outbox event in `TaskControllerIT` for PUT requests; `PARTICIPANT_ADDED/REMOVED` in `TaskParticipantControllerIT`; `TIMELINE_CHANGED` in `TaskTimelineControllerIT`
