package com.demo.taskmanager.core.network.push

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-process event bus for FCM task push messages.
 * [AppFirebaseMessagingService] emits here; [TaskDetailViewModel] collects to auto-refresh.
 * SharedFlow with extraBufferCapacity=64 so slow consumers don't drop events.
 */
@Singleton
class PushEventBus @Inject constructor() {

    private val _flow = MutableSharedFlow<TaskPushMessage>(extraBufferCapacity = 64)

    /** Collect this to receive incoming push events. */
    val flow: SharedFlow<TaskPushMessage> = _flow.asSharedFlow()

    /** Emits [message] to all active collectors. */
    suspend fun emit(message: TaskPushMessage) {
        _flow.emit(message)
    }
}
