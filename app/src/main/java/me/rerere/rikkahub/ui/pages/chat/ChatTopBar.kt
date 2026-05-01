package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.hugeicons.stroke.PlayCircle
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Stop
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState

@Composable
fun TopBar(
    settings: Settings,
    conversation: Conversation,
    drawerState: DrawerState,
    bigScreen: Boolean,
    previewMode: Boolean,
    onClickMenu: () -> Unit,
    onNewChat: () -> Unit,
    onUpdateTitle: (String) -> Unit,
    onOpenGroupChatSettings: (() -> Unit)? = null,
    onOpenAssistantSettings: (() -> Unit)? = null,
    onCreateGroupChat: (() -> Unit)? = null,
    onStartAutoDiscuss: (() -> Unit)? = null,
    isAutoDiscussRunning: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val titleState = useEditState<String> {
        onUpdateTitle(it)
    }
    val hasValidGroupChat = remember(conversation) {
        conversation.groupChatConfig.isValid()
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        navigationIcon = {
            if (!bigScreen) {
                IconButton(
                    onClick = {
                        scope.launch { drawerState.open() }
                    }
                ) {
                    Icon(HugeIcons.Menu03, "Messages")
                }
            }
        },
        title = {
            val editTitleWarning = stringResource(R.string.chat_page_edit_title_warning)
            Surface(
                onClick = {
                    if (conversation.messageNodes.isNotEmpty()) {
                        titleState.open(conversation.title)
                    } else {
                        toaster.show(editTitleWarning, type = ToastType.Warning)
                    }
                },
                color = Color.Transparent,
            ) {
                Column {
                    if (hasValidGroupChat) {
                        val config = conversation.groupChatConfig
                        val enabledParticipants = config.enabledParticipants
                        val participantNames = enabledParticipants.take(3).joinToString(", ") { p ->
                            p.displayName ?: settings.assistants.find { it.id == p.assistantId }?.name ?: "Unknown"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = conversation.title.ifBlank { stringResource(R.string.group_chat_title) },
                                maxLines = 1,
                                style = MaterialTheme.typography.bodyMedium,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = "${enabledParticipants.size}人",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = participantNames,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            color = LocalContentColor.current.copy(0.65f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                            )
                        )
                    } else {
                        val assistant = settings.getCurrentAssistant()
                        val model = settings.getCurrentChatModel()
                        val provider = model?.findProvider(providers = settings.providers, checkOverwrite = false)
                        Text(
                            text = conversation.title.ifBlank { stringResource(R.string.chat_page_new_chat) },
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyMedium,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (model != null && provider != null) {
                            Text(
                                text = "${assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) }} / ${model.displayName} (${provider.name})",
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                color = LocalContentColor.current.copy(0.65f),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                )
                            )
                        }
                    }
                }
            }
        },
        actions = {
            // Settings button — always visible
            IconButton(
                onClick = {
                    if (hasValidGroupChat) {
                        onOpenGroupChatSettings?.invoke()
                    } else {
                        onOpenAssistantSettings?.invoke()
                    }
                }
            ) {
                Icon(HugeIcons.Settings03, "Settings")
            }

            if (hasValidGroupChat && onStartAutoDiscuss != null) {
                IconButton(
                    onClick = onStartAutoDiscuss
                ) {
                    if (isAutoDiscussRunning) {
                        Icon(
                            HugeIcons.Stop,
                            "Stop Auto Discuss",
                            tint = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Icon(
                            HugeIcons.PlayCircle,
                            "Start Auto Discuss",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            IconButton(
                onClick = {
                    onClickMenu()
                }
            ) {
                Icon(if (previewMode) HugeIcons.Cancel01 else HugeIcons.LeftToRightListBullet, "Chat Options")
            }

            // New Chat button with dropdown menu
            var showNewChatMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { showNewChatMenu = true }
                ) {
                    Icon(HugeIcons.MessageAdd01, "New Chat")
                }
                DropdownMenu(
                    expanded = showNewChatMenu,
                    onDismissRequest = { showNewChatMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.group_chat_new_single_chat)) },
                        onClick = {
                            showNewChatMenu = false
                            onNewChat()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.group_chat_new_group_chat)) },
                        onClick = {
                            showNewChatMenu = false
                            onCreateGroupChat?.invoke()
                        }
                    )
                }
            }
        },
    )
    titleState.EditStateContent { title, onUpdate ->
        AlertDialog(
            onDismissRequest = {
                titleState.dismiss()
            },
            title = {
                Text(stringResource(R.string.chat_page_edit_title))
            },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        titleState.confirm()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        titleState.dismiss()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }
}

