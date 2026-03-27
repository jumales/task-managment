# Kafka Producer Non-Daemon Threads Block JVM Shutdown in Tests

## Problem

After a `@SpringBootTest` integration test that uses a real Kafka broker (via Testcontainers) completes,
the JVM hangs and refuses to exit. Surefire prints:

```
[ERROR] Surefire is going to kill self fork JVM. The exit has elapsed 30 seconds after System.exit(0).
```

On CI this caused the job to run for 16+ minutes (and would eventually hit the 6-hour GitHub Actions default).

## Root Cause

Spring's `KafkaTemplate` uses the Kafka client library internally. The Kafka client spawns a background
**NetworkClient** thread (`kafka-producer-network-thread | ...`) that handles all I/O to the broker.
This thread is **non-daemon**, meaning the JVM cannot exit while it is alive.

The sequence that causes the hang:

1. `TaskStatusKafkaIT` starts a `KafkaContainer` via Testcontainers and runs tests.
2. The test class finishes. Testcontainers stops the Kafka broker container.
3. Spring's context caching (`SpringExtension`) keeps the `ApplicationContext` alive for potential reuse by other test classes — **it does not close the context**.
4. The `OutboxPublisher` `@Scheduled` task fires every 5 seconds. It calls `kafkaTemplate.send(...)` which hits the now-dead broker.
5. The Kafka producer's NetworkClient thread keeps trying to reconnect, retrying with backoff.
6. When the test JVM finally tries to exit, the non-daemon NetworkClient thread prevents shutdown.
7. Surefire calls `System.exit(0)`, waits 30 seconds, then force-kills the process.

With two Kafka IT classes (`TaskStatusKafkaIT`, `TaskCommentKafkaIT`) each creating a separate cached
context, the total forced hang time was 2 × 30 seconds = 60+ seconds per test run, compounding further
on slow CI environments.

## Why It Wasn't Caught Earlier

The same tests were failing fast before the participants feature was added (deserialization errors
meant Kafka was never actually used). Once the tests were fixed and Kafka started being exercised
properly, the shutdown hang appeared for the first time.

## Fix

### 1. `@DirtiesContext` on Kafka IT classes

```java
@DirtiesContext
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, ...)
class TaskStatusKafkaIT { ... }
```

`@DirtiesContext` tells Spring to **close and discard** the `ApplicationContext` after the test class
finishes. Closing the context calls `destroy()` on all beans, which calls `close()` on the
`KafkaTemplate`/`KafkaProducer`. The producer's `close()` method shuts down the NetworkClient thread
cleanly, allowing the JVM to exit normally.

Use `@DirtiesContext` on any `@SpringBootTest` class that:
- Spins up a real Kafka broker via Testcontainers
- Uses any `@Scheduled` component that publishes to Kafka

### 2. CI job `timeout-minutes` as a safety net

```yaml
jobs:
  build-and-test:
    runs-on: ubuntu-latest
    timeout-minutes: 30
```

Without an explicit timeout, GitHub Actions defaults to **6 hours**. A single hung job can block
the branch and waste runner minutes. Setting `timeout-minutes: 30` ensures the job fails visibly
and quickly if a new hang is introduced.

## Trade-off

`@DirtiesContext` destroys and recreates the Spring context after the annotated class. This adds
~10–20 seconds per annotated class (context startup time). For Kafka IT classes this is acceptable
because the Testcontainers Kafka broker startup already dominates the test time.

Do **not** add `@DirtiesContext` to non-Kafka IT classes — they benefit from context sharing and
adding it there would slow the test suite unnecessarily.

## Related

- `OutboxPublisher` — the `@Scheduled` component whose background execution keeps the context "active"
- `TaskStatusKafkaIT`, `TaskCommentKafkaIT` — the two classes that required this fix
- GitHub Actions default timeout: 6 hours (see [docs](https://docs.github.com/en/actions/writing-workflows/workflow-syntax-for-github-actions#jobsjob_idtimeout-minutes))
