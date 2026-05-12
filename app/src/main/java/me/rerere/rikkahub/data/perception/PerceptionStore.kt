package me.rerere.rikkahub.data.perception

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.perceptionStore: DataStore<Preferences> by preferencesDataStore(
    name = "perception_memory"
)

class PerceptionStore(private val context: Context) {

    companion object {
        private val KEY_MEMORY = stringPreferencesKey("perception_memory")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 感知记忆流 */
    val memoryFlow: Flow<PerceptionMemory> = context.perceptionStore.data
        .map { preferences ->
            preferences[KEY_MEMORY]?.let {
                try {
                    json.decodeFromString<PerceptionMemory>(it)
                } catch (e: Exception) {
                    PerceptionMemory()
                }
            } ?: PerceptionMemory()
        }

    /** 获取当前记忆快照 */
    suspend fun getMemory(): PerceptionMemory {
        return memoryFlow.first()
    }

    /** 追加位置记录 */
    suspend fun appendLocation(snapshot: LocationSnapshot, force: Boolean = false) {
        context.perceptionStore.edit { preferences ->
            val current = readCurrent(preferences)

            // 熵驱动：同区域跳过（相同 city + district + 无 POI 变化）
            if (!force) {
                val last = current.locationHistory.lastOrNull()
                if (last != null && last.city == snapshot.city &&
                    last.district == snapshot.district &&
                    last.poiName == snapshot.poiName
                ) {
                    return@edit
                }
            }

            val lastTimestamp = current.locationHistory.lastOrNull()?.timestamp
            val interval = if (lastTimestamp != null) {
                snapshot.timestamp - lastTimestamp
            } else {
                0L
            }
            val updated = current.copy(
                locationHistory = current.locationHistory + snapshot.copy(intervalMs = interval)
            )
            preferences[KEY_MEMORY] = json.encodeToString(PerceptionMemory.serializer(), updated)
        }
    }

    /** 追加天气记录 */
    suspend fun appendWeather(snapshot: WeatherSnapshot, force: Boolean = false) {
        context.perceptionStore.edit { preferences ->
            val current = readCurrent(preferences)

            // 熵驱动：同温度+同状况跳过
            if (!force) {
                val last = current.weatherHistory.lastOrNull()
                if (last != null && last.condition == snapshot.condition &&
                    last.temperature == snapshot.temperature
                ) {
                    return@edit
                }
            }

            val lastTimestamp = current.weatherHistory.lastOrNull()?.timestamp
            val interval = if (lastTimestamp != null) {
                snapshot.timestamp - lastTimestamp
            } else {
                0L
            }
            val updated = current.copy(
                weatherHistory = current.weatherHistory + snapshot.copy(intervalMs = interval)
            )
            preferences[KEY_MEMORY] = json.encodeToString(PerceptionMemory.serializer(), updated)
        }
    }

    /** 覆盖写入（用于导入、重置） */
    suspend fun replaceMemory(memory: PerceptionMemory) {
        context.perceptionStore.edit { preferences ->
            preferences[KEY_MEMORY] = json.encodeToString(PerceptionMemory.serializer(), memory)
        }
    }

    /** 清空感知数据 */
    suspend fun clearMemory() {
        context.perceptionStore.edit { preferences ->
            preferences.remove(KEY_MEMORY)
        }
    }

    private fun readCurrent(preferences: Preferences): PerceptionMemory {
        return preferences[KEY_MEMORY]?.let {
            try {
                json.decodeFromString<PerceptionMemory>(it)
            } catch (e: Exception) {
                PerceptionMemory()
            }
        } ?: PerceptionMemory()
    }
}
