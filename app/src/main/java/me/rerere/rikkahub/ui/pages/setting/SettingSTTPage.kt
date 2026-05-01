package me.rerere.rikkahub.ui.pages.setting

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.DragDropHorizontal
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Tools
import me.rerere.hugeicons.stroke.Delete01
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.DEFAULT_STT_PROVIDER_ID
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.pages.setting.components.STTProviderConfigure
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import me.rerere.stt.provider.SttProviderSetting
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun SettingSTTPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var editingProvider by remember { mutableStateOf<SttProviderSetting?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(R.string.setting_stt_page_title))
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    AddSTTProviderButton {
                        vm.updateSettings(
                            settings.copy(
                                sttProviders = listOf(it) + settings.sttProviders
                            )
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val newProviders = settings.sttProviders.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            vm.updateSettings(settings.copy(sttProviders = newProviders))
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = lazyListState
        ) {
            items(settings.sttProviders, key = { it.id }) { provider ->
                ReorderableItem(
                    state = reorderableState,
                    key = provider.id
                ) { isDragging ->
                    STTProviderItem(
                        modifier = Modifier
                            .scale(if (isDragging) 0.95f else 1f)
                            .fillMaxWidth(),
                        provider = provider,
                        dragHandle = {
                            val haptic = LocalHapticFeedback.current
                            IconButton(
                                onClick = {},
                                modifier = Modifier
                                    .longPressDraggableHandle(
                                        onDragStarted = {
                                            haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                        },
                                        onDragStopped = {
                                            haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                        }
                                    )
                            ) {
                                Icon(
                                    imageVector = HugeIcons.DragDropHorizontal,
                                    contentDescription = null
                                )
                            }
                        },
                        isSelected = settings.selectedSttProviderId == provider.id,
                        onSelect = {
                            vm.updateSettings(settings.copy(selectedSttProviderId = provider.id))
                        },
                        onEdit = {
                            editingProvider = provider
                        },
                        onDelete = {
                            val newProviders = settings.sttProviders - provider
                            val newSelectedId =
                                if (settings.selectedSttProviderId == provider.id) DEFAULT_STT_PROVIDER_ID else settings.selectedSttProviderId
                            vm.updateSettings(
                                settings.copy(
                                    sttProviders = newProviders,
                                    selectedSttProviderId = newSelectedId
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    // Edit STT Provider Bottom Sheet
    editingProvider?.let { provider ->
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var currentProvider by remember(provider) { mutableStateOf(provider) }

        ModalBottomSheet(
            onDismissRequest = {
                editingProvider = null
            },
            sheetState = bottomSheetState,
            dragHandle = {
                BottomSheetDefaults.DragHandle()
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .fillMaxHeight(0.8f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.setting_stt_page_edit_provider),
                    style = MaterialTheme.typography.headlineSmall
                )

                STTProviderConfigure(
                    setting = currentProvider,
                    onValueChange = { newState ->
                        currentProvider = newState
                    },
                    modifier = Modifier.weight(1f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            editingProvider = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }

                    TextButton(
                        onClick = {
                            val newProviders = settings.sttProviders.map {
                                if (it.id == provider.id) currentProvider else it
                            }
                            vm.updateSettings(settings.copy(sttProviders = newProviders))
                            editingProvider = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.chat_page_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSTTProviderButton(onAdd: (SttProviderSetting) -> Unit) {
    var showBottomSheet by remember { mutableStateOf(false) }
    var currentProvider: SttProviderSetting by remember { mutableStateOf(SttProviderSetting.OpenAIWhisper()) }

    IconButton(
        onClick = {
            currentProvider = SttProviderSetting.OpenAIWhisper()
            showBottomSheet = true
        }
    ) {
        Icon(HugeIcons.Add01, stringResource(R.string.setting_stt_page_add_provider_content_description))
    }

    if (showBottomSheet) {
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                showBottomSheet = false
            },
            sheetState = bottomSheetState,
            dragHandle = {
                BottomSheetDefaults.DragHandle()
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .fillMaxHeight(0.8f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.setting_stt_page_add_provider),
                    style = MaterialTheme.typography.headlineSmall
                )

                STTProviderConfigure(
                    setting = currentProvider,
                    onValueChange = { newState ->
                        currentProvider = newState
                    },
                    modifier = Modifier.weight(1f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            showBottomSheet = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }

                    TextButton(
                        onClick = {
                            onAdd(currentProvider)
                            showBottomSheet = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.setting_stt_page_add))
                    }
                }
            }
        }
    }
}

@Composable
private fun STTProviderItem(
    provider: SttProviderSetting,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    dragHandle: @Composable () -> Unit,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDropdownMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                CustomColors.listItemColors.containerColor
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AutoAIIcon(
                    name = provider.name,
                    modifier = Modifier.size(32.dp)
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )

                    Text(
                        text = when (provider) {
                            is SttProviderSetting.OpenAIWhisper -> stringResource(R.string.setting_stt_page_provider_openai)
                            is SttProviderSetting.Gemini -> stringResource(R.string.setting_stt_page_provider_gemini)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                RadioButton(
                    selected = isSelected,
                    onClick = onSelect
                )

                dragHandle()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelected) {
                    Tag(type = TagType.SUCCESS) {
                        Text(stringResource(R.string.setting_stt_page_selected))
                    }
                }

                IconButton(
                    onClick = { showDropdownMenu = true }
                ) {
                    Icon(
                        imageVector = HugeIcons.Tools,
                        contentDescription = stringResource(R.string.setting_stt_page_more_options_content_description)
                    )
                    DropdownMenu(
                        expanded = showDropdownMenu,
                        onDismissRequest = { showDropdownMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            onClick = {
                                showDropdownMenu = false
                                onEdit()
                            },
                            leadingIcon = {
                                Icon(HugeIcons.PencilEdit01, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = {
                                showDropdownMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(HugeIcons.Delete01, contentDescription = null)
                            },
                            enabled = provider.id != DEFAULT_STT_PROVIDER_ID
                        )
                    }
                }
            }
        }
    }
}
