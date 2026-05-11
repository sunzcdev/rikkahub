package me.rerere.rikkahub.data.perception

import kotlinx.serialization.Serializable

@Serializable
data class LocationSnapshot(
    val city: String,
    val district: String?,
    val street: String? = null,
    val streetNum: String? = null,
    val poiName: String? = null,     // 附近地标/大厦
    val aoiName: String? = null,     // 商圈/区域
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long,
)

@Serializable
data class WeatherSnapshot(
    val city: String,
    val temperature: Int,
    val condition: String,
    val humidity: Int,
    val timestamp: Long,
)

/**
 * 感知数据存储模型
 *
 * 熵驱动写入：只有状态发生变化时才追加记录，无意义重复不写。
 * 长期存储不修剪，导入导出时全量。
 * 短期记忆从这里取最近 N 条输出到 prompt。
 */
@Serializable
data class PerceptionMemory(
    val locationHistory: List<LocationSnapshot> = emptyList(),
    val weatherHistory: List<WeatherSnapshot> = emptyList(),
) {
    // 感知细节常量
    companion object {
        /** 短期记忆的最大条目数（注入 prompt 时取最近这 N 条） */
        const val SHORT_TERM_LIMIT = 100

        /** 天气熵驱动的温度变化阈值（°C） */
        const val WEATHER_TEMP_THRESHOLD = 3

        /** 定位熵驱动：相同区域视为无变化 */
        const val LOCATION_SAME_RADIUS_METERS = 500
    }

    /**
     * 生成短期记忆文本（取最近 N 条，去重合并相邻同值）
     * 用于注入系统 prompt
     */
    fun toShortTermText(): String {
        val sb = StringBuilder()
        sb.appendLine("## 近期感知记录")

        // 位置趋势（取最近 SHORT_TERM_LIMIT 条，但合并相邻同区域）
        val recentLocations = locationHistory.takeLast(SHORT_TERM_LIMIT)
            .mergeAdjacentSame()
        if (recentLocations.isNotEmpty()) {
            sb.append("位置变动：")
            sb.appendLine(recentLocations.joinToString(" → ") { loc ->
                val name = loc.district ?: loc.city
                formatTimestampAgo(loc.timestamp)
            })
        }

        // 天气变化
        val recentWeather = weatherHistory.takeLast(SHORT_TERM_LIMIT)
        if (recentWeather.isNotEmpty()) {
            sb.append("天气变化：")
            sb.appendLine(recentWeather.joinToString(" → ") { w ->
                "${formatTimestampAgo(w.timestamp)}${
                    w.condition
                }${w.temperature}°C"
            })
        }

        return sb.toString().trimEnd()
    }

    private fun formatTimestampAgo(timestamp: Long): String {
        val minutes = (System.currentTimeMillis() - timestamp) / 60_000
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            minutes < 1440 -> "${minutes / 60}小时前"
            else -> "${minutes / 1440}天前"
        }
    }
}

/** 合并相邻的相同区域定位记录 */
private fun List<LocationSnapshot>.mergeAdjacentSame(): List<LocationSnapshot> {
    if (isEmpty()) return this
    val result = mutableListOf<LocationSnapshot>()
    var last = first()
    result.add(last)
    for (i in 1 until size) {
        val current = this[i]
        if (current.city != last.city || current.district != last.district) {
            result.add(current)
            last = current
        }
        // 同区域跳过
    }
    return result
}
