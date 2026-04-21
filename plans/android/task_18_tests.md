# task_18 — Consolidated test coverage + CI

## Goal
Progressive tests ship with each feature chunk; this chunk closes gaps and wires Android CI.

## Changes

### Unit tests (JUnit5 + MockK + Turbine + kotlinx-coroutines-test)
- `:domain` — use-case contract tests.
- `:data` — every repository tested with MockWebServer:
  - Happy path + 4xx + 5xx + timeout.
  - Serialization: known Postman response fixture → DTO → re-encode equals original (per `postman/*.postman_collection.json`).
- `:core-network`:
  - `AuthInterceptor` adds/omits header.
  - `TokenRefreshAuthenticator` — two concurrent 401s → single refresh (`Mutex` test).
  - `TokenStore` — round-trip write / read; clear on logout.
  - `AuthManager` — happy path using AppAuth test doubles.
- Feature ViewModels (one test class each): tasks-list, task-detail, task-create, booked-work, planned-work, projects, users, search, reports, config, profile. Use `MainDispatcherRule` + `Turbine`.

### Instrumented tests (Compose UI Test + Gradle Managed Devices)
- `:app` `androidTest` module:
  - Login flow: stub AppAuth → land on `tasks` screen.
  - `TasksListScreen`: filter chip toggle → list re-queries; empty state shown when list empty; paging loads page 2.
  - `TaskDetailScreen`: tab switch loads correct data once; push via `PushEventBus` triggers refetch.
- Gradle Managed Device declaration in `:app/build.gradle.kts`:
  ```kotlin
  testOptions.managedDevices.devices {
    create("pixel6api33", com.android.build.api.dsl.ManagedVirtualDevice::class) {
      device = "Pixel 6"; apiLevel = 33; systemImageSource = "aosp"
    }
  }
  ```
  Task: `./gradlew :app:pixel6api33EmulatorDebugAndroidTest`.

### Coverage
- Enable JaCoCo on `:data`, `:domain`, `:core-network`, feature modules.
- Targets: `:domain` ≥ 70%, `:data` ≥ 70%, `:core-network` ≥ 70%, feature modules ≥ 50% (realistic given Compose UI).

### CI
- New GitHub Actions workflow `.github/workflows/android.yml`:
  - Matrix: `emulator` flavor debug.
  - Steps: checkout → JDK 17 → Android SDK → `./gradlew :app:lintEmulatorDebug :app:testEmulatorDebugUnitTest :data:jacocoTestReport :domain:test :core-network:test detekt`.
  - Upload jacoco XML to Codecov.
- Don't run instrumented tests on every push (slow); trigger on `release/*` branches only.

### Lint / static
- Add `io.gitlab.arturbosch.detekt` + baseline; `ktlint` via detekt plugin.
- `./gradlew detekt` expected clean.

## Acceptance
- All unit tests green locally + CI.
- Coverage thresholds met.
- New `android` CI job appears on PRs touching `android/**` or `notification-service/**` (via path filter).
