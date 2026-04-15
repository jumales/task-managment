# Plan: Replace Table/List Action Buttons with Icon-Only Buttons

## Context

Action buttons in tables and lists (Edit, Delete, View, Download, etc.) currently show text labels, making rows visually heavy. The goal is to replace these with compact icon-only buttons wrapped in `<Tooltip>` for accessibility. Modal footer buttons (Save, Cancel, Create, Next, Back) and page-header CTAs (New Task, New Project, New User) are **out of scope** — they keep their text.

None of the 9 affected files currently import `Tooltip` from antd — every file needs it added.

---

## Pattern

**Before:**
```tsx
<Button size="small" danger onClick={handleDelete}>{t('common.delete')}</Button>
```

**After:**
```tsx
<Tooltip title={t('common.delete')}>
  <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={handleDelete} />
</Tooltip>
```

For buttons inside `<Popconfirm>`, Tooltip wraps the Button *inside* the Popconfirm:
```tsx
<Popconfirm title="..." onConfirm={handleDelete}>
  <Tooltip title={t('common.delete')}>
    <Button size="small" type="text" danger icon={<DeleteOutlined />} />
  </Tooltip>
</Popconfirm>
```

Non-destructive actions use `type="text"`. Destructive actions use `type="text" danger`.
Primary inline row saves (phase label save) keep `type="primary"` but go icon-only.

---

## Files & Changes

### 1. `web-client/src/pages/TasksPage.tsx`
Add imports: `Tooltip` from antd; `EyeOutlined` from @ant-design/icons

| Button | Icon | Type |
|---|---|---|
| View task | `EyeOutlined` | `text` |
| Delete task | `DeleteOutlined` | `text danger` |

### 2. `web-client/src/pages/ProjectsPage.tsx`
Add imports: `Tooltip` from antd; `ApartmentOutlined, CheckOutlined, StarOutlined, StarFilled` from @ant-design/icons

| Button | Icon | Type |
|---|---|---|
| Manage Phases (column) | `ApartmentOutlined` | `text` |
| Save phase label (inline row) | `CheckOutlined` | `primary` |
| Set as Default (inline row) | `StarOutlined` (not default) / `StarFilled` (default) | `text` (not default) / `primary` (default) |
| Delete project | `DeleteOutlined` | `text danger` |

### 3. `web-client/src/pages/UsersPage.tsx`
Add imports: `Tooltip` from antd; `EditOutlined` from @ant-design/icons

| Button | Icon | Type |
|---|---|---|
| Upload Avatar | `UploadOutlined` (already imported) — remove text | `default` (keep, has loading state) |
| Edit User | `EditOutlined` | `text` |

### 4. `web-client/src/components/taskDetail/TaskAttachmentsTab.tsx`
Add imports: `Tooltip` from antd (icons already imported)

| Button | Icon | Type |
|---|---|---|
| Download | `DownloadOutlined` (already imported) — remove text | `text` |
| Delete Attachment | `DeleteOutlined` (already imported) — remove text | `text danger` |

### 5. `web-client/src/components/taskDetail/TaskBookedWorkTab.tsx`
Add imports: `Tooltip` from antd; `EditOutlined, DeleteOutlined` from @ant-design/icons

| Button | Icon | Type |
|---|---|---|
| Edit booked work | `EditOutlined` | `text` |
| Delete booked work | `DeleteOutlined` | `text danger` |

### 6. `web-client/src/components/taskDetail/TaskTimelineTab.tsx`
Add imports: `Tooltip` from antd; `EditOutlined, CloseCircleOutlined` from @ant-design/icons (`CalendarOutlined` already imported)

| Button | Condition | Icon | Type |
|---|---|---|---|
| Set Date | `!entry` | `CalendarOutlined` | `text` |
| Edit Date | `entry` exists | `EditOutlined` | `text` |
| Clear Date | in Popconfirm | `CloseCircleOutlined` | `text danger` |

### 7. `web-client/src/components/taskDetail/TaskParticipantsTab.tsx`
Add imports: `Tooltip` from antd; `BellOutlined` from @ant-design/icons

| Button | Icon | Type |
|---|---|---|
| Unwatch participant | `BellOutlined` | `text danger` |

### 8. `web-client/src/pages/ReportsPage.tsx`
Add imports: `Tooltip` from antd; `ArrowRightOutlined` from @ant-design/icons

| Button | Icon | Type |
|---|---|---|
| Open task (table row) | `ArrowRightOutlined` | `text` |

Note: "Refresh" buttons are header buttons (not table row actions) — out of scope.

---

## Icons Not Yet Imported (per file)

| File | New icons needed |
|---|---|
| TasksPage | `EyeOutlined` |
| ProjectsPage | `ApartmentOutlined, CheckOutlined, StarOutlined, StarFilled` |
| UsersPage | `EditOutlined` |
| TaskAttachmentsTab | none (already has all icons) |
| TaskBookedWorkTab | `EditOutlined, DeleteOutlined` |
| TaskTimelineTab | `EditOutlined, CloseCircleOutlined` |
| TaskParticipantsTab | `BellOutlined` |
| ReportsPage | `ArrowRightOutlined` |

---

## Status

**Implemented** — all 8 files updated. TypeScript clean (one pre-existing unrelated error in `useTaskRealtime.ts`).

## Verification

1. Start dev server: `cd web-client && npm run dev`
2. **Tasks page** — table rows show 👁 and 🗑 icons; hover shows tooltip; delete still triggers Popconfirm
3. **Projects page** — Phases column shows ⚙ icon; phases modal table shows ✓ and ★ row actions; delete shows 🗑
4. **Users page** — table rows show ⬆ (upload) and ✏ icons with tooltips
5. **Task detail — Attachments tab** — list items show ⬇ and 🗑 icon buttons
6. **Task detail — Booked Work tab** — list items show ✏ and 🗑 icon buttons
7. **Task detail — Timeline tab** — cards show 📅/✏ and ✕ icon buttons
8. **Task detail — Participants tab** — list items show 🔔 danger icon button
9. **Reports page** — My Tasks table rows show → icon button
10. Confirm modal footers (Save/Cancel/Create) and page headers (New Task etc.) still show text labels
