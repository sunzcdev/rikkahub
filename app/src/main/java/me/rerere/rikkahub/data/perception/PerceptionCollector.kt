package me.rerere.rikkahub.data.perception

import me.rerere.common.android.Logging
import kotlinx.coroutines.*
import me.rerere.rikkahub.data.ai.tools.WeatherFetcher
import me.rerere.rikkahub.data.model.HardwareKeyConfig
import me.rerere.rikkahub.data.model.findHardwareKey
import me.rerere.rikkahub.jiji.JijiLocationProvider

/**
 * 感知数据采集器
 *
 * - 定位每 1 分钟采集一次（熵驱动：同区域跳过）
 * - 天气每 1 小时采集一次（熵驱动：同温度+同状况跳过）
 *
 * 运行在 JijiSchedulerService 的协程作用域中。
 */
class PerceptionCollector(
    private val perceptionStore: PerceptionStore,
    private val locationProvider: JijiLocationProvider,
    private val weatherFetcher: WeatherFetcher,
    private val getHardwareKeys: () -> List<HardwareKeyConfig>,
) {
    companion object {
        private const val TAG = "PerceptionCollector"
        private const val LOCATION_INTERVAL_MS = 60_000L       // 1分钟
        private const val WEATHER_INTERVAL_MS = 3_600_000L     // 1小时
    }

    private var collectorJob: Job? = null

    /**
     * 启动采集器（运行在提供的协程作用域中）
     */
    fun start(scope: CoroutineScope) {
        stop()
        collectorJob = scope.launch {
            Logging.i(TAG, "Perception collector started")
            // 先采集一次
            collectLocationOnce()
            // 两个并行循环（内联，避免 isActive 问题）
            launch {
                while (isActive) {
                    delay(LOCATION_INTERVAL_MS)
                    collectLocationOnce()
                }
            }
            launch {
                // 天气第一次等 30 秒，给定位初始化留时间
                delay(30_000)
                while (isActive) {
                    collectWeatherOnce()
                    delay(WEATHER_INTERVAL_MS)
                }
            }
        }
    }

    fun stop() {
        collectorJob?.cancel()
        collectorJob = null
        Logging.i(TAG, "Perception collector stopped")
    }

    /**
     * 手动触发一次定位采集
     * @param force 为 true 时跳过熵驱动检查，强制写入
     */
    suspend fun collectLocationOnce(force: Boolean = false) {
        try {
            if (force) locationProvider.clearCache()
            val location = locationProvider.getLocation() ?: return
            perceptionStore.appendLocation(
                LocationSnapshot(
                    city = location.city,
                    district = location.district,
                    street = location.street,
                    streetNum = location.streetNum,
                    poiName = location.poiName,
                    aoiName = location.aoiName,
                    address = location.address,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = System.currentTimeMillis(),
                ),
                force = force,
            )
            Logging.d(TAG, "Location recorded: ${location.city} ${location.district}")
        } catch (e: Exception) {
            Logging.w(TAG, "Location collection failed", e)
        }
    }

    /**
     * 手动触发一次天气采集
     * @param force 为 true 时跳过熵驱动检查，强制写入
     * @return null 表示成功，非 null 为失败原因
     */
    suspend fun collectWeatherOnce(force: Boolean = false): String? {
        return try {
            // 从最近定位记录取经纬度（比城市名更可靠，OpenWeather city name 已弃用）
            val lastLoc = perceptionStore.getMemory().locationHistory.lastOrNull()
            val lat = lastLoc?.latitude
            val lon = lastLoc?.longitude

            // 没有定位记录时自举一次：调定位同时写入记录
            if (lat == null || lon == null) {
                val location = locationProvider.getLocation()
                if (location != null) {
                    perceptionStore.appendLocation(
                        LocationSnapshot(
                            city = location.city,
                            district = location.district,
                            street = location.street,
                            streetNum = location.streetNum,
                            poiName = location.poiName,
                            aoiName = location.aoiName,
                            address = location.address,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            timestamp = System.currentTimeMillis(),
                        ),
                        force = force,
                    )
                    Logging.d(TAG, "Auto-located for weather: ${location.city} ${location.district}")
                }
            }

            // 从存储中重新取（可能是自举刚写入的）
            val locAfter = perceptionStore.getMemory().locationHistory.lastOrNull()
            val finalLat = locAfter?.latitude
            val finalLon = locAfter?.longitude

            if (finalLat == null || finalLon == null) {
                return "无法获取位置信息（定位失败）"
            }
            val apiKey = getHardwareKeys()
                .findHardwareKey<HardwareKeyConfig.OpenWeather>()?.apiKey
            if (apiKey.isNullOrBlank()) {
                val msg = "OpenWeather Key 未配置"
                Logging.d(TAG, msg)
                return msg
            }
            val result = weatherFetcher.fetchWeather(finalLat, finalLon, apiKey)
            if (result == null) {
                return "天气API无响应: lat=$finalLat lon=$finalLon"
            }

            perceptionStore.appendWeather(
                WeatherSnapshot(
                    city = result.cityName,
                    temperature = result.temperature,
                    condition = result.condition,
                    humidity = result.humidity,
                    timestamp = System.currentTimeMillis(),
                ),
                force = force,
            )
            Logging.d(TAG, "Weather recorded: ${result.cityName} ${result.condition} ${result.temperature}°C")
            null // 成功
        } catch (e: Exception) {
            val msg = "天气采集异常: ${e.message}"
            Logging.w(TAG, msg, e)
            msg
        }
    }
}
