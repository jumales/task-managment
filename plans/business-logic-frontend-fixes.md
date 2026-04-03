# Plan: Frontend Business Logic Fixes

Source: business logic audit (`/business_logic` skill run 2026-04-03).  
All issues are frontend-only — no backend changes required.

---

## Issues to fix

| # | Severity | Issue | File |
|---|---|---|---|
| 1 | FAIL | `plannedStart` / `plannedEnd` not marked required in create wizard | `TasksPage.tsx:425-429` |
| 2 | FAIL | No `plannedStart < plannedEnd` cross-field validation in create wizard | `TasksPage.tsx` step 3 |
| 3 | FAIL | Already-used `workType` not filtered from planned-work dropdown | `TaskPlannedWorkTab.tsx:26-28` |
| 4 | FAIL | Timeline ordering (START < END) not validated before saving | `TaskTimelineTab.tsx:86-95` |
| 5 | FAIL | HTTP 409 on task / phase delete shows generic error instead of specific message | `TasksPage.tsx:235-237`, `ConfigurationPage.tsx:141-142` |
| 6 | WARN | Notification template allows unrecognized `{placeholder}` tokens | `ConfigurationPage.tsx` template fields |

---

## Fix 1 — `plannedStart` / `plannedEnd` required in create wizard

**File:** `web-client/src/pages/TasksPage.tsx`, lines 425–429

I18n keys already exist: `tasks.plannedStartRequired`, `tasks.plannedEndRequired`.
Also add both fields to the step 3 `validateFields` list (line 450) so the wizard
cannot advance without them.

```diff
- <Form.Item name="plannedStart" label={t('tasks.plannedStart')}>
+ <Form.Item name="plannedStart" label={t('tasks.plannedStart')} rules={[{ required: true, message: t('tasks.plannedStartRequired') }]}>
    <DatePicker showTime style={{ width: '100%' }} placeholder={t('tasks.selectDate')} />
  </Form.Item>
- <Form.Item name="plannedEnd" label={t('tasks.plannedEnd')}>
+ <Form.Item name="plannedEnd" label={t('tasks.plannedEnd')} rules={[{ required: true, message: t('tasks.plannedEndRequired') }]}>
    <DatePicker showTime style={{ width: '100%' }} placeholder={t('tasks.selectDate')} />
  </Form.Item>
```

Also extend the step 2 → step 3 `validateFields` call to include the date fields:
```diff
- const fieldsForStep = [
-   ['title'],
-   ['type', 'status', 'projectId', 'phaseId'],
- ][wizardStep];
+ const fieldsForStep = [
+   ['title'],
+   ['type', 'status', 'projectId', 'phaseId'],
+   ['assignedUserId', 'plannedStart', 'plannedEnd'],
+ ][wizardStep];
```
(Step 3 is the final step so `validateFields` on the full submit already covers it,
but adding the array makes the pattern consistent and protects future step re-ordering.)

---

## Fix 2 — `plannedStart < plannedEnd` cross-field validator

**File:** `web-client/src/pages/TasksPage.tsx`, line 428

Add a dependent validator on `plannedEnd` that reads `plannedStart` from the form.
A new i18n key is needed: `tasks.plannedEndMustBeAfterStart`.

Add to `en.json` → `tasks`:
```json
"plannedEndMustBeAfterStart": "Planned end must be after planned start"
```
Add to `hr.json` → `tasks`:
```json
"plannedEndMustBeAfterStart": "Planirani završetak mora biti nakon planiranog početka"
```

Component change:
```diff
  <Form.Item
    name="plannedEnd"
    label={t('tasks.plannedEnd')}
-   rules={[{ required: true, message: t('tasks.plannedEndRequired') }]}
+   rules={[
+     { required: true, message: t('tasks.plannedEndRequired') },
+     ({ getFieldValue }) => ({
+       validator(_, value) {
+         const start = getFieldValue('plannedStart');
+         if (!value || !start || value.isAfter(start)) return Promise.resolve();
+         return Promise.reject(new Error(t('tasks.plannedEndMustBeAfterStart')));
+       },
+     }),
+   ]}
+   dependencies={['plannedStart']}
  >
```

