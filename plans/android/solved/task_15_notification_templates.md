# task_15 — Configuration: notification templates

## Goal
ADMIN-only screen to edit per-project notification templates per `TaskChangeType` — same endpoints webapp uses.

## Changes

### New module `android/feature-config/`
- `build.gradle.kts` — `:core-ui`, `:data`, `:domain`, Hilt.
- `templates/TemplatesListScreen.kt`:
  - Project picker at top.
  - List of `TaskChangeType` rows (enum from task_04) with template preview.
- `templates/TemplateEditScreen.kt`:
  - Subject + body editable.
  - Placeholder hints panel derived from `common/src/main/java/com/demo/common/dto/ProjectNotificationTemplateResponse.java`: e.g. `{{taskCode}}`, `{{authorName}}`, `{{status}}`.
  - Save → `PUT /api/v1/projects/{projectId}/notification-templates/{eventType}`.
  - Delete → `DELETE /api/v1/projects/{projectId}/notification-templates/{eventType}` (reverts to default).

### Role gating
- Entire `config` route hidden from navigation when JWT lacks ADMIN.
- Even with nav hidden, `AuthGate` + server-side RBAC stops non-admin hits from succeeding.

## Tests
- Unit: non-admin auth state → nav composable does not register `config` route.
- Instrumented: edit template → save → GET returns updated body.

## Acceptance
- Edit template from Android → next Kafka event email uses new body.
- Non-admin user does not see the Configuration tab.
