package com.demo.taskmanager.core.network.push

/** Payload emitted by [PushEventBus] when a task-related FCM push is received. */
data class TaskPushMessage(
    val taskId: String,
    val changeType: String,
)
