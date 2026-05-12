package me.rerere.rikkahub.jiji

import kotlinx.serialization.Serializable

/**
 * 唧唧的配置信息，存储在 DataStore 中（JSON 序列化）
 */
@Serializable
data class JijiConfig(
    /** 唧唧总开关 */
    val enabled: Boolean = true,
    /** 检查间隔（分钟） */
    val checkIntervalMinutes: Int = 3,
    /** 是否启用位置感知 */
    val locationEnabled: Boolean = true,
    /** 是否启用天气感知 */
    val weatherEnabled: Boolean = false,  // 测试阶段关掉天气，避免网络超时
    /** OpenWeatherMap API Key */
    val openWeatherApiKey: String = "",
    /** 熵驱动开关（开启后随机搭话，搭话不限次数） */
    val entropyEnabled: Boolean = true,
    /** 每日主动搭话上限（Int.MAX_VALUE 即不限） */
    val dailyProactiveLimit: Int = Int.MAX_VALUE,
    /** 推送冷却时间（分钟） */
    val cooldownMinutes: Int = 0,  // 测试阶段：无冷却
) {
    companion object {
        val DEFAULT = JijiConfig()
    }
}

/**
 * 唧唧每天的状态（用于日限流）
 */
@Serializable
data class JijiDailyState(
    val date: String = "",          // yyyy-MM-dd
    val proactiveCount: Int = 0,
    val lastProactiveTime: Long = 0L,
    val lastDeviationType: String = "",
)
