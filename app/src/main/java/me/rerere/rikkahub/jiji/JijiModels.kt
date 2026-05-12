package me.rerere.rikkahub.jiji

/**
 * 唧唧当前感知到的上下文
 */
data class JijiContext(
    val timestamp: Long,
    val timeDescription: String,        // "周六下午2点"
    val location: LocationInfo?,
    val weather: WeatherInfo?,
    val lastInteractionMinutes: Int,
    val recentMemories: List<String>,
)

data class LocationInfo(
    val city: String,
    val district: String?,
    val street: String? = null,
    val streetNum: String? = null,
    val poiName: String? = null,     // 附近地标/大厦
    val aoiName: String? = null,     // 商圈/区域
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isHome: Boolean? = null,
    val isWork: Boolean? = null,
)

data class WeatherInfo(
    val condition: String,              // "晴"/"雨"/"阴"
    val description: String,            // "晴 / 25.5°C"
    val temperature: Int,               // 摄氏度
    val humidity: Int,                  // 湿度百分比
    val lastUpdated: Long,
)

/**
 * 用户基线（从记忆归纳）
 */
data class Baseline(
    val timePatterns: List<TimePattern> = emptyList(),
    val preferenceTags: List<String> = emptyList(),
    val lastUpdated: Long = 0L,
)

data class TimePattern(
    val description: String,
    val confidence: Float,
)

/**
 * 偏差信号
 */
data class Deviation(
    val type: DeviationType,
    val description: String,
    val relevance: Float,               // 0.0 ~ 1.0
    val suggestedMessage: String? = null,
)

enum class DeviationType {
    TIME_MISMATCH,
    WEATHER_CHANGE,
    LONG_SILENCE,
    PREFERENCE_GAP,
    POSITIVE_OPPORTUNITY,
    UPCOMING_EVENT,
    PUSH_EVENT,
}