---

## Fix 3 — Filter already-used `workType` from planned-work dropdown

**File:** `web-client/src/components/taskDetail/TaskPlannedWorkTab.tsx`, lines 26–28

`plannedWork` is already available as a prop. Filter the options to exclude any
`workType` already present in the list.

```diff
  const workTypeOptions = useMemo(
-   () => (Object.keys(workTypeLabels) as WorkType[]).map((w) => ({ label: workTypeLabels[w], value: w })),
-   [workTypeLabels],
+   () => {
+     const usedTypes = new Set(plannedWork.map((pw) => pw.workType));
+     return (Object.keys(workTypeLabels) as WorkType[])
+       .filter((w) => !usedTypes.has(w))
+       .map((w) => ({ label: workTypeLabels[w], value: w }));
+   },
+   [workTypeLabels, plannedWork],
  );
```

Also reset `pwType` when the selected type becomes unavailable after save.
In `useTaskPlannedWork.ts`, after `setPlannedWork((prev) => [...prev, saved])`,
the type is already reset to `'DEVELOPMENT'`. The tab will re-compute options on
next render so no additional change needed there.

Edge case: if all 9 types are used, `workTypeOptions` becomes empty.
Hide the entire add-section in that case (inside the `taskStatus === 'TODO'` block):

```diff
+ {taskStatus === 'TODO' && workTypeOptions.length > 0 && (
- {taskStatus === 'TODO' && (
```

---

## Fix 4 — Timeline ordering validation (START must be before END)

**File:** `web-client/src/components/taskDetail/TaskTimelineTab.tsx`

The modal currently disables OK when `!tlUserId || !tlTimestamp` (line 86).
We need to also disable it when the new timestamp violates the pair ordering rule:
- Saving `PLANNED_END` → must be after the stored `PLANNED_START`
- Saving `PLANNED_START` → must be before the stored `PLANNED_END`
- Same for `REAL_START` / `REAL_END`

Pair map (static, defined outside the component):
```ts
const PAIR: Partial<Record<TimelineState, TimelineState>> = {
  PLANNED_END:  'PLANNED_START',
  REAL_END:     'REAL_START',
  PLANNED_START: 'PLANNED_END',
  REAL_START:    'REAL_END',
};
```

Add an `orderError` derived value inside the component:
```ts
const orderError = useMemo(() => {
  if (!editingState || !tlTimestamp) return null;
  const pairedState = PAIR[editingState];
  if (!pairedState) return null;
  const paired = timelines.find((tl) => tl.state === pairedState);
  if (!paired) return null;
  const isEndState = editingState.endsWith('_END');
  const pairedDayjs = dayjs(paired.timestamp);
  if (isEndState && !tlTimestamp.isAfter(pairedDayjs)) return t('tasks.endMustBeAfterStart');
  if (!isEndState && !tlTimestamp.isBefore(pairedDayjs)) return t('tasks.startMustBeBeforeEnd');
  return null;
}, [editingState, tlTimestamp, timelines, t]);
```

Update the OK button and add an error message below the DatePicker:
```diff
- okButtonProps={{ disabled: !tlUserId || !tlTimestamp }}
+ okButtonProps={{ disabled: !tlUserId || !tlTimestamp || !!orderError }}
```
```diff
  <DatePicker ... />
+ {orderError && <Typography.Text type="danger">{orderError}</Typography.Text>}
```

New i18n keys needed:

`en.json` → `tasks`:
```json
"endMustBeAfterStart": "End date must be after the start date",
"startMustBeBeforeEnd": "Start date must be before the end date"
```
`hr.json` → `tasks`:
```json
"endMustBeAfterStart": "Datum završetka mora biti nakon datuma početka",
"startMustBeBeforeEnd": "Datum početka mora biti prije datuma završetka"
```

