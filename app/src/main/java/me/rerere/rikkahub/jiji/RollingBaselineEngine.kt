package me.rerere.rikkahub.jiji

import me.rerere.common.android.Logging

/**
 * EWMA 滚动基线引擎
 *
 * 5 条信息流各维护一个 EWMA 均值，聚合偏差检测整体状态变化。
 * 状态机：稳定 → 跃迁 → 新稳定 → 推送
 *
 * 唧唧只吸收自己感知到的信息（聊天、天气、位置、时间、设备）。
 * 推送不是"阈值到了"，而是"信息状态变了"。
 */
class RollingBaselineEngine(
    config: Config = Config(),
) {
    companion object {
        private const val TAG = "RollingBaselineEngine"

        /**
         * 每条流的 EWMA 窗口大小（α = 2 / (windowSize + 1)）
         * 值越小的流 α 越大，响应越快
         */
        private val WINDOW_SIZES = mapOf(
            StreamName.CHAT to 3,       // α=0.5, 快速响应，聊天中断即刻察觉
            StreamName.WEATHER to 30,    // α=0.065, 慢速，只看长期趋势
            StreamName.LOCATION to 5,    // α=0.33, 中等
            StreamName.TIME to 60,       // α=0.033, 极慢，仅做参考
            StreamName.DEVICE to 2,      // α=0.67, 快速响应
        )

        /** 每条流的聚合权重 */
        private val DEFAULT_WEIGHTS = mapOf(
            StreamName.CHAT to 0.4,
            StreamName.WEATHER to 0.2,
            StreamName.LOCATION to 0.25,
            StreamName.TIME to 0.05,
            StreamName.DEVICE to 0.1,
        )

        private fun initialStreams(): Map<String, InformationStream> {
            return DEFAULT_WEIGHTS.mapValues { (name, weight) ->
                InformationStream(name = name, weight = weight)
            }
        }
    }

    /** 流名称常量 */
    object StreamName {
        const val CHAT = "chat"
        const val WEATHER = "weather"
        const val LOCATION = "location"
        const val TIME = "time"
        const val DEVICE = "device"
    }

    data class Config(
        val stableThreshold: Double = 0.1,
        val pushThreshold: Double = 0.3,
        val stableConfirmTicks: Int = 2,
        val cooldownMs: Long = 30 * 60 * 1000L,
    )

    data class InformationStream(
        val name: String,
        val currentValue: Double = 0.0,
        val baselineAvg: Double = 0.0,
        val deviation: Double = 0.0,
        val weight: Double,
        val lastUpdateAt: Long = 0L,
    )

    data class State(
        val streams: Map<String, InformationStream> = initialStreams(),
        val aggregateDeviation: Double = 0.0,
        val wasStable: Boolean = true,
        val stableDurationTicks: Int = 0,
        val lastPushAt: Long = 0L,
        val lastPushReason: String = "",
    )

    /** 推送触发事件 */
    data class PushEvent(
        val triggerStream: String,
        val aggregateDeviation: Double,
        val reason: String,          // 可读的触发原因
        val timestamp: Long,
    )

    private var state = State()

    var config: Config = config
        private set

    fun updateConfig(newConfig: Config) {
        config = newConfig
    }

    /** 重置引擎（用于测试或重启检测周期） */
    fun reset() {
        state = State()
        Logging.d(TAG, "Rolling baseline engine reset")
    }

    /**
     * 更新某条流的值
     *
     * @param name 流名称（StreamName 常量）
     * @param value 归一化值 0.0~1.0
     */
    fun updateStream(name: String, value: Double) {
        val stream = state.streams[name] ?: run {
            Logging.w(TAG, "Unknown stream: $name")
            return
        }

        val windowSize = WINDOW_SIZES[name] ?: 5
        val alpha = 2.0 / (windowSize + 1.0)
        val newBaseline = alpha * value + (1.0 - alpha) * stream.baselineAvg
        val deviation = Math.abs(value - newBaseline)

        val updatedStream = stream.copy(
            currentValue = value,
            baselineAvg = newBaseline,
            deviation = deviation,
            lastUpdateAt = System.currentTimeMillis(),
        )

        state = state.copy(
            streams = state.streams + (name to updatedStream)
        )
    }

    /**
     * 检查是否发生了状态跃迁
     *
     * 每次检测周期调用一次。内部维护状态机：
     *   稳定 → 发现跃迁（aggregateDeviation > pushThreshold）→ 等待稳定
     *   → 连续 stableConfirmTicks 次检测稳定 → 推送触发
     *
     * @return 如果跃迁完成且可推送，返回 PushEvent；否则 null
     */
    fun checkTransition(): PushEvent? {
        val aggregate = state.streams.values.sumOf { it.deviation * it.weight }
        val isStable = aggregate < config.stableThreshold

        val currentTicks = if (isStable) state.stableDurationTicks + 1 else 0

        state = state.copy(
            aggregateDeviation = aggregate,
            stableDurationTicks = currentTicks,
        )

        // 稳定 → 不稳定（跃迁开始）
        if (state.wasStable && !isStable && aggregate >= config.pushThreshold) {
            state = state.copy(wasStable = false)
            Logging.d(TAG, "Transition started: aggregate=$aggregate")
            return null
        }

        // 不稳定 → 稳定（跃迁完成）
        val justCompleted = !state.wasStable && isStable && currentTicks >= config.stableConfirmTicks
        if (justCompleted) {
            state = state.copy(wasStable = true, stableDurationTicks = 0)

            // 冷却检查
            val now = System.currentTimeMillis()
            if (now - state.lastPushAt < config.cooldownMs) {
                Logging.d(TAG, "In cooldown, skipping push")
                return null
            }

            // 找出变化最大的流
            val triggerEntry = state.streams.maxByOrNull { it.value.deviation }
            val triggerName = triggerEntry?.key ?: return null
            val reason = buildPushReason(triggerName, aggregate)

            state = state.copy(
                lastPushAt = now,
                lastPushReason = reason,
            )

            Logging.i(TAG, "Push triggered: stream=$triggerName, aggregate=$aggregate, reason=$reason")
            return PushEvent(
                triggerStream = triggerName,
                aggregateDeviation = aggregate,
                reason = reason,
                timestamp = now,
            )
        }

        return null
    }

    /** 获取当前引擎状态快照 */
    fun getState(): State = state

    /** 获取各流的可读摘要 */
    fun getStreamsSummary(): String {
        return state.streams.entries.joinToString("\n") { (name, stream) ->
            "$name: cur=${"%.2f".format(stream.currentValue)}, avg=${"%.2f".format(stream.baselineAvg)}, dev=${"%.2f".format(stream.deviation)}"
        }
    }

    private fun buildPushReason(triggerStream: String, aggregate: Double): String {
        return when (triggerStream) {
            StreamName.CHAT -> "突然不说话了呢"
            StreamName.WEATHER -> "天气变了"
            StreamName.LOCATION -> "位置变了"
            StreamName.TIME -> "时间到了"
            StreamName.DEVICE -> "设备状态变了"
            else -> "信息状态发生了变化"
        } + "（偏差=${"%.2f".format(aggregate)}）"
    }
}
