package me.rerere.rikkahub.jiji

import android.util.Log

/**
 * 基线管理器
 *
 * 从唧唧的记忆中归纳用户的日常模式（时间规律、偏好等）。
 * 实现策略：简化版——直接将记忆内容传递给偏差检测器做 LLM 判断，
 * 不单独持久化基线。每次检测时动态评估。
 */
class BaselineManager {
    companion object {
        private const val TAG = "BaselineManager"
    }

    /**
     * 从记忆中提取用户模式
     * 简化实现：直接返回原始记忆作为上下文，
     * 由偏差检测器的 LLM 调用来做模式识别
     */
    fun extractBaseline(memories: List<String>): Baseline {
        if (memories.isEmpty()) {
            return Baseline()
        }

        // 从记忆中提取时间相关的模式（关键词匹配）
        val timePatterns = mutableListOf<TimePattern>()
        val preferenceTags = mutableListOf<String>()

        for (memory in memories) {
            val lower = memory.lowercase()

            // 简单的时间模式检测
            if (lower.contains("上班") || lower.contains("工作") || lower.contains("公司")) {
                timePatterns.add(
                    TimePattern(
                        description = "用户有工作/上班的日常安排",
                        confidence = 0.7f,
                    )
                )
                preferenceTags.add("工作")
            }
            if (lower.contains("周末") || lower.contains("放假") || lower.contains("休息")) {
                timePatterns.add(
                    TimePattern(
                        description = "用户周末有特别的安排",
                        confidence = 0.6f,
                    )
                )
            }
            if (lower.contains("早") || lower.contains("起床")) {
                timePatterns.add(
                    TimePattern(
                        description = "用户早起",
                        confidence = 0.5f,
                    )
                )
            }
            if (lower.contains("晚") || lower.contains("熬夜") || lower.contains("失眠")) {
                timePatterns.add(
                    TimePattern(
                        description = "用户睡得晚",
                        confidence = 0.5f,
                    )
                )
            }

            // 偏好标签提取
            val preferences = listOf("户外", "运动", "游戏", "电影", "音乐", "读书", "旅行", "美食", "摄影")
            for (pref in preferences) {
                if (lower.contains(pref) && pref !in preferenceTags) {
                    preferenceTags.add(pref)
                }
            }
        }

        return Baseline(
            timePatterns = timePatterns,
            preferenceTags = preferenceTags,
            lastUpdated = System.currentTimeMillis(),
        )
    }
}
