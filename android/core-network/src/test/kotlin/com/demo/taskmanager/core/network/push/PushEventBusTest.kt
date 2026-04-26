package com.demo.taskmanager.core.network.push

import app.cash.turbine.test
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PushEventBusTest {

    private val bus = PushEventBus()

    @Test
    fun `emitted message is received by collector`() = runTest {
        val message = TaskPushMessage(taskId = "task-1", changeType = "STATUS_CHANGED")
        bus.flow.test {
            bus.emit(message)
            assertEquals(message, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple collectors each receive emitted message`() = runTest {
        val message = TaskPushMessage(taskId = "task-2", changeType = "COMMENT_ADDED")
        val received1 = mutableListOf<TaskPushMessage>()
        val received2 = mutableListOf<TaskPushMessage>()

        bus.flow.test {
            bus.emit(message)
            received1.add(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        bus.flow.test {
            bus.emit(message)
            received2.add(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf(message), received1)
        assertEquals(listOf(message), received2)
    }

    @Test
    fun `filter by taskId — unrelated push is not collected`() = runTest {
        val targetId = "task-target"
        val other = TaskPushMessage(taskId = "task-other", changeType = "UPDATED")

        bus.flow
            .filter { it.taskId == targetId }
            .test {
                bus.emit(other)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
    }
}
