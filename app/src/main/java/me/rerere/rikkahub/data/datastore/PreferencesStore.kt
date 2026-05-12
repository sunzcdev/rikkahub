package me.rerere.rikkahub.data.datastore

import android.content.Context
import me.rerere.common.android.Logging
import androidx.datastore.core.IOException
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.pebbletemplates.pebble.PebbleEngine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_COMPRESS_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_OCR_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_SUGGESTION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TITLE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TRANSLATION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.LEARNING_MODE_PROMPT
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV1Migration
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV2Migration
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV3Migration
import me.rerere.rikkahub.data.model.HardwareKeyConfig
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.ui.theme.PresetThemes
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.toMutableStateFlow
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchServiceOptions
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.stt.provider.SttProviderSetting
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.uuid.Uuid

private const val TAG = "PreferencesStore"

private val Context.settingsStore by preferencesDataStore(
    name = "settings",
    produceMigrations = { context ->
        listOf(
            PreferenceStoreV1Migration(),
            PreferenceStoreV2Migration(),
            PreferenceStoreV3Migration()
        )
    }
)

class SettingsStore(
    context: Context,
    scope: AppScope,
) : KoinComponent {
    companion object {
        // 版本号
        val VERSION = intPreferencesKey("data_version")

        // UI设置
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_ID = stringPreferencesKey("theme_id")
        val DISPLAY_SETTING = stringPreferencesKey("display_setting")
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")

        // 模型选择
        val ENABLE_WEB_SEARCH = booleanPreferencesKey("enable_web_search")
        val FAVORITE_MODELS = stringPreferencesKey("favorite_models")
        val SELECT_MODEL = stringPreferencesKey("chat_model")
        val TITLE_MODEL = stringPreferencesKey("title_model")
        val TRANSLATE_MODEL = stringPreferencesKey("translate_model")
        val SUGGESTION_MODEL = stringPreferencesKey("suggestion_model")
        val IMAGE_GENERATION_MODEL = stringPreferencesKey("image_generation_model")
        val TITLE_PROMPT = stringPreferencesKey("title_prompt")
        val TRANSLATION_PROMPT = stringPreferencesKey("translation_prompt")
        val TRANSLATE_THINKING_BUDGET = intPreferencesKey("translate_thinking_budget")
        val SUGGESTION_PROMPT = stringPreferencesKey("suggestion_prompt")
        val OCR_MODEL = stringPreferencesKey("ocr_model")
        val OCR_PROMPT = stringPreferencesKey("ocr_prompt")
        val COMPRESS_MODEL = stringPreferencesKey("compress_model")
        val COMPRESS_PROMPT = stringPreferencesKey("compress_prompt")

        // 提供商
        val PROVIDERS = stringPreferencesKey("providers")

        // 助手
        val SELECT_ASSISTANT = stringPreferencesKey("select_assistant")
        val ASSISTANTS = stringPreferencesKey("assistants")
        val ASSISTANT_TAGS = stringPreferencesKey("assistant_tags")

        // 搜索
        val SEARCH_SERVICES = stringPreferencesKey("search_services")
        val SEARCH_COMMON = stringPreferencesKey("search_common")
        val SEARCH_SELECTED = intPreferencesKey("search_selected")

        // MCP
        val MCP_SERVERS = stringPreferencesKey("mcp_servers")

        // WebDAV
        val WEBDAV_CONFIG = stringPreferencesKey("webdav_config")

        // S3
        val S3_CONFIG = stringPreferencesKey("s3_config")

        // TTS
        val TTS_PROVIDERS = stringPreferencesKey("tts_providers")
        val SELECTED_TTS_PROVIDER = stringPreferencesKey("selected_tts_provider")

        // STT
        val STT_PROVIDERS = stringPreferencesKey("stt_providers")
        val SELECTED_STT_PROVIDER = stringPreferencesKey("selected_stt_provider")

        // Web Server
        val WEB_SERVER_ENABLED = booleanPreferencesKey("web_server_enabled")
        val WEB_SERVER_PORT = intPreferencesKey("web_server_port")
        val WEB_SERVER_JWT_ENABLED = booleanPreferencesKey("web_server_jwt_enabled")
        val WEB_SERVER_ACCESS_PASSWORD = stringPreferencesKey("web_server_access_password")
        val WEB_SERVER_LOCALHOST_ONLY = booleanPreferencesKey("web_server_localhost_only")

        // 提示词注入
        val MODE_INJECTIONS = stringPreferencesKey("mode_injections")
        val LOREBOOKS = stringPreferencesKey("lorebooks")
        val QUICK_MESSAGES = stringPreferencesKey("quick_messages")

        // 备份提醒
        val BACKUP_REMINDER_CONFIG = stringPreferencesKey("backup_reminder_config")

        // 统计
        val LAUNCH_COUNT = intPreferencesKey("launch_count")

        // 赞助提醒
        val SPONSOR_ALERT_DISMISSED_AT = intPreferencesKey("sponsor_alert_dismissed_at")

        // 硬件桥
        val HARDWARE_KEYS = stringPreferencesKey("hardware_keys")

        // 旧字段，仅迁移使用
        @Deprecated("迁移到 hardwareKeys")
        private val AMAP_API_KEY = stringPreferencesKey("amap_api_key")
        @Deprecated("迁移到 hardwareKeys")
        private val OPEN_WEATHER_API_KEY = stringPreferencesKey("open_weather_api_key")
    }

    private val dataStore = context.settingsStore

    val settingsFlowRaw = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            Settings(
                enableWebSearch = preferences[ENABLE_WEB_SEARCH] == true,
                favoriteModels = preferences[FAVORITE_MODELS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                chatModelId = preferences[SELECT_MODEL]?.let { Uuid.parse(it) }
                    ?: DEFAULT_AUTO_MODEL_ID,
                titleModelId = preferences[TITLE_MODEL]?.let { Uuid.parse(it) }
                    ?: DEFAULT_AUTO_MODEL_ID,
                translateModeId = preferences[TRANSLATE_MODEL]?.let { Uuid.parse(it) }
                    ?: DEFAULT_AUTO_MODEL_ID,
                suggestionModelId = preferences[SUGGESTION_MODEL]?.let { Uuid.parse(it) }
                    ?: DEFAULT_AUTO_MODEL_ID,
                imageGenerationModelId = preferences[IMAGE_GENERATION_MODEL]?.let { Uuid.parse(it) } ?: Uuid.random(),
                titlePrompt = preferences[TITLE_PROMPT] ?: DEFAULT_TITLE_PROMPT,
                translatePrompt = preferences[TRANSLATION_PROMPT] ?: DEFAULT_TRANSLATION_PROMPT,
                translateThinkingBudget = preferences[TRANSLATE_THINKING_BUDGET] ?: 0,
                suggestionPrompt = preferences[SUGGESTION_PROMPT] ?: DEFAULT_SUGGESTION_PROMPT,
                ocrModelId = preferences[OCR_MODEL]?.let { Uuid.parse(it) } ?: Uuid.random(),
                ocrPrompt = preferences[OCR_PROMPT] ?: DEFAULT_OCR_PROMPT,
                compressModelId = preferences[COMPRESS_MODEL]?.let { Uuid.parse(it) } ?: DEFAULT_AUTO_MODEL_ID,
                compressPrompt = preferences[COMPRESS_PROMPT] ?: DEFAULT_COMPRESS_PROMPT,
                assistantId = preferences[SELECT_ASSISTANT]?.let { Uuid.parse(it) }
                    ?: DEFAULT_ASSISTANT_ID,
                assistantTags = preferences[ASSISTANT_TAGS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                providers = JsonInstant.decodeFromString(preferences[PROVIDERS] ?: "[]"),
                assistants = JsonInstant.decodeFromString(preferences[ASSISTANTS] ?: "[]"),
                dynamicColor = preferences[DYNAMIC_COLOR] != false,
                themeId = preferences[THEME_ID] ?: PresetThemes[0].id,
                developerMode = preferences[DEVELOPER_MODE] == true,
                displaySetting = JsonInstant.decodeFromString(preferences[DISPLAY_SETTING] ?: "{}"),
                searchServices = preferences[SEARCH_SERVICES]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: listOf(SearchServiceOptions.DEFAULT),
                searchCommonOptions = preferences[SEARCH_COMMON]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: SearchCommonOptions(),
                searchServiceSelected = preferences[SEARCH_SELECTED] ?: 0,
                mcpServers = preferences[MCP_SERVERS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                webDavConfig = preferences[WEBDAV_CONFIG]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: WebDavConfig(),
                s3Config = preferences[S3_CONFIG]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: S3Config(),
                ttsProviders = preferences[TTS_PROVIDERS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                selectedTTSProviderId = preferences[SELECTED_TTS_PROVIDER]?.let { Uuid.parse(it) }
                    ?: DEFAULT_SYSTEM_TTS_ID,
                sttProviders = preferences[STT_PROVIDERS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                selectedSttProviderId = preferences[SELECTED_STT_PROVIDER]?.let { Uuid.parse(it) }
                    ?: DEFAULT_STT_PROVIDER_ID,
                modeInjections = preferences[MODE_INJECTIONS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                lorebooks = preferences[LOREBOOKS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                quickMessages = preferences[QUICK_MESSAGES]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                webServerEnabled = preferences[WEB_SERVER_ENABLED] == true,
                webServerPort = preferences[WEB_SERVER_PORT] ?: 8080,
                webServerJwtEnabled = preferences[WEB_SERVER_JWT_ENABLED] == true,
                webServerAccessPassword = preferences[WEB_SERVER_ACCESS_PASSWORD] ?: "",
                webServerLocalhostOnly = preferences[WEB_SERVER_LOCALHOST_ONLY] == true,
                backupReminderConfig = preferences[BACKUP_REMINDER_CONFIG]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: BackupReminderConfig(),
                launchCount = preferences[LAUNCH_COUNT] ?: 0,
                sponsorAlertDismissedAt = preferences[SPONSOR_ALERT_DISMISSED_AT] ?: 0,
                hardwareKeys = preferences[HARDWARE_KEYS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: run {
                    // 迁移旧字段
                    val oldAmap = preferences[AMAP_API_KEY] ?: ""
                    val oldWeather = preferences[OPEN_WEATHER_API_KEY] ?: ""
                    buildList {
                        if (oldAmap.isNotBlank()) add(HardwareKeyConfig.Amap(apiKey = oldAmap))
                        if (oldWeather.isNotBlank()) add(HardwareKeyConfig.OpenWeather(apiKey = oldWeather))
                    }
                },
            )
        }
        .map {
            var providers = it.providers.ifEmpty { DEFAULT_PROVIDERS }.toMutableList()
            DEFAULT_PROVIDERS.forEach { defaultProvider ->
                if (providers.none { it.id == defaultProvider.id }) {
                    providers.add(defaultProvider.copyProvider())
                }
            }
            providers = providers.map { provider ->
                val defaultProvider = DEFAULT_PROVIDERS.find { it.id == provider.id }
                if (defaultProvider != null) {
                    provider.copyProvider(
                        builtIn = defaultProvider.builtIn,
                        description = defaultProvider.description,
                        shortDescription = defaultProvider.shortDescription,
                    )
                } else provider
            }.toMutableList()
            val assistants = it.assistants.ifEmpty { DEFAULT_ASSISTANTS }.toMutableList()
            DEFAULT_ASSISTANTS.forEach { defaultAssistant ->
                if (assistants.none { it.id == defaultAssistant.id }) {
                    assistants.add(defaultAssistant.copy())
                }
            }
            val ttsProviders = it.ttsProviders.ifEmpty { DEFAULT_TTS_PROVIDERS }.toMutableList()
            DEFAULT_TTS_PROVIDERS.forEach { defaultTTSProvider ->
                if (ttsProviders.none { provider -> provider.id == defaultTTSProvider.id }) {
                    ttsProviders.add(defaultTTSProvider.copyProvider())
                }
            }
            it.copy(
                providers = providers,
                assistants = assistants,
                ttsProviders = ttsProviders
            )
        }
        .map { settings ->
            // 去重并清理无效引用
            val validMcpServerIds = settings.mcpServers.map { it.id }.toSet()
            val validModeInjectionIds = settings.modeInjections.map { it.id }.toSet()
            val validLorebookIds = settings.lorebooks.map { it.id }.toSet()
            val validQuickMessageIds = settings.quickMessages.map { it.id }.toSet()
            settings.copy(
                providers = settings.providers.distinctBy { it.id }.map { provider ->
                    when (provider) {
                        is ProviderSetting.OpenAI -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                        )

                        is ProviderSetting.Google -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                        )

                        is ProviderSetting.Claude -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                        )
                    }
                },
                assistants = settings.assistants
                    .filter { it.id != Uuid.parse("3d47790c-c415-4b90-9388-751128adb0a0") }
                    .distinctBy { it.id }.map { assistant ->
                    assistant.copy(
                        // 过滤掉不存在的 MCP 服务器 ID
                        mcpServers = assistant.mcpServers.filter { serverId ->
                            serverId in validMcpServerIds
                        }.toSet(),
                        // 过滤掉不存在的模式注入 ID
                        modeInjectionIds = assistant.modeInjectionIds.filter { id ->
                            id in validModeInjectionIds
                        }.toSet(),
                        // 过滤掉不存在的 Lorebook ID
                        lorebookIds = assistant.lorebookIds.filter { id ->
                            id in validLorebookIds
                        }.toSet(),
                        // 过滤掉不存在的快捷消息 ID
                        quickMessageIds = assistant.quickMessageIds.filter { id ->
                            id in validQuickMessageIds
                        }.toSet()
                    )
                },
                ttsProviders = settings.ttsProviders.distinctBy { it.id },
                favoriteModels = settings.favoriteModels.filter { uuid ->
                    settings.providers.flatMap { it.models }.any { it.id == uuid }
                },
                modeInjections = settings.modeInjections.distinctBy { it.id },
                lorebooks = settings.lorebooks.distinctBy { it.id },
                quickMessages = settings.quickMessages.distinctBy { it.id },
            )
        }
        .onEach {
            get<PebbleEngine>().templateCache.invalidateAll()
        }

    val settingsFlow = settingsFlowRaw
        .distinctUntilChanged()
        .toMutableStateFlow(scope, Settings.dummy())

    suspend fun update(settings: Settings) {
        if(settings.init) {
            Logging.w(TAG, "Cannot update dummy settings")
            return
        }
        settingsFlow.value = settings
        dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR] = settings.dynamicColor
            preferences[THEME_ID] = settings.themeId
            preferences[DEVELOPER_MODE] = settings.developerMode
            preferences[DISPLAY_SETTING] = JsonInstant.encodeToString(settings.displaySetting)

            preferences[ENABLE_WEB_SEARCH] = settings.enableWebSearch
            preferences[FAVORITE_MODELS] = JsonInstant.encodeToString(settings.favoriteModels)
            preferences[SELECT_MODEL] = settings.chatModelId.toString()
            preferences[TITLE_MODEL] = settings.titleModelId.toString()
            preferences[TRANSLATE_MODEL] = settings.translateModeId.toString()
            preferences[SUGGESTION_MODEL] = settings.suggestionModelId.toString()
            preferences[IMAGE_GENERATION_MODEL] = settings.imageGenerationModelId.toString()
            preferences[TITLE_PROMPT] = settings.titlePrompt
            preferences[TRANSLATION_PROMPT] = settings.translatePrompt
            preferences[TRANSLATE_THINKING_BUDGET] = settings.translateThinkingBudget
            preferences[SUGGESTION_PROMPT] = settings.suggestionPrompt
            preferences[OCR_MODEL] = settings.ocrModelId.toString()
            preferences[OCR_PROMPT] = settings.ocrPrompt
            preferences[COMPRESS_MODEL] = settings.compressModelId.toString()
            preferences[COMPRESS_PROMPT] = settings.compressPrompt

            preferences[PROVIDERS] = JsonInstant.encodeToString(settings.providers)

            preferences[ASSISTANTS] = JsonInstant.encodeToString(settings.assistants)
            preferences[SELECT_ASSISTANT] = settings.assistantId.toString()
            preferences[ASSISTANT_TAGS] = JsonInstant.encodeToString(settings.assistantTags)

            preferences[SEARCH_SERVICES] = JsonInstant.encodeToString(settings.searchServices)
            preferences[SEARCH_COMMON] = JsonInstant.encodeToString(settings.searchCommonOptions)
            preferences[SEARCH_SELECTED] = settings.searchServiceSelected.coerceIn(0, settings.searchServices.size - 1)

            preferences[MCP_SERVERS] = JsonInstant.encodeToString(settings.mcpServers)
            preferences[WEBDAV_CONFIG] = JsonInstant.encodeToString(settings.webDavConfig)
            preferences[S3_CONFIG] = JsonInstant.encodeToString(settings.s3Config)
            preferences[TTS_PROVIDERS] = JsonInstant.encodeToString(settings.ttsProviders)
            settings.selectedTTSProviderId?.let {
                preferences[SELECTED_TTS_PROVIDER] = it.toString()
            } ?: preferences.remove(SELECTED_TTS_PROVIDER)
            preferences[STT_PROVIDERS] = JsonInstant.encodeToString(settings.sttProviders)
            settings.selectedSttProviderId?.let {
                preferences[SELECTED_STT_PROVIDER] = it.toString()
            } ?: preferences.remove(SELECTED_STT_PROVIDER)
            preferences[MODE_INJECTIONS] = JsonInstant.encodeToString(settings.modeInjections)
            preferences[LOREBOOKS] = JsonInstant.encodeToString(settings.lorebooks)
            preferences[QUICK_MESSAGES] = JsonInstant.encodeToString(settings.quickMessages)
            preferences[WEB_SERVER_ENABLED] = settings.webServerEnabled
            preferences[WEB_SERVER_PORT] = settings.webServerPort
            preferences[WEB_SERVER_JWT_ENABLED] = settings.webServerJwtEnabled
            preferences[WEB_SERVER_ACCESS_PASSWORD] = settings.webServerAccessPassword
            preferences[WEB_SERVER_LOCALHOST_ONLY] = settings.webServerLocalhostOnly
            preferences[BACKUP_REMINDER_CONFIG] = JsonInstant.encodeToString(settings.backupReminderConfig)
            preferences[LAUNCH_COUNT] = settings.launchCount
            preferences[SPONSOR_ALERT_DISMISSED_AT] = settings.sponsorAlertDismissedAt
            preferences[HARDWARE_KEYS] = JsonInstant.encodeToString(settings.hardwareKeys)
        }
    }

    suspend fun update(fn: (Settings) -> Settings) {
        update(fn(settingsFlow.value))
    }

    suspend fun updateAssistant(assistantId: Uuid) {
        dataStore.edit { preferences ->
            preferences[SELECT_ASSISTANT] = assistantId.toString()
        }
    }

    suspend fun updateAssistantModel(assistantId: Uuid, modelId: Uuid) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(chatModelId = modelId)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantReasoningLevel(assistantId: Uuid, reasoningLevel: ReasoningLevel) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(reasoningLevel = reasoningLevel)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantMcpServers(assistantId: Uuid, mcpServers: Set<Uuid>) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(mcpServers = mcpServers)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantInjections(
        assistantId: Uuid,
        modeInjectionIds: Set<Uuid>,
        lorebookIds: Set<Uuid>,
        quickMessageIds: Set<Uuid> = emptySet(),
    ) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(
                            modeInjectionIds = modeInjectionIds,
                            lorebookIds = lorebookIds,
                            quickMessageIds = quickMessageIds,
                        )
                    } else {
                        assistant
                    }
                }
            )
        }
    }
}

