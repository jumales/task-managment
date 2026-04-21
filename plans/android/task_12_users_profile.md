# task_12 — Users list + profile (avatar + language)

## Goal
Users list with role filter; own profile editable via `/users/me`; avatar upload via task_10 uploader; language toggle that updates both server preference and the Android locale.

## Changes

### New module `android/feature-users/`
- `build.gradle.kts` — `:core-ui`, `:domain`, `:data`, `:feature-attachments` (for avatar upload), Hilt.
- `list/UsersListScreen.kt` — paged via `GET /api/v1/users?page=&size=`; role filter chips.
- `profile/ProfileViewModel.kt`:
  - Loads `GET /api/v1/users/me`.
  - Edit name, language (`en` / `hr`), avatar.
  - Avatar upload: `FileUploader(bucket = "avatars")` → resulting `fileId` → `PATCH /api/v1/users/{id}/avatar`.
  - Language change: `PATCH /api/v1/users/{id}/language` + `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("hr"))`.
- `profile/ProfileScreen.kt`.
- `profile/LogoutSection.kt` — calls `AuthManager.logout(activity)` and triggers nav back to login.

### Admin-only
- `UsersListScreen` role-edit dialog visible only for ADMIN — `PUT /api/v1/users/{id}/roles` with new role set.

### Integration with task_17 (FCM)
- Logout must delete device-token via `DELETE /api/v1/device-tokens/{token}` BEFORE clearing local tokens (see task_17 for details). Leave a `TODO` marker here.

## Tests
- Unit: `ProfileViewModel.changeLanguage` calls API and applies locales.
- Instrumented: admin user sees role editor; non-admin does not.

## Acceptance
- Avatar uploaded from Android renders in webapp.
- Language change on Android persists across app restart and shows same preference in webapp after reload.
