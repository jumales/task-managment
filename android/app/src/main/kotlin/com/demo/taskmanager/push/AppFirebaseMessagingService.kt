package com.demo.taskmanager.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import com.demo.taskmanager.MainActivity
import com.demo.taskmanager.core.network.auth.AuthManager
import com.demo.taskmanager.core.network.auth.AuthState
import com.demo.taskmanager.core.network.push.PushEventBus
import com.demo.taskmanager.core.network.push.TaskPushMessage
import com.demo.taskmanager.R
import com.demo.taskmanager.data.repo.DeviceTokenRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles FCM token lifecycle and incoming data-only push messages.
 * Registered in AndroidManifest.xml with the MESSAGING_EVENT intent filter.
 */
@AndroidEntryPoint
class AppFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var authManager: AuthManager
    @Inject lateinit var deviceTokenRepository: DeviceTokenRepository
    @Inject lateinit var pushEventBus: PushEventBus

    /** Fire-and-forget scope; SupervisorJob prevents one failure from cancelling siblings. */
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Called when Firebase issues a new token (fresh install, token expiry, or app reinstall).
     * Rotates the token on the backend if the user is already authenticated; otherwise caches it
     * as pending so it's registered on the next successful login.
     */
    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token issued")
        val state = authManager.authState.value
        if (state is AuthState.Authenticated) {
            val oldToken = deviceTokenRepository.getPendingToken()
                ?: return registerFresh(token)
            serviceScope.launch {
                runCatching { deviceTokenRepository.rotate(oldToken, token) }
                    .onFailure { Log.w(TAG, "Token rotate failed", it) }
            }
        } else {
            deviceTokenRepository.storePendingToken(token)
        }
    }

    /**
     * Called for every incoming data-only push (the backend never sets a display notification block).
     * Builds a system notification with a deep-link intent, and emits to [PushEventBus] so any
     * currently-open [TaskDetailScreen] can refresh in-process.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val taskId = message.data[KEY_TASK_ID] ?: return
        val changeType = message.data[KEY_CHANGE_TYPE] ?: "UPDATED"

        serviceScope.launch {
            runCatching { pushEventBus.emit(TaskPushMessage(taskId, changeType)) }
        }

        showNotification(taskId, changeType)
    }

    private fun registerFresh(token: String) {
        serviceScope.launch {
            runCatching { deviceTokenRepository.register(token) }
                .onSuccess { deviceTokenRepository.clearPendingToken() }
                .onFailure { Log.w(TAG, "Token register failed", it) }
        }
    }

    private fun showNotification(taskId: String, changeType: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)

        val deepLinkUri = Uri.parse("taskmanager://tasks/$taskId")
        val deepLinkIntent = Intent(Intent.ACTION_VIEW, deepLinkUri, this, MainActivity::class.java)

        val pendingIntent = TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(deepLinkIntent)
            getPendingIntent(taskId.hashCode(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Task updated")
            .setContentText(formatChangeType(changeType))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(taskId.hashCode(), notification)
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "Task updates", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(channel)
    }

    private fun formatChangeType(changeType: String): String =
        changeType.replace('_', ' ').lowercase().replaceFirstChar { it.uppercaseChar() }

    companion object {
        private const val TAG = "AppFcmService"
        private const val CHANNEL_ID = "task_updates"
        private const val KEY_TASK_ID = "taskId"
        private const val KEY_CHANGE_TYPE = "changeType"
    }
}
