package com.demo.task;

import com.demo.common.web.MdcTaskDecorator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link MdcTaskDecorator} correctly propagates the calling thread's MDC
 * context into async threads and clears it on completion.
 * Uses a real thread pool — {@code SyncTaskExecutor} from {@link TestSecurityConfig} would
 * keep everything on the same thread, making MDC propagation trivially pass without the decorator.
 */
class MdcTaskDecoratorTest {

    private ThreadPoolTaskExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        executor.destroy();
    }

    @Test
    void shouldPropagateCallerRequestIdToAsyncThread() throws InterruptedException {
        MDC.put("requestId", "trace-abc-123");
        AtomicReference<String> captured = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        executor.execute(() -> {
            captured.set(MDC.get("requestId"));
            latch.countDown();
        });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(captured.get()).isEqualTo("trace-abc-123");
    }

    @Test
    void shouldPropagateAllMdcKeysToAsyncThread() throws InterruptedException {
        MDC.put("requestId", "req-1");
        MDC.put("method", "POST");
        MDC.put("path", "/api/v1/tasks");

        AtomicReference<String> capturedMethod = new AtomicReference<>();
        AtomicReference<String> capturedPath   = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        executor.execute(() -> {
            capturedMethod.set(MDC.get("method"));
            capturedPath.set(MDC.get("path"));
            latch.countDown();
        });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(capturedMethod.get()).isEqualTo("POST");
        assertThat(capturedPath.get()).isEqualTo("/api/v1/tasks");
    }

    @Test
    void shouldClearMdcOnAsyncThreadAfterTaskCompletes() throws InterruptedException {
        MDC.put("requestId", "req-to-clear");
        AtomicReference<String> capturedInsideTask = new AtomicReference<>();
        AtomicReference<String> capturedAfterTask  = new AtomicReference<>();
        CountDownLatch taskDone   = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);

        executor.execute(() -> {
            capturedInsideTask.set(MDC.get("requestId"));
            taskDone.countDown();
        });

        assertThat(taskDone.await(5, TimeUnit.SECONDS)).isTrue();

        // Clear MDC on the calling thread before submitting the second task.
        // The decorator snapshots caller MDC at submission time — if caller still has MDC set,
        // the second task would correctly inherit it. We want to verify the async thread's MDC
        // was cleared by the finally block, not that new tasks spawn with empty context.
        MDC.clear();
        executor.execute(() -> {
            capturedAfterTask.set(MDC.get("requestId"));
            secondDone.countDown();
        });

        assertThat(secondDone.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(capturedInsideTask.get()).isEqualTo("req-to-clear");
        assertThat(capturedAfterTask.get()).isNull();
    }

    @Test
    void shouldNotThrowWhenCallerMdcIsEmpty() throws InterruptedException {
        MDC.clear();
        CountDownLatch latch = new CountDownLatch(1);

        executor.execute(latch::countDown);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    }
}
