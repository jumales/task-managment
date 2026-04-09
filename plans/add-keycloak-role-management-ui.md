# Keycloak Role Management via Application UI

**Goal**: Allow admins to assign and remove Keycloak realm roles for users directly from the Users page in the web client.

**Branch**: `add_keycloak_role_management_ui`

---

## Context

Currently, role assignment is only possible via the Keycloak admin console. The `UserDto` has no `roles` field, no role endpoints exist, and the UI shows no role information. Admins need to manage roles (ADMIN, DEVELOPER, QA, DEVOPS, PM, SUPERVISOR) from within the application. `WEB_APP` is always auto-assigned on user creation and must never be user-manageable.

---

## Call sites for `new UserDto(...)` — must all gain `List.of()` as 8th arg in Chunk 1

**Production (1 site):**
- `KeycloakUserClient.java:347`

**Tests (35 sites across 3 services):**
- `user-service`: `UserControllerIT.java` — 8 sites
- `task-service`: `TaskControllerIT`, `TaskProjectControllerIT`, `TaskPhaseControllerIT`, `TaskParticipantControllerIT`, `TaskBookedWorkControllerIT`, `TaskPlannedWorkControllerIT`, `TaskTimelineControllerIT`, `TaskCommentKafkaIT`, `TaskStatusKafkaIT`, `TaskAttachmentControllerIT`, `ControllerLoggingAspectIT` — 26 sites
- `notification-service`: `NotificationConsumerIT.java` — 1 site

Note: `GlobalExceptionHandler` already maps `IllegalArgumentException` → 400, so no changes needed there.

---

## Chunks

### Chunk 1 — Common DTO + Keycloak Client role methods
**What**:
- Add `List<String> roles` as last field to `UserDto` (in common/)
- Update all 36 `new UserDto(...)` call sites to pass `List.of()` as 8th arg
- Add role-name constants to `KeycloakUserClient`: `ROLE_ADMIN`, `ROLE_DEVELOPER`, `ROLE_QA`, `ROLE_DEVOPS`, `ROLE_PM`, `ROLE_SUPERVISOR`, and `MANAGEABLE_ROLES = Set.of(...)`
- Generalize `fetchWebAppRoleRepresentation()` → `fetchRoleRepresentation(String roleName)`
- Add `getUserRoles(UUID userId)` — calls `GET /users/{userId}/role-mappings/realm`, filters to `MANAGEABLE_ROLES`, returns list of names
- Add `setUserRoles(UUID userId, List<String> roleNames)` — validates names are in `MANAGEABLE_ROLES`, computes diff vs current, POSTs additions and DELETEs removals (Keycloak DELETE requires full role representation objects)
- Add `toDtoWithRoles(Map<String, Object> rep)` — calls `getUserRoles` after base mapping; use only in `findByIdDirect`. Keep base `toDto()` without roles (avoids N+1 in findAll)
- Add new methods to `KeycloakUserPort` interface with Javadoc

**Files**:
- `common/src/main/java/com/demo/common/dto/UserDto.java`
- `user-service/src/main/java/com/demo/user/keycloak/KeycloakUserClient.java`
- `user-service/src/main/java/com/demo/user/keycloak/KeycloakUserPort.java`
- All 35 test files listed above

**Commit**: `feat(user): add roles field to UserDto and generalize Keycloak role helpers`

---

### Chunk 2 — UserService + UserController role endpoints
**What**:
- Add `getUserRoles(UUID userId)` and `setUserRoles(UUID userId, List<String> roleNames)` to `UserService` (thin delegation to port)
- Add two controller endpoints (both `@PreAuthorize("hasRole('ADMIN')")`):
  - `GET /api/v1/users/{id}/roles` → `List<String>` — returns current manageable roles
  - `PUT /api/v1/users/{id}/roles` → `List<String>` — replaces roles, returns updated list (avoids follow-up GET)
- Add OpenAPI `@Operation` and `@ApiResponse` annotations to both endpoints

**Files**:
- `user-service/src/main/java/com/demo/user/service/UserService.java`
- `user-service/src/main/java/com/demo/user/controller/UserController.java`

**Commit**: `feat(user): expose GET/PUT /users/{id}/roles endpoints (ADMIN only)`

---

### Chunk 3 — Integration tests for role endpoints
**What**: Extend `UserControllerIT` with 5 test cases:
- `getRoles_returnsRoleList()` — 200 with list
- `getRoles_whenUserNotFound_returns404()` — mocks `ResourceNotFoundException`
- `setRoles_replacesRoles_returnsUpdatedList()` — 200 with new list
- `setRoles_withInvalidRole_returns400()` — mocks `IllegalArgumentException`
- `setRoles_emptyList_removesAllRoles()` — 200 with empty list

**Files**:
- `user-service/src/test/java/com/demo/user/UserControllerIT.java`

**Commit**: `test(user): add integration tests for GET/PUT /users/{id}/roles`

---

### Chunk 4 — Postman + Frontend types + API functions
**What**:
- Add `GET /users/{{userId}}/roles` and `PUT /users/{{userId}}/roles` to the Users folder in the Postman collection
- Add `roles: string[]` to `UserResponse` in `types.ts`
- Add `RealmRole` type alias: `'ADMIN' | 'DEVELOPER' | 'QA' | 'DEVOPS' | 'PM' | 'SUPERVISOR'`
- Add `getUserRoles(userId)` and `setUserRoles(userId, roles)` to `userApi.ts`

**Files**:
- `postman/user-service.postman_collection.json`
- `web-client/src/api/types.ts`
- `web-client/src/api/userApi.ts`

**Commit**: `feat(frontend): add role types, API functions, and update Postman collection`

---

### Chunk 5 — Frontend UsersPage role UI + i18n
**What**:
- Add a "Roles" column to the users table rendering roles as blue `<Tag>` components (empty for list view since findAll returns `roles: []`)
- In the edit modal (admin only), add a multi-select `<Select mode="multiple">` with options `['ADMIN', 'DEVELOPER', 'QA', 'DEVOPS', 'PM', 'SUPERVISOR']` — defined as a module-level constant `ASSIGNABLE_ROLE_OPTIONS` (following `TaskParticipantsTab` pattern)
- `openEditModal` fetches current roles via `getUserRoles()` — modal opens immediately, `rolesLoading` drives the Select's `loading` prop (no waterfall)
- `handleSubmit` calls `Promise.all([updateUser(...), setUserRoles(...)])` in parallel
- Add `roles: []` to inline `UserResponse` constructions (e.g. in search results mapping)
- Add i18n keys `users.selectRoles` and `users.failedLoadRoles` to both locale files

**Files**:
- `web-client/src/pages/UsersPage.tsx`
- `web-client/src/i18n/locales/en.json`
- `web-client/src/i18n/locales/hr.json`

**Commit**: `feat(frontend): role tag column and multi-select in user edit modal`

---

## Verification

1. `mvn clean install -DskipTests=true` must pass after Chunk 1 (all call sites updated)
2. `mvn test` in user-service passes after Chunk 3
3. Manually: create a user → open edit modal → assign QA + DEVELOPER → save → reopen modal → roles are pre-selected
4. Manually: assign SUPERVISOR role → user is restricted to read-only in task endpoints
5. Confirm WEB_APP never appears in the role multi-select
