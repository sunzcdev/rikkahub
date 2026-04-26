package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import kotlin.uuid.Uuid

@Composable
fun CreateGroupChatDialog(
    assistants: List<Assistant>,
    onDismiss: () -> Unit,
    onConfirm: (List<Uuid>) -> Unit,
) {
    var selectedIds by remember { mutableStateOf(setOf<Uuid>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.group_chat_new_group_chat))
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.auto_discuss_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (assistants.isEmpty()) {
                    Text(
                        text = stringResource(R.string.group_chat_no_available_assistants),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(assistants, key = { it.id.toString() }) { assistant ->
                            val isSelected = assistant.id in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedIds = if (isSelected) {
                                            selectedIds - assistant.id
                                        } else {
                                            selectedIds + assistant.id
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        selectedIds = if (it) {
                                            selectedIds + assistant.id
                                        } else {
                                            selectedIds - assistant.id
                                        }
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                UIAvatar(
                                    name = assistant.name.ifBlank { "Assistant" },
                                    value = assistant.avatar,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedIds.toList()) },
                enabled = selectedIds.size >= 2,
            ) {
                Text(stringResource(R.string.assistant_page_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
