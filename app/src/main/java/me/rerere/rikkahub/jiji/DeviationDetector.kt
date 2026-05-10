package me.rerere.rikkahub.jiji

import kotlin.math.abs

/**
 * 偏差检测器
 *
 * 对比当前上下文与基线（以及原始记忆），判断是否有"值得主动搭话"的时机。
 * 基于记忆内容 + 上下文规则的检测，不依赖外部 LLM 调用。
 */
class DeviationDetector(
    private val baselineManager: BaselineManager = BaselineManager(),
) {
    companion object {
        private const val SILENCE_THRESHOLD_MINUTES = 5   // 测试阶段：5分钟未互动视为"长时间沉默"
        private const val WEATHER_CHANGE_MIN_DELTA = 8       // 温度变化 > 8°C 视为天气突变
    }

    /**
     * 执行偏差检测
     * @param context 当前感知上下文
     * @param baseline 用户基线
     * @param lastDeviationType 上次推送的偏差类型（用于去重）
     * @return 检测到的偏差列表，按 relevance 降序排列
     */
    fun detect(
        context: JijiContext,
        baseline: Baseline,
        lastDeviationType: String = "",
    ): List<Deviation> {
        val deviations = mutableListOf<Deviation>()

        // 1. 长时间沉默检测
        if (context.lastInteractionMinutes > SILENCE_THRESHOLD_MINUTES) {
            deviations.add(
                Deviation(
                    type = DeviationType.LONG_SILENCE,
                    description = "用户已 ${context.lastInteractionMinutes} 分钟未互动",
                    relevance = calculateSilenceRelevance(context.lastInteractionMinutes),
                )
            )
        }

        // 2. 时间模式偏差检测
        val timeDeviation = detectTimeDeviation(context, baseline)
        if (timeDeviation != null) {
            deviations.add(timeDeviation)
        }

        // 3. 天气变化检测
        val weatherDeviation = detectWeatherDeviation(context, baseline)
        if (weatherDeviation != null) {
            deviations.add(weatherDeviation)
        }

        // 4. 偏好偏差检测（天气 vs 偏好）
        val preferenceDeviation = detectPreferenceGap(context, baseline)
        if (preferenceDeviation != null) {
            deviations.add(preferenceDeviation)
        }

        // 5. 正向机会检测
        val positiveDeviation = detectPositiveOpportunity(context, baseline)
        if (positiveDeviation != null) {
            deviations.add(positiveDeviation)
        }

        // 按相关性排序，过滤掉上次已推送过的类型
        return deviations
            .filter { it.type.name != lastDeviationType }
            .sortedByDescending { it.relevance }
    }

    /**
     * 计算沉默相关性：沉默越久越相关
     * 测试阶段：按 SILENCE_THRESHOLD 动态缩放
     */
    private fun calculateSilenceRelevance(minutes: Int): Float {
        return when {
            minutes > SILENCE_THRESHOLD_MINUTES * 8 -> 0.9f
            minutes > SILENCE_THRESHOLD_MINUTES * 4 -> 0.7f
            minutes > SILENCE_THRESHOLD_MINUTES * 2 -> 0.5f
            minutes > SILENCE_THRESHOLD_MINUTES -> 0.5f
            else -> 0.3f
        }
    }

    /**
     * 时间模式偏差
     */
    private fun detectTimeDeviation(
        context: JijiContext,
        baseline: Baseline,
    ): Deviation? {
        val now = context.timeDescription
        val isWeekend = now.contains("周六") || now.contains("周日")
        val isNight = now.contains("晚上") || now.contains("凌晨")

        // 夜间不触发时间偏差
        if (isNight) return null

        // 检查是否有相关的记忆/基线
        for (pattern in baseline.timePatterns) {
            if (pattern.confidence < 0.5f) continue

            // 周末检测
            if (isWeekend && pattern.description.contains("周末")) {
                return Deviation(
                    type = DeviationType.TIME_MISMATCH,
                    description = "现在是${context.timeDescription}，用户周末通常有特别安排",
                    relevance = pattern.confidence * 0.8f,
                )
            }
        }

        return null
    }

    /**
     * 天气偏差检测
     */
    private fun detectWeatherDeviation(
        context: JijiContext,
        baseline: Baseline,
    ): Deviation? {
        val weather = context.weather ?: return null

        // 不同天气类型的"值得说话"程度
        return when (weather.condition.lowercase()) {
            "rain", "drizzle", "thunderstorm" -> {
                Deviation(
                    type = DeviationType.WEATHER_CHANGE,
                    description = "今天${weather.description}，下雨了",
                    relevance = 0.7f,
                )
            }
            "snow" -> {
                Deviation(
                    type = DeviationType.WEATHER_CHANGE,
                    description = "今天下雪了！${weather.description}",
                    relevance = 0.8f,
                )
            }
            "clear", "sunny" -> {
                if (weather.temperature in 20..28) {
                    Deviation(
                        type = DeviationType.POSITIVE_OPPORTUNITY,
                        description = "天气超好！${weather.description}",
                        relevance = 0.6f,
                    )
                } else {
                    null  // 普通好天气不触发
                }
            }
            "clouds" -> null  // 阴天不触发
            else -> null
        }
    }

    /**
     * 偏好偏差：天气变化与用户偏好的矛盾
     */
    private fun detectPreferenceGap(
        context: JijiContext,
        baseline: Baseline,
    ): Deviation? {
        val weather = context.weather ?: return null

        // 用户喜欢户外但下雨
        val likesOutdoor = "户外" in baseline.preferenceTags
        val isRainy = weather.condition.lowercase() in listOf("rain", "drizzle", "thunderstorm")

        if (likesOutdoor && isRainy) {
            return Deviation(
                type = DeviationType.PREFERENCE_GAP,
                description = "用户喜欢户外活动，但今天下雨了",
                relevance = 0.75f,
            )
        }

        // 极端温度
        if (weather.temperature > 35) {
            return Deviation(
                type = DeviationType.WEATHER_CHANGE,
                description = "今天${weather.temperature}°C，太热了",
                relevance = 0.5f,
            )
        }
        if (weather.temperature < 0) {
            return Deviation(
                type = DeviationType.WEATHER_CHANGE,
                description = "今天${weather.temperature}°C，好冷",
                relevance = 0.5f,
            )
        }

        return null
    }

    /**
     * 正向机会检测
     */
    private fun detectPositiveOpportunity(
        context: JijiContext,
        baseline: Baseline,
    ): Deviation? {
        val weather = context.weather ?: return null
        val isWeekend = context.timeDescription.contains("周六") ||
                context.timeDescription.contains("周日")

        // 周末 + 好天气 + 用户喜欢户外
        if (isWeekend && weather.temperature in 20..28 && "户外" in baseline.preferenceTags) {
            return Deviation(
                type = DeviationType.POSITIVE_OPPORTUNITY,
                description = "周末天气好，适合出去玩",
                relevance = 0.85f,
            )
        }

        return null
    }

    /**
     * 检查用户是否在免打扰时段
     */
    fun isInDoNotDisturb(timeDescription: String): Boolean {
        val hour = extractHour(timeDescription) ?: return false
        // 免打扰：23:00 ~ 07:00
        return hour >= 23 || hour < 7
    }

    private fun extractHour(timeDescription: String): Int? {
        val regex = Regex("(\\d+)点")
        return regex.find(timeDescription)?.groupValues?.get(1)?.toIntOrNull()
    }
}
