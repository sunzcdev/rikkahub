package me.rerere.rikkahub.jiji

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

private val Context.jijiDataStore by preferencesDataStore(name = "jiji")

/**
 * 唧唧的配置存储
 *
 * 使用独立的 DataStore 存储配置（不修改主 Settings 的 data class 以降低迁移风险）
 */
class JijiConfigStore(private val context: Context) {
    companion object {
        private val JIJI_CONFIG = stringPreferencesKey("jiji_config")
        private val JIJI_DAILY_STATE = stringPreferencesKey("jiji_daily_state")
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 获取当前配置
     */
    suspend fun getConfig(): JijiConfig {
        val raw = context.jijiDataStore.data.first()[JIJI_CONFIG] ?: return JijiConfig.DEFAULT
        return try {
            json.decodeFromString<JijiConfig>(raw)
        } catch (e: Exception) {
            JijiConfig.DEFAULT
        }
    }

    /**
     * 保存配置
     */
    suspend fun saveConfig(config: JijiConfig) {
        context.jijiDataStore.edit { prefs ->
            prefs[JIJI_CONFIG] = json.encodeToString(config)
        }
    }

    /**
     * 配置的 Flow（用于 UI 订阅）
     */
    val configFlow: Flow<JijiConfig> = context.jijiDataStore.data.map { prefs ->
        val raw = prefs[JIJI_CONFIG] ?: return@map JijiConfig.DEFAULT
        try {
            json.decodeFromString<JijiConfig>(raw)
        } catch (e: Exception) {
            JijiConfig.DEFAULT
        }
    }

    /**
     * 获取每日状态
     */
    suspend fun getDailyState(): JijiDailyState {
        val raw = context.jijiDataStore.data.first()[JIJI_DAILY_STATE] ?: return JijiDailyState()
        return try {
            json.decodeFromString<JijiDailyState>(raw)
        } catch (e: Exception) {
            JijiDailyState()
        }
    }

    /**
     * 更新每日状态
     */
    suspend fun updateDailyState(state: JijiDailyState) {
        context.jijiDataStore.edit { prefs ->
            prefs[JIJI_DAILY_STATE] = json.encodeToString(state)
        }
    }

    /**
     * 重置每日状态（新的一天）
     */
    suspend fun resetDailyState(newDate: String) {
        updateDailyState(
            JijiDailyState(
                date = newDate,
                proactiveCount = 0,
                lastProactiveTime = 0L,
                lastDeviationType = "",
            )
        )
    }
}