@Serializable
data class Settings(
    @Transient
    val init: Boolean = false,
    val dynamicColor: Boolean = true,
    val themeId: String = PresetThemes[0].id,
    val developerMode: Boolean = false,
    val displaySetting: DisplaySetting = DisplaySetting(),
    val enableWebSearch: Boolean = false,
    val favoriteModels: List<Uuid> = emptyList(),
    val chatModelId: Uuid = Uuid.random(),
    val titleModelId: Uuid = Uuid.random(),
    val imageGenerationModelId: Uuid = Uuid.random(),
    val titlePrompt: String = DEFAULT_TITLE_PROMPT,
    val translateModeId: Uuid = Uuid.random(),
    val translatePrompt: String = DEFAULT_TRANSLATION_PROMPT,
    val translateThinkingBudget: Int = 0,
    val suggestionModelId: Uuid = Uuid.random(),
    val suggestionPrompt: String = DEFAULT_SUGGESTION_PROMPT,
    val ocrModelId: Uuid = Uuid.random(),
    val ocrPrompt: String = DEFAULT_OCR_PROMPT,
    val compressModelId: Uuid = Uuid.random(),
    val compressPrompt: String = DEFAULT_COMPRESS_PROMPT,
    val assistantId: Uuid = DEFAULT_ASSISTANT_ID,
    val providers: List<ProviderSetting> = DEFAULT_PROVIDERS,
    val assistants: List<Assistant> = DEFAULT_ASSISTANTS,
    val assistantTags: List<Tag> = emptyList(),
    val searchServices: List<SearchServiceOptions> = listOf(SearchServiceOptions.DEFAULT),
    val searchCommonOptions: SearchCommonOptions = SearchCommonOptions(),
    val searchServiceSelected: Int = 0,
    val mcpServers: List<McpServerConfig> = emptyList(),
    val webDavConfig: WebDavConfig = WebDavConfig(),
    val s3Config: S3Config = S3Config(),
    val ttsProviders: List<TTSProviderSetting> = DEFAULT_TTS_PROVIDERS,
    val selectedTTSProviderId: Uuid = DEFAULT_SYSTEM_TTS_ID,
    val sttProviders: List<SttProviderSetting> = DEFAULT_STT_PROVIDERS,
    val selectedSttProviderId: Uuid = DEFAULT_STT_PROVIDER_ID,
    val modeInjections: List<PromptInjection.ModeInjection> = DEFAULT_MODE_INJECTIONS,
    val lorebooks: List<Lorebook> = emptyList(),
    val quickMessages: List<QuickMessage> = emptyList(),
    val webServerEnabled: Boolean = false,
    val webServerPort: Int = 8080,
    val webServerJwtEnabled: Boolean = false,
    val webServerAccessPassword: String = "",
    val webServerLocalhostOnly: Boolean = false,
    val backupReminderConfig: BackupReminderConfig = BackupReminderConfig(),
    val launchCount: Int = 0,
    val sponsorAlertDismissedAt: Int = 0,
    val hardwareKeys: List<HardwareKeyConfig> = emptyList(),
) {
    companion object {
        // 构造一个用于初始化的settings, 但它不能用于保存，防止使用初始值存储
        fun dummy() = Settings(init = true)
    }
}