---

## Fix 5 — HTTP 409 shows specific error on task / phase delete

### 5a — Task deletion (`TasksPage.tsx:235-237`)

```diff
  .catch((err) => {
-   const message = err?.response?.data?.message ?? err?.message ?? t('tasks.failedDelete');
-   setError(`${t('tasks.failedDelete')}: ${message}`);
+   if (err?.response?.status === 409) {
+     setError(t('tasks.deleteBlockedByRelations'));
+   } else {
+     const message = err?.response?.data?.message ?? err?.message ?? t('tasks.failedDelete');
+     setError(`${t('tasks.failedDelete')}: ${message}`);
+   }
  })
```

### 5b — Phase deletion (`ConfigurationPage.tsx:141-142`)

```diff
  } catch (err: unknown) {
-   setError(t('configuration.failedDeletePhase'));
+   const status = (err as { response?: { status?: number } })?.response?.status;
+   if (status === 409) {
+     setError(t('configuration.deletePhaseBlockedByTasks'));
+   } else {
+     setError(t('configuration.failedDeletePhase'));
+   }
  }
```

New i18n keys needed:

`en.json` → `tasks`:
```json
"deleteBlockedByRelations": "Cannot delete — this task has comments. Remove all comments first."
```
`en.json` → `configuration`:
```json
"deletePhaseBlockedByTasks": "Cannot delete — this phase still has tasks assigned to it."
```

`hr.json` → `tasks`:
```json
"deleteBlockedByRelations": "Nije moguće brisanje — zadatak ima komentare. Prvo uklonite sve komentare."
```
`hr.json` → `configuration`:
```json
"deletePhaseBlockedByTasks": "Nije moguće brisanje — ova faza još uvijek ima zadatke."
```

---

## Fix 6 — Notification template: warn on unrecognized `{placeholder}` tokens (WARN)

**File:** `web-client/src/pages/ConfigurationPage.tsx`, template subject and body fields.

The fetched `placeholders` array is already available in scope.
Add a warning validator to both `Form.Item` fields that scans for `{token}` patterns
and cross-checks against known keys.

```ts
const validatePlaceholders = (_: unknown, value: string) => {
  if (!value) return Promise.resolve();
  const knownKeys = new Set(placeholders.map((p) => p.key));
  const tokens = [...value.matchAll(/\{(\w+)\}/g)].map((m) => m[1]);
  const unknown = tokens.filter((tok) => !knownKeys.has(tok));
  if (unknown.length === 0) return Promise.resolve();
  return Promise.reject(
    new Error(t('configuration.unknownPlaceholders', { tokens: unknown.join(', ') }))
  );
};
```

Apply as a `warningOnly` rule on each template field:
```diff
  rules={[
    { required: true, message: t('...') },
+   { validator: validatePlaceholders, warningOnly: true },
  ]}
```

New i18n keys needed:

`en.json` → `configuration`:
```json
"unknownPlaceholders": "Unknown placeholder(s): {{tokens}} — these will be left as-is in the notification"
```
`hr.json` → `configuration`:
```json
"unknownPlaceholders": "Nepoznati placeholder(i): {{tokens}} — ostat će nepromijenjeni u obavijesti"
```

---

## Implementation order

1. Fix 1 — required dates (simplest, i18n keys already exist)
2. Fix 2 — date ordering validator (depends on Fix 1 being in place)
3. Fix 5 — 409 error messages (independent, easy)
4. Fix 3 — work type filter (independent)
5. Fix 4 — timeline ordering (slightly more logic, depends on understanding hook state)
6. Fix 6 — placeholder warn validator (lowest priority)

All fixes are in the same branch. One PR covers all.

## Branch name

`fix_frontend_business_logic_validation`
