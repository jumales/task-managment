package com.demo.task;

import com.demo.common.dto.TaskPhaseName;
import com.demo.common.dto.TaskPhaseRequest;
import com.demo.common.dto.TaskPhaseResponse;
import com.demo.common.dto.TaskProjectRequest;
import com.demo.common.dto.TaskProjectResponse;
import com.demo.common.dto.TaskRequest;
import com.demo.common.dto.TaskResponse;
import com.demo.common.dto.TaskStatus;
import com.demo.common.dto.TaskType;
import com.demo.common.dto.UserDto;
import com.demo.task.client.UserClient;
import com.demo.task.repository.TaskCodeJobRepository;
import com.demo.task.repository.TaskParticipantRepository;
import com.demo.task.repository.TaskPhaseRepository;
import com.demo.task.repository.TaskProjectRepository;
import com.demo.task.repository.TaskRepository;
import com.demo.task.repository.TaskTimelineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Concurrency integration tests for optimistic locking on task updates.
 *
 * <p>Verifies that when N users simultaneously submit a PUT with the same stale version,
 * exactly one succeeds (HTTP 200) and all others are rejected with HTTP 409 Conflict.
 * Runs against a real PostgreSQL container so the {@code WHERE id=? AND version=?}
 * constraint is enforced at the database level, not just in-memory.
 */
@Import(TestSecurityConfig.class)
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false"
)
class TaskOptimisticLockIT {

    private static final int CONCURRENT_USERS = 100;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    UserClient userClient;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired TaskRepository taskRepository;
    @Autowired TaskProjectRepository projectRepository;
    @Autowired TaskParticipantRepository participantRepository;
    @Autowired TaskTimelineRepository timelineRepository;
    @Autowired TaskPhaseRepository phaseRepository;
    @Autowired TaskCodeJobRepository taskCodeJobRepository;

    private UUID projectId;
    private UUID planningPhaseId;

    @BeforeEach
    void setUp() {
        taskCodeJobRepository.deleteAll();
        participantRepository.deleteAll();
        timelineRepository.deleteAll();
        taskRepository.deleteAll();
        phaseRepository.deleteAll();
        projectRepository.deleteAll();

        UserDto testUser = new UserDto(TestSecurityConfig.TEST_USER_ID,
                "Test Admin", "admin@test.com", null, true, null, "en", List.of());
        when(userClient.getUserById(any(UUID.class))).thenReturn(testUser);
        when(userClient.getUsersByIds(anyList())).thenReturn(List.of(testUser));

        TaskProjectRequest projectReq = new TaskProjectRequest();
        projectReq.setName("Lock Test Project");
        projectId = restTemplate.postForEntity("/api/v1/projects", projectReq, TaskProjectResponse.class)
                .getBody().getId();

        TaskPhaseRequest planningReq = new TaskPhaseRequest();
        planningReq.setName(TaskPhaseName.PLANNING);
        planningReq.setProjectId(projectId);
        planningPhaseId = restTemplate.postForEntity("/api/v1/phases", planningReq, TaskPhaseResponse.class)
                .getBody().getId();
    }

    /**
     * Core scenario: 100 users all read version=0 and all submit PUT concurrently.
     * <ul>
     *   <li>Exactly 1 update must succeed (HTTP 200) — the one whose UPDATE wins the DB row lock.</li>
     *   <li>Exactly 99 must be rejected (HTTP 409) — their {@code WHERE version=0} clause
     *       finds 0 rows after the first commit incremented the counter to 1.</li>
     * </ul>
     */
    @Test
    void update_100UsersSubmitSameVersion_exactlyOneSucceeds() throws InterruptedException {
        // ── Arrange ──────────────────────────────────────────────────────────────
        TaskResponse created = restTemplate.postForEntity(
                "/api/v1/tasks", buildRequest("Original title"), TaskResponse.class).getBody();

        assertThat(created.getVersion()).isEqualTo(0L); // fresh task starts at version 0
        UUID taskId   = created.getId();
        Long version0 = created.getVersion();

        CountDownLatch startGate = new CountDownLatch(1);        // releases all threads at once
        CountDownLatch doneLatch  = new CountDownLatch(CONCURRENT_USERS); // waits for all to finish

        // Per-status counters — ConcurrentHashMap is used for thread-safe counting
        Map<Integer, AtomicInteger> statusCounts = new ConcurrentHashMap<>();
        statusCounts.put(200, new AtomicInteger(0));
        statusCounts.put(409, new AtomicInteger(0));
        AtomicInteger otherCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);

