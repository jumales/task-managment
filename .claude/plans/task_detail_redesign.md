# Plan: Task Detail Page Layout Redesign

## Context
The current task detail page is a single-column layout: a full-width overview card stacked above a full-width tab panel. The redesign promotes task code + title to a prominent top header and splits the body into two columns — left for metadata and timeline, right for collaborative tabs (comments, work logs, participants) — making better use of horizontal space.

---

## Target Layout

```
[← Back to Tasks]

[TASK-42]  Task Title Here          [TODO] [BUG_FIXING] [Dev Finished] [✎ Edit]

┌─────────────────────────┬────────────────────────────────────────┐
│  LEFT col (xs=24 md=10) │  RIGHT col (xs=24 md=14)              │
│                         │                                        │
│  Phase: DONE  [Change]  │  Tabs:                                 │
│  Project: XYZ           │    Comments + Attachments (default)    │
│  Assignee: John         │    Planned Work                        │
│  Progress: ████░ 60%    │    Booked Work                         │
│                         │    Participants                         │
│  Description:           │                                        │
│  Lorem ipsum...         │                                        │
│                         │                                        │
│  ──── Timeline ────     │                                        │
│  [Planned Start][Pl.End]│                                        │
│  [Real Start][Real End] │                                        │
└─────────────────────────┴────────────────────────────────────────┘
```

---

## Implementation

### 1. `web-client/src/pages/TaskDetailPage.tsx`

**Top header (full-width, above columns):**
- `task.taskCode` as `<Typography.Text type="secondary">` (rendered only when not null)
- `task.title` as `<Typography.Title level={3}>`
- Status tag using `STATUS_COLORS[task.status]` from `taskDetailConstants.ts`
- Type tag using `TYPE_COLORS[task.type]` and `typeLabels[task.type]` (add `useMemo(() => getTypeLabels(t), [t])` to page)
- Finished / Dev Finished badge (same logic as currently in `TaskOverviewCard`)
- All tags and title in a `<Space wrap>` with `justifyContent: space-between`

**Body: Ant Design `Row` / `Col`:**
```jsx
<Row gutter={[24, 24]}>
  <Col xs={24} md={10}>
    <TaskOverviewCard task={data.task} users={data.users} onSave={handleOverviewSave} saving={saving} onChangePhase={phaseChange.openModal} />
    <Divider orientation="left">{t('tasks.timeline')}</Divider>
    <TaskTimelineTab {...timeline} users={data.users} taskPhaseName={data.task.phase.name} />
  </Col>
  <Col xs={24} md={14}>
    <Tabs items={[comments+attachments, plannedwork, bookedwork, participants]} />
  </Col>
</Row>
```

**Remove timeline from `<Tabs>` items** — it is now rendered inline in the left column.

Add imports: `Row`, `Col`, `Divider` from `antd`; `STATUS_COLORS`, `TYPE_COLORS`, `getTypeLabels` from `taskDetailConstants`.

---

### 2. `web-client/src/components/taskDetail/TaskOverviewCard.tsx`

**Remove:**
- The top `<div>` that renders title display/input, status tag, type tag, finished badges, and edit button — these all move to the page header
- The outer `<Card>` wrapper — render as plain `<div>`

**Keep and adjust:**
- All internal state: `editing`, `title`, `description`, `assignedUserId`, `progress`
- `startEditing` / `cancelEditing` / `handleSave` — logic unchanged
- Edit button (`<Button icon={<EditOutlined />}>`) — render as a small button at top of metadata block, hidden when `fieldsLocked`
- When `editing === true`, render title `<Input>` as the first editable field
- `<Descriptions>` rows: phase (+Change Phase btn), project, assignee, description, progress — unchanged
- Save / Cancel buttons at bottom when editing — unchanged

**Props interface: unchanged** — `task`, `users`, `onSave`, `saving`, `onChangePhase`

---

## Files to Modify

| File | Change |
|---|---|
| `web-client/src/pages/TaskDetailPage.tsx` | Add top header; two-column `Row`/`Col`; `TaskTimelineTab` in left col; remove timeline tab |
| `web-client/src/components/taskDetail/TaskOverviewCard.tsx` | Remove Card wrapper, title display, and status/type tags from top |

---

## Verification

1. Open any task detail — task code + title appear in a prominent header row with status/type tags
2. Left column shows phase, project, assignee, progress, description, and timeline cards below a divider
3. Right column shows tabs: Comments + Attachments (default active), Planned Work, Booked Work, Participants — no Timeline tab
4. Click Edit — title input appears in left panel; description/assignee/progress become editable; Save persists changes
5. On a narrow viewport (< `md` breakpoint ~768 px) both columns stack vertically
6. Change Phase modal still opens from left panel phase row
7. Finished/locked state hides edit button; RELEASED/REJECTED hides Change Phase button; comment and booked-work forms disabled correctly
