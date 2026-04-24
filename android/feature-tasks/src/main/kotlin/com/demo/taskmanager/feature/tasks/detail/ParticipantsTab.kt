package com.demo.taskmanager.feature.tasks.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.demo.taskmanager.core.ui.components.UserAvatar
import com.demo.taskmanager.data.dto.ParticipantDto
import com.demo.taskmanager.data.dto.enums.TaskParticipantRole

/**
 * Participants tab: shows current participants and buttons to join or watch the task.
 * Users can remove any participant by tapping the remove icon.
 */
@Composable
fun ParticipantsTab(
    participants: List<ParticipantDto>,
    onJoin: () -> Unit,
    onWatch: () -> Unit,
    onRemove: (participantId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onJoin, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.GroupAdd, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Join")
            }
            OutlinedButton(onClick = onWatch, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Visibility, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Watch")
            }
        }

        HorizontalDivider()

        if (participants.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No participants yet", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(participants, key = { it.id }) { participant ->
                    ParticipantRow(
                        participant = participant,
                        onRemove = { onRemove(participant.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ParticipantRow(
    participant: ParticipantDto,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            avatarUrl = null,
            displayName = participant.userName ?: "?",
            size = 36.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = participant.userName ?: participant.userId,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = participant.role.label(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        // Only allow removing non-CREATOR participants
        if (participant.role != TaskParticipantRole.CREATOR) {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.PersonRemove,
                    contentDescription = "Remove ${participant.userName}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

private fun TaskParticipantRole.label() = when (this) {
    TaskParticipantRole.CREATOR -> "Creator"
    TaskParticipantRole.ASSIGNEE -> "Assignee"
    TaskParticipantRole.CONTRIBUTOR -> "Contributor"
    TaskParticipantRole.WATCHER -> "Watcher"
}
