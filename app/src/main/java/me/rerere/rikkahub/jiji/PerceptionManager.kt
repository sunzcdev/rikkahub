package me.rerere.rikkahub.jiji

import me.rerere.common.android.Logging
import me.rerere.rikkahub.data.ai.tools.WeatherFetcher
import me.rerere.rikkahub.data.model.HardwareKeyConfig
import me.rerere.rikkahub.data.model.findHardwareKey
import me.rerere.rikkahub.data.perception.PerceptionCollector
import me.rerere.rikkahub.data.perception.PerceptionMemory
import me.rerere.rikkahub.data.perception.PerceptionStore
import java.util.Calendar

/**
 * 感知管理器——协调感知采集 + 存储 + 上下文输出
 *
 * 位置获取：复用高德地图（Amap）定位方案，通过 JijiLocationProvider 获取
 * 天气获取：通过 OpenWeatherMap API
 *
 * 采集器定时运行，数据经熵驱动存入 PerceptionStore。
 * 短期记忆从 Store 取最近 100 条输出到 JIJI prompt。
 * 长期记忆通过 query_perception tool 按需查询。
 */
class PerceptionManager(
    private val weatherFetcher: WeatherFetcher,
    private val locationProvider: JijiLocationProvider,
    private val perceptionStore: PerceptionStore,
    private val getHardwareKeys: () -> List<HardwareKeyConfig>,
) {
    companion object {
        private const val TAG = "PerceptionManager"
    }

    /** 感知采集器 */
    val collector: PerceptionCollector = PerceptionCollector(
        perceptionStore = perceptionStore,
        locationProvider = locationProvider,
        weatherFetcher = weatherFetcher,
        getHardwareKeys = getHardwareKeys,
    )

    /**
     * 获取感知短期记忆文本（用于注入 JIJI prompt）
     */
    suspend fun getShortTermMemory(): String {
        return perceptionStore.getMemory().toShortTermText()
    }

    /**
     * 获取完整感知记忆（用于 query_perception tool）
     */
    suspend fun getMemory(): PerceptionMemory {
        return perceptionStore.getMemory()
    }

    /**
     * 获取当前时间描述（如 "周六下午2点"）
     */
    fun getTimeDescription(): String {
        val cal = Calendar.getInstance()
        val dayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "周一"
            Calendar.TUESDAY -> "周二"
            Calendar.WEDNESDAY -> "周三"
            Calendar.THURSDAY -> "周四"
            Calendar.FRIDAY -> "周五"
            Calendar.SATURDAY -> "周六"
            Calendar.SUNDAY -> "周日"
            else -> ""
        }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val period = when (hour) {
            in 0..5 -> "凌晨"
            in 6..8 -> "早上"
            in 9..11 -> "上午"
            12 -> "中午"
            in 13..17 -> "下午"
            in 18..23 -> "晚上"
            else -> ""
        }
        return "$dayOfWeek${period}${hour}点"
    }

    /**
     * 获取当前时间戳
     */
    fun getCurrentTimestamp(): Long {
        return System.currentTimeMillis()
    }

    /**
     * 获取当前位置（通过 Amap 定位）
     */
    suspend fun getLocation(): LocationInfo? {
        return locationProvider.getLocation()
    }

    /**
     * 获取天气
     */
    suspend fun getWeather(config: JijiConfig): WeatherInfo? {
        if (!config.weatherEnabled) return null

        val now = System.currentTimeMillis()

        val apiKey = getHardwareKeys().findHardwareKey<HardwareKeyConfig.OpenWeather>()?.apiKey
        if (apiKey.isNullOrBlank()) return null

        val location = getLocation() ?: return null
        val lat = location.latitude
        val lon = location.longitude
        if (lat == null || lon == null) return null
        val result = weatherFetcher.fetchWeather(lat, lon, apiKey)
            ?: return null

        return WeatherInfo(
            condition = result.condition,
            description = "${result.description} / ${result.temperature}°C",
            temperature = result.temperature,
            humidity = result.humidity,
            lastUpdated = now,
        )
    }

    /**
     * 收集完整上下文（用于偏差检测和搭话生成）
     */
    suspend fun collectContext(
        config: JijiConfig,
        lastInteractionMinutes: Int,
        recentMemories: List<String>,
    ): JijiContext {
        return JijiContext(
            timestamp = getCurrentTimestamp(),
            timeDescription = getTimeDescription(),
            location = if (config.locationEnabled) getLocation() else null,
            weather = getWeather(config),
            lastInteractionMinutes = lastInteractionMinutes,
            recentMemories = recentMemories,
        )
    }
}
