package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.DragDropVertical
import me.rerere.hugeicons.stroke.UserAdd01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.GroupChatConfig
import me.rerere.rikkahub.data.model.GroupChatParticipant
import me.rerere.rikkahub.data.model.SpeakingOrder
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSettingsBottomSheet(
    settings: Settings,
    currentAssistant: Assistant,
    conversationId: Uuid,
    groupChatConfig: GroupChatConfig,
    onDismiss: () -> Unit,
    onUpdateConfig: (GroupChatConfig) -> Unit,
    onNavigateToSearch: (conversationId: Uuid) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val navController = LocalNavController.current
    val minParticipantsWarning = stringResource(R.string.group_chat_min_participants_warning)
    val context = LocalContext.current

    val isGroupChat = groupChatConfig.isValid()

    var participants by remember(groupChatConfig) {
        mutableStateOf(
            if (isGroupChat) {
                groupChatConfig.participants.sortedBy { it.order }
            } else {
                listOf(
                    GroupChatParticipant(
                        id = Uuid.random(),
                        assistantId = currentAssistant.id,
                        order = 0,
                        enabled = true
                    )
                )
            }
        )
    }

    var selectedSpeakingOrder by remember(groupChatConfig) {
        mutableStateOf(groupChatConfig.speakingOrder)
    }

    var customSystemPrompt by remember(groupChatConfig) {
        mutableStateOf(groupChatConfig.groupSystemPrompt ?: "")
    }

    var autoDiscussRounds by remember(groupChatConfig) {
        mutableIntStateOf(if (groupChatConfig.autoDiscussRounds > 0) groupChatConfig.autoDiscussRounds else 5)
    }

    var showAddParticipantSheet by remember { mutableStateOf(false) }
    var showRoundsPicker by remember { mutableStateOf(false) }

    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            participants = participants.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }.mapIndexed { index, participant ->
                participant.copy(order = index)
            }
        }
    )

    fun getAssistantById(id: Uuid): Assistant? =
        settings.assistants.find { it.id == id }

    val participantLabels: Map<Uuid, String> = remember(participants) {
        participants.associate { participant ->
            val assistant = getAssistantById(participant.assistantId)
            val label = participant.displayName
                ?: assistant?.name?.ifBlank { context.getString(R.string.assistant_page_default_assistant) }
                ?: "Participant ${participant.order + 1}"
            participant.id to label
        }
    }

    fun saveChanges() {
        val enabledCount = participants.count { it.enabled }

        if (isGroupChat && enabledCount < 2) {
            toaster.show(minParticipantsWarning, type = com.dokar.sonner.ToastType.Warning)
            return
        }

        val newIsGroupChat = enabledCount >= 2
        val newConfig = GroupChatConfig(
            isGroupChat = newIsGroupChat,
            participants = participants,
            speakingOrder = selectedSpeakingOrder,
            groupSystemPrompt = customSystemPrompt.takeIf { it.isNotBlank() },
            autoDiscussEnabled = groupChatConfig.autoDiscussEnabled,
            autoDiscussRounds = autoDiscussRounds,
        )
        onUpdateConfig(newConfig)
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        scope.launch { sheetState.expand() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = HugeIcons.Settings03,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (isGroupChat) {
                        stringResource(R.string.group_chat_settings)
                    } else {
                        stringResource(R.string.chat_settings)
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SettingSection(
                        title = stringResource(R.string.group_chat_manage_participants),
                        icon = HugeIcons.UserAdd01
                    )

                    Spacer(Modifier.height(8.dp))

                    ParticipantListCard(
                        participants = participants,
                        getAssistantById = ::getAssistantById,
                        participantLabels = participantLabels,
                        reorderableState = reorderableState,
                        isGroupChat = isGroupChat,
                        onAddClick = { showAddParticipantSheet = true },
                        onParticipantChange = { updatedParticipants ->
                            participants = updatedParticipants
                        },
                        onNavigateToAssistantDetail = { assistantId ->
                            navController.navigate(
                                me.rerere.rikkahub.Screen.AssistantDetail(
                                    assistantId.toString()
                                )
                            )
                        }
                    )
                }

                if (participants.size >= 2) {
                    item {
                        SettingSection(
                            title = stringResource(R.string.group_chat_speaking_order),
                            icon = HugeIcons.Settings03
                        )

                        Spacer(Modifier.height(8.dp))

                        SpeakingOrderSelector(
                            selectedOrder = selectedSpeakingOrder,
                            onSelect = { selectedSpeakingOrder = it }
                        )
                    }

                    item {
                        SettingSection(
                            title = stringResource(R.string.auto_discuss_rounds),
                            icon = HugeIcons.Settings03
                        )

                        Spacer(Modifier.height(8.dp))

                        AutoDiscussRoundsSelector(
                            rounds = autoDiscussRounds,
                            onClick = { showRoundsPicker = true }
                        )
                    }

                    item {
                        SettingSection(
                            title = stringResource(R.string.group_chat_custom_prompt),
                            icon = HugeIcons.Settings03
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = customSystemPrompt,
                            onValueChange = { customSystemPrompt = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(stringResource(R.string.group_chat_custom_prompt_placeholder))
                            },
                            maxLines = 3,
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                }

                item {
                    SettingSection(
                        title = stringResource(R.string.chat_page_search_chats),
                        icon = HugeIcons.Search01
                    )

                    Spacer(Modifier.height(8.dp))

                    Surface(
                        onClick = {
                            scope.launch { sheetState.hide() }
                            onNavigateToSearch(conversationId)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = HugeIcons.Search01,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.search_chat_messages),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.weight(1f))
                            Icon(
                                imageVector = HugeIcons.Settings03,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.group_chat_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { saveChanges() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(stringResource(R.string.group_chat_save))
                }
            }
        }
    }

    if (showAddParticipantSheet) {
        AddParticipantSheet(
            settings = settings,
            currentAssistant = currentAssistant,
            existingParticipants = participants,
            onDismiss = { showAddParticipantSheet = false },
            onAddParticipant = { newParticipant ->
                val newOrder = if (participants.isEmpty()) 0 else participants.maxOf { it.order } + 1
                participants = participants + newParticipant.copy(order = newOrder)
                showAddParticipantSheet = false
            }
        )
    }

    if (showRoundsPicker) {
        AlertDialog(
            onDismissRequest = { showRoundsPicker = false },
            title = { Text(stringResource(R.string.auto_discuss_rounds)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.auto_discuss_rounds_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(listOf(1, 3, 5, 10, 20, 50)) { rounds ->
                            Surface(
                                onClick = {
                                    autoDiscussRounds = rounds
                                    showRoundsPicker = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = if (rounds == autoDiscussRounds) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = rounds == autoDiscussRounds,
                                        onClick = {
                                            autoDiscussRounds = rounds
                                            showRoundsPicker = false
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "$rounds ${stringResource(R.string.auto_discuss_rounds)}",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRoundsPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingSection(
    title: String,
    icon: ImageVector,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ParticipantListCard(
    participants: List<GroupChatParticipant>,
    getAssistantById: (Uuid) -> Assistant?,
    participantLabels: Map<Uuid, String>,
    reorderableState: sh.calvin.reorderable.ReorderableLazyListState,
    isGroupChat: Boolean,
    onAddClick: () -> Unit,
    onParticipantChange: (List<GroupChatParticipant>) -> Unit,
    onNavigateToAssistantDetail: (Uuid) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    ) {
        if (participants.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    HugeIcons.Add01,
                    null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.group_chat_no_participants),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
            ) {
                itemsIndexed(
                    items = participants,
                    key = { _, p -> p.id }
                ) { index, participant ->
                    val assistant = getAssistantById(participant.assistantId)

                    ReorderableItem(
                        state = reorderableState,
                        key = participant.id,
                    ) { isDragging: Boolean ->
                        ParticipantItem(
                            participant = participant,
                            assistant = assistant,
                            label = participantLabels[participant.id] ?: "?",
                            isDragging = isDragging,
                            isGroupChat = isGroupChat,
                            isLast = index == participants.lastIndex,
                            dragHandleModifier = if (isGroupChat) Modifier.longPressDraggableHandle() else Modifier,
                            onEnabledChange = { enabled ->
                                val updated = participants.map { p ->
                                    if (p.id == participant.id) {
                                        p.copy(enabled = enabled)
                                    } else p
                                }
                                onParticipantChange(updated)
                            },
                            onRemove = {
                                val updated = participants.filter { p ->
                                    p.id != participant.id
                                }.mapIndexed { i, p ->
                                    p.copy(order = i)
                                }
                                onParticipantChange(updated)
                            },
                            onAvatarClick = {
                                assistant?.let { onNavigateToAssistantDetail(it.id) }
                            }
                        )
                    }
                }
            }
        }

        Surface(
            onClick = onAddClick,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = HugeIcons.Add01,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.group_chat_add_participant),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ParticipantItem(
    participant: GroupChatParticipant,
    assistant: Assistant?,
    label: String,
    isDragging: Boolean,
    isGroupChat: Boolean,
    isLast: Boolean,
    dragHandleModifier: Modifier = Modifier,
    onEnabledChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onAvatarClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isDragging) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isGroupChat) {
                Icon(
                    imageVector = HugeIcons.DragDropVertical,
                    contentDescription = stringResource(R.string.reorder),
                    modifier = Modifier
                        .size(18.dp)
                        .then(dragHandleModifier),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            UIAvatar(
                name = assistant?.name ?: "?",
                value = assistant?.avatar ?: me.rerere.rikkahub.data.model.Avatar.Dummy,
                modifier = Modifier.size(32.dp),
                onClick = onAvatarClick
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (participant.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (!participant.enabled) {
                    Text(
                        text = stringResource(R.string.participant_disabled),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isGroupChat) {
                Checkbox(
                    checked = participant.enabled,
                    onCheckedChange = onEnabledChange
                )

                IconButton(
                    onClick = onRemove
                ) {
                    Icon(
                        HugeIcons.Cancel01,
                        stringResource(R.string.assistant_page_remove),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (!isLast) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .padding(horizontal = 56.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        )
    }
}

@Composable
private fun SpeakingOrderSelector(
    selectedOrder: SpeakingOrder,
    onSelect: (SpeakingOrder) -> Unit,
) {
    val orders = listOf(
        SpeakingOrder.Sequential to stringResource(R.string.group_chat_order_sequential),
        SpeakingOrder.Random to stringResource(R.string.group_chat_order_random),
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        orders.forEach { (order, label) ->
            Surface(
                onClick = { onSelect(order) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (order == selectedOrder) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = order == selectedOrder,
                        onClick = { onSelect(order) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoDiscussRoundsSelector(
    rounds: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "$rounds ${stringResource(R.string.auto_discuss_rounds)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = HugeIcons.Settings03,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