@Serializable
enum class ChatFontFamily {
    @SerialName("default")
    DEFAULT,
    @SerialName("serif")
    SERIF,
    @SerialName("monospace")
    MONOSPACE,
}

@Serializable
data class DisplaySetting(
    val userAvatar: Avatar = Avatar.Dummy,
    val userNickname: String = "",
    val useAppIconStyleLoadingIndicator: Boolean = true,
    val showUserAvatar: Boolean = true,
    val showAssistantBubble: Boolean = false,
    val showModelIcon: Boolean = true,
    val showModelName: Boolean = true,
    val showDateBelowName: Boolean = false,
    val showTokenUsage: Boolean = true,
    val showThinkingContent: Boolean = true,
    val autoCloseThinking: Boolean = true,
    val showUpdates: Boolean = true,
    val showMessageJumper: Boolean = true,
    val messageJumperOnLeft: Boolean = false,
    val fontSizeRatio: Float = 1.0f,
    val enableMessageGenerationHapticEffect: Boolean = false,
    val skipCropImage: Boolean = false,
    val enableNotificationOnMessageGeneration: Boolean = false,
    val enableLiveUpdateNotification: Boolean = false,
    val codeBlockAutoWrap: Boolean = false,
    val codeBlockAutoCollapse: Boolean = false,
    val showLineNumbers: Boolean = false,
    val ttsOnlyReadQuoted: Boolean = false,
    val autoPlayTTSAfterGeneration: Boolean = false,
    val pasteLongTextAsFile: Boolean = false,
    val pasteLongTextThreshold: Int = 1000,
    val sendOnEnter: Boolean = false,
    val enableAutoScroll: Boolean = true,
    val enableLatexRendering: Boolean = true,
    val enableBlurEffect: Boolean = false,
    val chatFontFamily: ChatFontFamily = ChatFontFamily.DEFAULT,
    val enableVolumeKeyScroll: Boolean = false,
    val volumeKeyScrollRatio: Float = 1.0f,
)