        // ── 100 threads, all holding the same stale version ───────────────────
        IntStream.range(0, CONCURRENT_USERS).forEach(i -> executor.submit(() -> {
            try {
                startGate.await(); // block until all threads are ready

                TaskRequest req = buildRequest("Updated by thread " + i);
                req.setVersion(version0); // every thread sends the same version=0

                // Use String.class to avoid Jackson mapping the error body's "status":409
                // integer field to TaskStatus enum when the server returns 409 Conflict.
                ResponseEntity<String> response = restTemplate.exchange(
                        "/api/v1/tasks/" + taskId,
                        HttpMethod.PUT,
                        new HttpEntity<>(req),
                        String.class);

                int status = response.getStatusCode().value();
                if (status == 200) statusCounts.get(200).incrementAndGet();
                else if (status == 409) statusCounts.get(409).incrementAndGet();
                else otherCount.incrementAndGet();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        }));

        // ── Act: release all threads simultaneously ───────────────────────────
        startGate.countDown();
        boolean finished = doneLatch.await(60, TimeUnit.SECONDS);

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(finished).as("All threads should complete within 60 seconds").isTrue();

        int successes  = statusCounts.get(200).get();
        int conflicts  = statusCounts.get(409).get();
        int unexpected = otherCount.get();

        assertThat(unexpected).as("No unexpected HTTP status codes").isEqualTo(0);
        assertThat(successes).as("Exactly one update must win the optimistic lock").isEqualTo(1);
        assertThat(conflicts).as("Remaining 99 must be rejected with 409 Conflict")
                .isEqualTo(CONCURRENT_USERS - 1);

        // The winning update incremented the version to 1
        TaskResponse finalTask = restTemplate.getForEntity(
                "/api/v1/tasks/" + taskId, TaskResponse.class).getBody();
        assertThat(finalTask.getVersion()).as("Version incremented to 1 after one successful update")
                .isEqualTo(1L);
    }

    /**
     * Verifies the simpler deterministic case: a single stale update returns 409.
     * Acts as a focused regression guard independent of concurrency mechanics.
     */
    @Test
    void update_withStaleVersion_returns409() {
        // Create task (version=0), update once (version becomes 1), then retry with version=0
        TaskResponse v0 = restTemplate.postForEntity(
                "/api/v1/tasks", buildRequest("First"), TaskResponse.class).getBody();

        TaskRequest firstUpdate = buildRequest("Second");
        firstUpdate.setVersion(v0.getVersion()); // version=0 → should succeed
        ResponseEntity<TaskResponse> v1Response = restTemplate.exchange(
                "/api/v1/tasks/" + v0.getId(), HttpMethod.PUT,
                new HttpEntity<>(firstUpdate), TaskResponse.class);

        assertThat(v1Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(v1Response.getBody().getVersion()).isEqualTo(1L);

        // Now submit with the original stale version=0 — must be rejected
        TaskRequest staleUpdate = buildRequest("Third");
        staleUpdate.setVersion(v0.getVersion()); // stale: version=0, DB now has version=1
        ResponseEntity<String> staleResponse = restTemplate.exchange(
                "/api/v1/tasks/" + v0.getId(), HttpMethod.PUT,
                new HttpEntity<>(staleUpdate), String.class);

        assertThat(staleResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(staleResponse.getBody()).contains("modified by another user");
    }

    /**
     * Verifies backward compatibility: when version is omitted (null), the update
     * proceeds with last-write-wins semantics and returns 200. No 409 should occur.
     */
    @Test
    void update_withoutVersion_succeedsWithLastWriteWins() {
        TaskResponse created = restTemplate.postForEntity(
                "/api/v1/tasks", buildRequest("Original"), TaskResponse.class).getBody();

        TaskRequest req = buildRequest("Updated without version");
        // version intentionally not set — null

        ResponseEntity<TaskResponse> response = restTemplate.exchange(
                "/api/v1/tasks/" + created.getId(), HttpMethod.PUT,
                new HttpEntity<>(req), TaskResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getVersion()).isEqualTo(1L);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /** Builds a valid task request pointing to the project and PLANNING phase created in setUp(). */
    private TaskRequest buildRequest(String title) {
        TaskRequest req = new TaskRequest();
        req.setTitle(title);
        req.setDescription("Test description");
        req.setStatus(TaskStatus.TODO);
        req.setType(TaskType.FEATURE);
        req.setProgress(0);
        req.setAssignedUserId(TestSecurityConfig.TEST_USER_ID);
        req.setProjectId(projectId);
        req.setPhaseId(planningPhaseId);
        req.setPlannedStart(Instant.parse("2026-05-01T08:00:00Z"));
        req.setPlannedEnd(Instant.parse("2026-05-31T17:00:00Z"));
        return req;
    }
}
