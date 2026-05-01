package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.dokar.sonner.ToastType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState

@Composable
fun TopBar(
    conversation: Conversation,
    assistants: List<Assistant>,
    onBack: () -> Unit,
    onUpdateTitle: (String) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    onDeleteConversation: (() -> Unit)? = null,
) {
    val toaster = LocalToaster.current
    val titleState = useEditState<String> {
        onUpdateTitle(it)
    }

    var showMoreMenu by remember { mutableStateOf(false) }
    val editTitleWarning = stringResource(R.string.chat_page_edit_title_warning)

    val groupChatConfig = conversation.groupChatConfig
    val subtitle = if (groupChatConfig.isGroupChat) {
        groupChatConfig.enabledParticipants.mapNotNull { p ->
            assistants.find { it.id == p.assistantId }?.name
        }.joinToString(", ")
    } else {
        assistants.find { it.id == conversation.assistantId }?.name.orEmpty()
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(HugeIcons.ArrowLeft01, contentDescription = "Back")
            }
        },
        title = {
            Surface(
                onClick = {
                    if (conversation.messageNodes.isNotEmpty()) {
                        titleState.open(conversation.title)
                    } else {
                        toaster.show(
                            editTitleWarning,
                            type = ToastType.Warning
                        )
                    }
                },
                color = Color.Transparent,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val titleText = if (groupChatConfig.isGroupChat) {
                        val base = conversation.title.ifBlank { stringResource(R.string.chat_page_new_chat) }
                        "$base (${groupChatConfig.enabledParticipants.size})"
                    } else {
                        conversation.title.ifBlank { stringResource(R.string.chat_page_new_chat) }
                    }
                    Text(
                        text = titleText,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = { showMoreMenu = true }) {
                Icon(HugeIcons.MoreVertical, contentDescription = "More")
            }
            DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false }
            ) {
                if (onOpenSettings != null) {
                    DropdownMenuItem(
                        text = { Text("聊天设置") },
                        leadingIcon = { Icon(HugeIcons.Settings03, null) },
                        onClick = {
                            showMoreMenu = false
                            onOpenSettings()
                        }
                    )
                }
                if (onDeleteConversation != null) {
                    DropdownMenuItem(
                        text = { Text("删除聊天", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(HugeIcons.Delete01, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMoreMenu = false
                            onDeleteConversation()
                        }
                    )
                }
            }
        },
    )

    titleState.EditStateContent { title, onUpdate ->
        AlertDialog(
            onDismissRequest = { titleState.dismiss() },
            title = { Text(stringResource(R.string.chat_page_edit_title)) },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { titleState.confirm() }) {
                    Text(stringResource(R.string.chat_page_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { titleState.dismiss() }) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }
}