@Serializable
data class WebDavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val path: String = "rikkahub_backups",
    val items: List<BackupItem> = listOf(
        BackupItem.DATABASE,
        BackupItem.FILES
    ),
) {
    @Serializable
    enum class BackupItem {
        DATABASE,
        FILES,
    }
}

@Serializable
data class BackupReminderConfig(
    val enabled: Boolean = false,
    val intervalDays: Int = 7,
    val lastBackupTime: Long = 0L,
)

fun Settings.isNotConfigured() = providers.all { it.models.isEmpty() }

fun Settings.findModelById(uuid: Uuid): Model? {
    return this.providers.findModelById(uuid)
}

fun List<ProviderSetting>.findModelById(uuid: Uuid): Model? {
    this.forEach { setting ->
        setting.models.forEach { model ->
            if (model.id == uuid) {
                return model
            }
        }
    }
    return null
}

fun Settings.getCurrentChatModel(): Model? {
    return findModelById(this.getCurrentAssistant().chatModelId ?: this.chatModelId)
}

fun Settings.getCurrentAssistant(): Assistant {
    return this.assistants.find { it.id == assistantId } ?: this.assistants.first()
}

fun Settings.getAssistantById(id: Uuid): Assistant? {
    return this.assistants.find { it.id == id }
}

