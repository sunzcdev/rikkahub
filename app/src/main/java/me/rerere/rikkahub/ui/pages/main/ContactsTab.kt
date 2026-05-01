package me.rerere.rikkahub.ui.pages.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.LanguageCircle
import me.rerere.hugeicons.stroke.Search01
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.ai.CreateGroupChatDialog
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.rememberUserSettingsState
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
fun ContactsTab(modifier: Modifier = Modifier) {
    val navController = LocalNavController.current
    val settingsState by rememberUserSettingsState()
    val settingsStore: SettingsStore = koinInject()
    val chatService: me.rerere.rikkahub.service.ChatService = koinInject()
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var showCreateGroupChat by rememberSaveable { mutableStateOf(false) }

    val filteredAssistants = remember(settingsState.assistants, searchQuery) {
        if (searchQuery.isBlank()) settingsState.assistants
        else settingsState.assistants.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Search bar
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text("搜索") },
            leadingIcon = {
                Icon(HugeIcons.Search01, contentDescription = null)
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
            ),
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            // 新建群聊
            item(key = "new_group_chat") {
                ContactsRow(
                    icon = {
                        Icon(
                            HugeIcons.Copy01,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    },
                    iconBg = MaterialTheme.colorScheme.primaryContainer,
                    text = "新建群聊",
                    onClick = { showCreateGroupChat = true }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                )
            }

            // 收藏
            item(key = "favorites") {
                ContactsRow(
                    icon = {
                        Icon(
                            HugeIcons.InLove,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    },
                    iconBg = MaterialTheme.colorScheme.secondaryContainer,
                    text = "收藏",
                    onClick = { navController.navigate(Screen.Favorite) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                )
            }

            // AI 工具
            item(key = "ai_translator") {
                ContactsRow(
                    icon = {
                        Icon(
                            HugeIcons.LanguageCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    },
                    iconBg = MaterialTheme.colorScheme.tertiaryContainer,
                    text = "AI 翻译",
                    onClick = { navController.navigate(Screen.Translator) }
                )
            }
            item(key = "ai_image_gen") {
                ContactsRow(
                    icon = {
                        Icon(
                            HugeIcons.Image02,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    },
                    iconBg = MaterialTheme.colorScheme.tertiaryContainer,
                    text = "AI 绘图",
                    onClick = { navController.navigate(Screen.ImageGen) }
                )
            }

            // Section header before assistant list
            item(key = "section_header") {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                )
            }

            // Assistants
            items(filteredAssistants, key = { it.id }) { assistant ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UIAvatar(
                        name = assistant.name,
                        value = assistant.avatar,
                        onClick = {
                            navController.navigate(
                                Screen.AssistantDetail(id = assistant.id.toString())
                            )
                        },
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = assistant.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                scope.launch {
                                    settingsStore.updateAssistant(assistant.id)
                                    navigateToChatPage(navController)
                                }
                            },
                    )
                }
            }
        }
    }

    if (showCreateGroupChat) {
        CreateGroupChatDialog(
            assistants = settingsState.assistants,
            onDismiss = { showCreateGroupChat = false },
            onConfirm = { selectedIds ->
                showCreateGroupChat = false
                scope.launch {
                    val newId = chatService.createGroupChatConversation(selectedIds)
                    navigateToChatPage(navController, chatId = newId)
                }
            }
        )
    }
}

@Composable
private fun ContactsRow(
    text: String,
    onClick: () -> Unit,
    avatar: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    iconBg: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (avatar != null) {
            avatar()
        } else if (icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
