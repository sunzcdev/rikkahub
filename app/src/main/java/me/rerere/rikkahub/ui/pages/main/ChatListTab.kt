package me.rerere.rikkahub.ui.pages.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Notification03
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.jiji.JijiNotificationManager
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.rememberUserSettingsState
import me.rerere.rikkahub.ui.pages.chat.ConversationList
import me.rerere.rikkahub.ui.pages.chat.ConversationListVM
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListTab(modifier: Modifier = Modifier) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val listVm: ConversationListVM = koinViewModel(viewModelStoreOwner = activity)
    val conversationRepo: ConversationRepository = koinInject()
    val scope = rememberCoroutineScope()

    val conversations = listVm.conversations.collectAsLazyPagingItems()
    val conversationListState = rememberLazyListState(
        initialFirstVisibleItemIndex = listVm.scrollIndex,
        initialFirstVisibleItemScrollOffset = listVm.scrollOffset,
    )

    LaunchedEffect(conversationListState) {
        snapshotFlow {
            conversationListState.firstVisibleItemIndex to
                conversationListState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collectLatest { (index, offset) ->
                listVm.saveScrollPosition(index, offset)
            }
    }

    val settingsStore: SettingsStore = koinInject()
    var showPlusMenu by remember { mutableStateOf(false) }
    var showAddAssistantDialog by remember { mutableStateOf(false) }
    var newAssistantName by remember { mutableStateOf("") }
    val settingsState by rememberUserSettingsState()
    val nameExists = newAssistantName.isNotBlank() &&
        settingsState.assistants.any { it.name.equals(newAssistantName, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("RikkaHub")
                },
                actions = {
                    Box {
                        IconButton(onClick = { showPlusMenu = true }) {
                            Icon(HugeIcons.Add01, stringResource(R.string.add))
                        }
                        DropdownMenu(
                            expanded = showPlusMenu,
                            onDismissRequest = { showPlusMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("添加助手") },
                                leadingIcon = { Icon(HugeIcons.Add01, null) },
                                onClick = {
                                    showPlusMenu = false
                                    showAddAssistantDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_page_search_chats)) },
                                leadingIcon = { Icon(HugeIcons.Search01, null) },
                                onClick = {
                                    showPlusMenu = false
                                    navController.navigate(Screen.MessageSearch)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("设置") },
                                leadingIcon = { Icon(HugeIcons.Settings03, null) },
                                onClick = {
                                    showPlusMenu = false
                                    navController.navigate(Screen.Setting)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        containerColor = Color.Transparent,
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 唧唧未读提示
            val jijiNotificationManager: JijiNotificationManager = koinInject()
            val jijiUnreadCount = remember { mutableIntStateOf(jijiNotificationManager.getUnreadCount()) }
            val jijiLastMessage = remember { mutableStateOf(jijiNotificationManager.getLastMessagePreview() ?: "") }

            LaunchedEffect(Unit) {
                while (isActive) {
                    jijiUnreadCount.intValue = jijiNotificationManager.getUnreadCount()
                    jijiLastMessage.value = jijiNotificationManager.getLastMessagePreview() ?: ""
                    kotlinx.coroutines.delay(2000)
                }
            }

            if (jijiUnreadCount.intValue > 0) {
                JijiUnreadRow(
                    unreadCount = jijiUnreadCount.intValue,
                    lastMessage = jijiLastMessage.value,
                    onClick = {
                        jijiNotificationManager.clearUnread()
                        val convId = jijiNotificationManager.getConversationId()
                        if (convId != null) {
                            navigateToChatPage(navController, Uuid.parse(convId))
                        }
                    },
                )
            }

            ConversationList(
                current = null,
                conversations = conversations,
                conversationJobs = emptyList(),
                assistants = settingsState.assistants,
                listState = conversationListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onClick = { conversation ->
                    navigateToChatPage(navController, conversation.id)
                },
                onDelete = { conversation ->
                    scope.launch {
                        conversationRepo.deleteConversation(conversation)
                        conversations.refresh()
                    }
                },
                onRegenerateTitle = { conversation ->
                    // Title regeneration requires ChatService context — skip in list for now
                },
                onPin = { conversation ->
                    scope.launch {
                        conversationRepo.togglePinStatus(conversation.id)
                        conversations.refresh()
                    }
                },
                onMoveToAssistant = { conversation ->
                    // Move to assistant handled via bottom sheet if needed
                },
            )
        }
    }

    if (showAddAssistantDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddAssistantDialog = false
                newAssistantName = ""
            },
            title = { Text("新助手") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newAssistantName,
                        onValueChange = { newAssistantName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("输入助手名称") },
                        singleLine = true,
                        isError = nameExists,
                    )
                    if (nameExists) {
                        Text(
                            text = "助手名称已存在",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                    val assistant = Assistant(name = newAssistantName)
                    scope.launch {
                        settingsStore.update { settings ->
                            settings.copy(assistants = settings.assistants + assistant)
                        }
                    }
                    navController.navigate(Screen.AssistantDetail(id = assistant.id.toString()))
                    showAddAssistantDialog = false
                    newAssistantName = ""
                }) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = newAssistantName.isNotBlank() && !nameExists,
                    onClick = {
                    showAddAssistantDialog = false
                    newAssistantName = ""
                }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun JijiUnreadRow(
    unreadCount: Int,
    lastMessage: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = HugeIcons.Notification03,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "唧唧",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ) {
                        Text(
                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (lastMessage.isNotBlank()) {
                    Text(
                        text = lastMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