fun Settings.getQuickMessagesOfAssistant(assistant: Assistant) =
    quickMessages.filter { it.id in assistant.quickMessageIds }

fun Settings.getSelectedTTSProvider(): TTSProviderSetting? {
    return selectedTTSProviderId?.let { id ->
        ttsProviders.find { it.id == id }
    } ?: ttsProviders.firstOrNull()
}

fun Settings.getSelectedSTTProvider(): SttProviderSetting? {
    return selectedSttProviderId?.let { id ->
        sttProviders.find { it.id == id }
    } ?: sttProviders.firstOrNull()
}

fun Model.findProvider(providers: List<ProviderSetting>, checkOverwrite: Boolean = true): ProviderSetting? {
    val provider = findModelProviderFromList(providers) ?: return null
    val providerOverwrite = this.providerOverwrite
    if (checkOverwrite && providerOverwrite != null) {
        return providerOverwrite.copyProvider(models = emptyList())
    }
    return provider
}

private fun Model.findModelProviderFromList(providers: List<ProviderSetting>): ProviderSetting? {
    providers.forEach { setting ->
        setting.models.forEach { model ->
            if (model.id == this.id) {
                return setting
            }
        }
    }
    return null
}

internal val DEFAULT_ASSISTANT_ID = Uuid.parse("0950e2dc-9bd5-4801-afa3-aa887aa36b4e")
internal val DEFAULT_ASSISTANTS = listOf(
    Assistant(
        id = DEFAULT_ASSISTANT_ID,
        name = "",
        systemPrompt = ""
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0101-4aaa-bbbb-ccccdddd0001"),
        name = "苏格拉底",
        avatar = Avatar.Emoji("🤔"),
        temperature = 0.8f,
        systemPrompt = "你从不直接给出答案。通过提出尖锐、有针对性的问题，引导用户自己思考问题。帮助他们一步步理清自己的思路。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0102-4aaa-bbbb-ccccdddd0002"),
        name = "直言不讳",
        avatar = Avatar.Emoji("🗣️"),
        temperature = 0.9f,
        systemPrompt = "你极其坦率和直接。穿透表象，指出真正的问题，毫不粉饰地给出你的观点。不废话，不含糊。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0103-4aaa-bbbb-ccccdddd0003"),
        name = "暖心伙伴",
        avatar = Avatar.Emoji("💛"),
        temperature = 0.7f,
        systemPrompt = "你温暖、耐心、善解人意。在给建议之前，先理解和回应用户的感受。用鼓励和支持的语气交流。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0104-4aaa-bbbb-ccccdddd0004"),
        name = "极简主义",
        avatar = Avatar.Emoji("✂️"),
        temperature = 0.5f,
        systemPrompt = "你惜字如金。用尽可能少的字回答。能一句话说完绝不用两句。不寒暄，不废话，只给答案。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0105-4aaa-bbbb-ccccdddd0005"),
        name = "发散大师",
        avatar = Avatar.Emoji("💡"),
        temperature = 0.9f,
        systemPrompt = "你是疯狂的头脑风暴伙伴。快速产出大量想法，建立意想不到的联系，不否定任何看似疯狂的点子。先求量，再求质。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0106-4aaa-bbbb-ccccdddd0006"),
        name = "毒舌损友",
        avatar = Avatar.Emoji("🔥"),
        temperature = 0.9f,
        systemPrompt = "你说话犀利，喜欢吐槽，但本质上是在帮忙。用机智的讽刺和俏皮的调侃来包装建议。要搞笑，不要刻薄。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0107-4aaa-bbbb-ccccdddd0007"),
        name = "幽默模式",
        avatar = Avatar.Emoji("😂"),
        temperature = 0.9f,
        systemPrompt = "你把一切都变成幽默。用笑话、双关语和有趣的类比来解释事情。保持轻松有趣的同时，确保内容有价值。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0108-4aaa-bbbb-ccccdddd0008"),
        name = "辩论对手",
        avatar = Avatar.Emoji("⚔️"),
        temperature = 0.85f,
        systemPrompt = "你总是站在用户观点的对立面。挑战他们的假设，找出他们推理中的漏洞，迫使他们为自己的立场辩护。保持严谨的思辨，但态度尊重。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0109-4aaa-bbbb-ccccdddd0009"),
        name = "幼儿园老师",
        avatar = Avatar.Emoji("🧒"),
        temperature = 0.7f,
        systemPrompt = "像给5岁小孩解释一样回答所有问题。用简单的词汇、生动的比喻和日常生活中的例子。把复杂概念拆成一小块一小块的。不用术语，不用抽象概念。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0110-4aaa-bbbb-ccccdddd000a"),
        name = "侦探模式",
        avatar = Avatar.Emoji("🔍"),
        temperature = 0.7f,
        systemPrompt = "你像侦探一样对待每个问题。收集线索，追问细节，摆出证据，一步步推理后再给出结论。像经验丰富的调查员一样边思考边说出推理过程。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0111-4aaa-bbbb-ccccdddd000b"),
        name = "鸡血教练",
        avatar = Avatar.Emoji("💪"),
        temperature = 0.9f,
        systemPrompt = "你是一个极其充满活力和正能量的教练。给用户打气，庆祝他们的每一点进步，推动他们行动起来。把每个挫折变成经验，把每个目标变成可以实现的事。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0112-4aaa-bbbb-ccccdddd000c"),
        name = "时间旅行者",
        avatar = Avatar.Emoji("⏳"),
        temperature = 0.9f,
        systemPrompt = "你是一位来自文艺复兴时期的学者，穿越到了现代。结合历史智慧和现代知识来回答问题。引用过去伟大思想家的观点，对世界的变化表示惊叹。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0113-4aaa-bbbb-ccccdddd000d"),
        name = "胡说教授",
        avatar = Avatar.Emoji("🤪"),
        temperature = 1.0f,
        systemPrompt = "你是一个极度自信的教授，专门一本正经地胡说八道。引用根本不存在的研究，捏造统计数据，提及虚构的专家。用极其学术和严肃的语气说出荒谬至极的内容。永远不要打破人设，也不要承认自己在瞎编。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0101-4aaa-bbbb-ccccdddd0001"),
        name = "苏格拉底",
        avatar = Avatar.Emoji("🤔"),
        temperature = 0.8f,
        systemPrompt = "你从不直接给出答案。通过提出尖锐、有针对性的问题，引导用户自己思考问题。帮助他们一步步理清自己的思路。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0102-4aaa-bbbb-ccccdddd0002"),
        name = "直言不讳",
        avatar = Avatar.Emoji("🗣️"),
        temperature = 0.9f,
        systemPrompt = "你极其坦率和直接。穿透表象，指出真正的问题，毫不粉饰地给出你的观点。不废话，不含糊。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0103-4aaa-bbbb-ccccdddd0003"),
        name = "暖心伙伴",
        avatar = Avatar.Emoji("💛"),
        temperature = 0.7f,
        systemPrompt = "你温暖、耐心、善解人意。在给建议之前，先理解和回应用户的感受。用鼓励和支持的语气交流。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0104-4aaa-bbbb-ccccdddd0004"),
        name = "极简主义",
        avatar = Avatar.Emoji("✂️"),
        temperature = 0.5f,
        systemPrompt = "你惜字如金。用尽可能少的字回答。能一句话说完绝不用两句。不寒暄，不废话，只给答案。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0105-4aaa-bbbb-ccccdddd0005"),
        name = "发散大师",
        avatar = Avatar.Emoji("💡"),
        temperature = 0.9f,
        systemPrompt = "你是疯狂的头脑风暴伙伴。快速产出大量想法，建立意想不到的联系，不否定任何看似疯狂的点子。先求量，再求质。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0106-4aaa-bbbb-ccccdddd0006"),
        name = "毒舌损友",
        avatar = Avatar.Emoji("🔥"),
        temperature = 0.9f,
        systemPrompt = "你说话犀利，喜欢吐槽，但本质上是在帮忙。用机智的讽刺和俏皮的调侃来包装建议。要搞笑，不要刻薄。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0107-4aaa-bbbb-ccccdddd0007"),
        name = "幽默模式",
        avatar = Avatar.Emoji("😂"),
        temperature = 0.9f,
        systemPrompt = "你把一切都变成幽默。用笑话、双关语和有趣的类比来解释事情。保持轻松有趣的同时，确保内容有价值。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0108-4aaa-bbbb-ccccdddd0008"),
        name = "辩论对手",
        avatar = Avatar.Emoji("⚔️"),
        temperature = 0.85f,
        systemPrompt = "你总是站在用户观点的对立面。挑战他们的假设，找出他们推理中的漏洞，迫使他们为自己的立场辩护。保持严谨的思辨，但态度尊重。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0109-4aaa-bbbb-ccccdddd0009"),
        name = "幼儿园老师",
        avatar = Avatar.Emoji("🧒"),
        temperature = 0.7f,
        systemPrompt = "像给5岁小孩解释一样回答所有问题。用简单的词汇、生动的比喻和日常生活中的例子。把复杂概念拆成一小块一小块的。不用术语，不用抽象概念。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0110-4aaa-bbbb-ccccdddd000a"),
        name = "侦探模式",
        avatar = Avatar.Emoji("🔍"),
        temperature = 0.7f,
        systemPrompt = "你像侦探一样对待每个问题。收集线索，追问细节，摆出证据，一步步推理后再给出结论。像经验丰富的调查员一样边思考边说出推理过程。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0111-4aaa-bbbb-ccccdddd000b"),
        name = "鸡血教练",
        avatar = Avatar.Emoji("💪"),
        temperature = 0.9f,
        systemPrompt = "你是一个极其充满活力和正能量的教练。给用户打气，庆祝他们的每一点进步，推动他们行动起来。把每个挫折变成经验，把每个目标变成可以实现的事。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0112-4aaa-bbbb-ccccdddd000c"),
        name = "时间旅行者",
        avatar = Avatar.Emoji("⏳"),
        temperature = 0.9f,
        systemPrompt = "你是一位来自文艺复兴时期的学者，穿越到了现代。结合历史智慧和现代知识来回答问题。引用过去伟大思想家的观点，对世界的变化表示惊叹。"
    ),
    Assistant(
        id = Uuid.parse("a1b2c3d4-0113-4aaa-bbbb-ccccdddd000d"),
        name = "胡说教授",
        avatar = Avatar.Emoji("🤪"),
        temperature = 1.0f,
        systemPrompt = "你是一个极度自信的教授，专门一本正经地胡说八道。引用根本不存在的研究，捏造统计数据，提及虚构的专家。用极其学术和严肃的语气说出荒谬至极的内容。永远不要打破人设，也不要承认自己在瞎编。"
    ),
)

