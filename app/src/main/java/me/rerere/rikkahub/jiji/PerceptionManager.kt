package me.rerere.rikkahub.jiji

import android.util.Log
import java.util.Calendar

/**
 * 感知管理器——收集时间、位置、天气信息
 *
 * 位置获取：复用高德地图（Amap）定位方案，通过 JijiLocationProvider 获取
 * 天气获取：每天最多请求 1 次，通过 OpenWeatherMap API
 */
class PerceptionManager(
    private val weatherFetcher: WeatherFetcher,
    private val locationProvider: JijiLocationProvider,
) {
    companion object {
        private const val TAG = "PerceptionManager"
        private const val WEATHER_CACHE_TTL_MS = 12L * 60 * 60 * 1000 // 12小时
    }

    private var cachedWeather: WeatherInfo? = null
    private var cachedWeatherTime: Long = 0L

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
     * 每天最多请求 1 次（12小时缓存）
     */
    suspend fun getWeather(config: JijiConfig): WeatherInfo? {
        if (!config.weatherEnabled || config.openWeatherApiKey.isBlank()) return null

        val now = System.currentTimeMillis()

        // 12小时内不重复请求
        if (cachedWeather != null && (now - cachedWeatherTime) < WEATHER_CACHE_TTL_MS) {
            return cachedWeather
        }

        val location = getLocation() ?: return null
        val result = weatherFetcher.fetchWeather(location.city, config.openWeatherApiKey)
            ?: return null

        val info = WeatherInfo(
            condition = result.condition,
            description = "${result.description} / ${result.temperature}°C",
            temperature = result.temperature,
            humidity = result.humidity,
            lastUpdated = now,
        )

        cachedWeather = info
        cachedWeatherTime = now
        return info
    }

    /**
     * 收集完整上下文
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
