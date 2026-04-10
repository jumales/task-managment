# Plan: Tab Pagination + Form-on-Top

## Context
The five tab sections on the task detail page (Comments, Attachments, Planned Work, Booked Work, Participants) load all items at once with no pagination, and the "add" form sits below the list. When lists grow large this makes the UX poor. This plan adds client-side pagination (max 5 items per page) and moves the add/edit form to the top of each tab so it is always visible without scrolling past items.

---

## Approach

All data is already loaded client-side via hooks — no backend changes needed.  
Ant Design's `<List>` has a built-in `pagination` prop that handles slicing and page controls automatically. This eliminates any extra state management.

```tsx
<List
  pagination={{ pageSize: 5, hideOnSinglePage: true, size: 'small' }}
  dataSource={items}
  renderItem={...}
/>
```

Each component is refactored to:
1. Render the add/edit form **first** (top)
2. A `<Divider>` separator
3. The `<List>` with `pagination` prop

---

## Per-Component Changes

### `TaskCommentsTab.tsx`
**Move to top:** TextArea + Add Comment button (hidden when `finished`)  
**List pagination:** `pagination={{ pageSize: 5, hideOnSinglePage: true, size: 'small' }}`

```
[TextArea + Add button]   ← top (hidden when finished)
[Divider]
[List — paginated]
```

---

### `TaskAttachmentsTab.tsx`
**Move to top:** the hidden file `<input>` + Upload button  
**List pagination:** same config

```
[Upload button + hidden file input]   ← top
[Divider]
[List — paginated]
```

---

### `TaskPlannedWorkTab.tsx`
**Move to top:** Select (work type) + InputNumber (hours) + Add button — conditioned on `taskPhaseName === 'PLANNING'`  
**List pagination:** same config

```
[Form — visible in PLANNING phase only]   ← top
[Divider — visible in PLANNING phase only]
[List — paginated]
```

---

### `TaskBookedWorkTab.tsx`
**Move to top:** User select + Work type select + InputNumber + Save/Cancel buttons — conditioned on `taskPhaseName !== 'PLANNING' && !finished`  
**Editing state indicator:** Divider label switches between "Add Booked Work" and "Edit Booked Work" (already uses `editingBw` flag — keep that logic)  
**List pagination:** same config  
**List item actions (edit/delete):** hidden when `finished` — unchanged

```
[Form — hidden in PLANNING or finished]   ← top
[Divider — hidden in PLANNING or finished]
[List — paginated, actions hidden when finished]
```

---

### `TaskParticipantsTab.tsx`
**Move to top:** User select + Role select + Add Participant button  
**List pagination:** same config

```
[User select + Role select + Add button]   ← top
[Divider]
[List — paginated]
```

---

## Files to Modify

| File | Change |
|---|---|
| `web-client/src/components/taskDetail/TaskCommentsTab.tsx` | Form to top, add List pagination |
| `web-client/src/components/taskDetail/TaskAttachmentsTab.tsx` | Form to top, add List pagination |
| `web-client/src/components/taskDetail/TaskPlannedWorkTab.tsx` | Form to top, add List pagination |
| `web-client/src/components/taskDetail/TaskBookedWorkTab.tsx` | Form to top, add List pagination |
| `web-client/src/components/taskDetail/TaskParticipantsTab.tsx` | Form to top, add List pagination |

No hook changes. No backend changes. No new files.

---

## After Implementation

Run integration tests locally:
```bash
cd /Users/admin/projects/cc/task-managment
mvn test -pl task-service -Dtest="TaskControllerIT,TaskBookedWorkControllerIT" -am
```

All 63 existing tests should pass — no backend logic was touched.
