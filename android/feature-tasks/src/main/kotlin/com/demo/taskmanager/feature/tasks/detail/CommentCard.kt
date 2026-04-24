package com.demo.taskmanager.feature.tasks.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.demo.taskmanager.core.ui.components.UserAvatar
import com.demo.taskmanager.domain.model.Comment
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Single comment row: avatar, author name, relative timestamp, and markdown body. */
@Composable
fun CommentCard(
    comment: Comment,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(
                avatarUrl = null,
                displayName = comment.userName ?: "?",
                size = 32.dp,
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = comment.userName ?: "Unknown",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = relativeTime(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        MarkdownText(
            markdown = comment.content,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
    }
}

/** Converts an ISO-8601 timestamp to a short human-readable relative string. */
private fun relativeTime(iso: String): String =
    runCatching {
        val seconds = ChronoUnit.SECONDS.between(Instant.parse(iso), Instant.now())
        when {
            seconds < 60 -> "just now"
            seconds < 3_600 -> "${seconds / 60}m ago"
            seconds < 86_400 -> "${seconds / 3_600}h ago"
            else -> "${seconds / 86_400}d ago"
        }
    }.getOrDefault(iso)
