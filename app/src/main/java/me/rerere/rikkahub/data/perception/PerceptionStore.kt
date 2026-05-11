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

    /** 追加位置记录（熵驱动：同区域不追加） */
    suspend fun appendLocation(snapshot: LocationSnapshot, force: Boolean = false) {
        context.perceptionStore.edit { preferences ->
            val current = readCurrent(preferences)
            // 熵驱动：跟最后一条比，同区域不写（除非 force）
            if (!force) {
                val lastLocation = current.locationHistory.lastOrNull()
                if (lastLocation != null &&
                    lastLocation.city == snapshot.city &&
                    lastLocation.district == snapshot.district
                ) {
                    return@edit // 没变化，跳过
                }
            }
            val updated = current.copy(
                locationHistory = current.locationHistory + snapshot
            )
            preferences[KEY_MEMORY] = json.encodeToString(PerceptionMemory.serializer(), updated)
        }
    }

    /** 追加天气记录（熵驱动：同温度区间+同状况不追加） */
    suspend fun appendWeather(snapshot: WeatherSnapshot, force: Boolean = false) {
        context.perceptionStore.edit { preferences ->
            val current = readCurrent(preferences)
            // 熵驱动：跟最后一条比，同温度区间+同状况不写（除非 force）
            if (!force) {
                val lastWeather = current.weatherHistory.lastOrNull()
                if (lastWeather != null &&
                    lastWeather.city == snapshot.city &&
                    lastWeather.condition == snapshot.condition &&
                    kotlin.math.abs(lastWeather.temperature - snapshot.temperature) < PerceptionMemory.WEATHER_TEMP_THRESHOLD
                ) {
                    return@edit // 没显著变化，跳过
                }
            }
            val updated = current.copy(
                weatherHistory = current.weatherHistory + snapshot
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