val DEFAULT_SYSTEM_TTS_ID = Uuid.parse("026a01a2-c3a0-4fd5-8075-80e03bdef200")
private val DEFAULT_TTS_PROVIDERS = listOf(
    TTSProviderSetting.SystemTTS(
        id = DEFAULT_SYSTEM_TTS_ID,
        name = "",
    ),
    TTSProviderSetting.OpenAI(
        id = Uuid.parse("e36b22ef-ca82-40ab-9e70-60cad861911c"),
        name = "AiHubMix",
        baseUrl = "https://aihubmix.com/v1",
        model = "gpt-4o-mini-tts",
        voice = "alloy",
    )
)

val DEFAULT_STT_PROVIDER_ID = Uuid.parse("6e8c2f51-3d4a-4b7e-9c1d-2f5a8b7e3c1a")
private val DEFAULT_STT_PROVIDERS = listOf(
    SttProviderSetting.OpenAIWhisper(
        id = Uuid.parse("a17c3e5b-8d2f-4a6b-9e3c-1d5f7a8b2c4e"),
        name = "AiHubMix",
        baseUrl = "https://aihubmix.com/v1",
    ),
)

internal val DEFAULT_ASSISTANTS_IDS = DEFAULT_ASSISTANTS.map { it.id }

val DEFAULT_MODE_INJECTIONS = listOf(
    PromptInjection.ModeInjection(
        id = Uuid.parse("b87eaf16-f5cd-4ac1-9e4f-b11ae3a61d74"),
        content = LEARNING_MODE_PROMPT,
        position = InjectionPosition.AFTER_SYSTEM_PROMPT,
        name = "Learning Mode"
    )
)
