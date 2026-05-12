package me.rerere.rikkahub.data.perception

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val intervalMs: Long = 0,        // 距上次采集的间隔(ms)，首条为 0
)

@Serializable
data class WeatherSnapshot(
    val city: String,
    val temperature: Int,
    val condition: String,
    val humidity: Int,
    val timestamp: Long,
    val intervalMs: Long = 0,        // 距上次采集的间隔(ms)，首条为 0
)

/**
 * 感知数据存储模型
 *
 * 全量写入：每次采集均追加记录，不做熵驱动去重。
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
    }

    /**
     * 生成短期记忆文本（轨迹格式），用于注入系统 prompt。
     *
     * 定位：按位置分组生成轨迹，如 "12:00 在 二七广场C口 停留1分钟"
     * 天气：按时间序列简明列出
     */
    fun toShortTermText(): String {
        val sb = StringBuilder()
        sb.appendLine("## 近期感知记录")

        // 轨迹
        val trajectory = buildTrajectory()
        if (trajectory.isNotEmpty()) {
            sb.appendLine("【轨迹】")
            sb.appendLine(trajectory)
        }

        // 天气变化
        val recentWeather = weatherHistory.takeLast(SHORT_TERM_LIMIT)
        if (recentWeather.isNotEmpty()) {
            sb.appendLine()
            sb.append("【天气】")
            sb.appendLine(recentWeather.joinToString(" → ") { w ->
                "${formatTime(w.timestamp)} ${w.condition} ${w.temperature}°C"
            })
        }

        return sb.toString().trimEnd()
    }

    /**
     * 构建轨迹文本：将连续同位置记录合并为"停留"，位置变化时显示过渡。
     * 输出示例：
     *   12:00 在 二七广场C口 停留1分钟
     *   12:05 在 东大街附近
     *   12:06 在 陇海路附近
     */
    private fun buildTrajectory(): String {
        val recent = locationHistory.takeLast(SHORT_TERM_LIMIT)
        if (recent.isEmpty()) return ""

        // 按位置分组（city+district+poi 相同视为同一位置）
        val groups = mutableListOf<Pair<LocationSnapshot, LocationSnapshot>>()
        var first = recent.first()
        var last = recent.first()
        for (i in 1 until recent.size) {
            val cur = recent[i]
            if (isSameLocation(cur, last)) {
                last = cur
            } else {
                groups.add(first to last)
                first = cur
                last = cur
            }
        }
        groups.add(first to last)

        return groups.joinToString("\n") { (start, end) ->
            val dur = end.timestamp - start.timestamp
            val locName = buildLocationName(start)
            if (dur < 60_000) {
                "${formatTime(start.timestamp)} 在 $locName"
            } else {
                "${formatTime(start.timestamp)} 在 $locName 停留${dur / 60_000}分钟"
            }
        }
    }

    /** 判断两条定位记录是否在同一位置 */
    private fun isSameLocation(a: LocationSnapshot, b: LocationSnapshot): Boolean {
        return a.city == b.city && a.district == b.district && a.poiName == b.poiName
    }

    /** 从定位记录提取可读的位置名称 */
    private fun buildLocationName(loc: LocationSnapshot): String {
        return when {
            !loc.poiName.isNullOrBlank() -> loc.poiName
            !loc.street.isNullOrBlank() -> {
                val num = loc.streetNum?.let { it } ?: ""
                "${loc.street}${num}附近"
            }
            !loc.district.isNullOrBlank() -> "${loc.district}附近"
            else -> "${loc.city}附近"
        }
    }

    private fun formatTime(timestamp: Long): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}

