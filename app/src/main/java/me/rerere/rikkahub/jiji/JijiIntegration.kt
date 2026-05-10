package me.rerere.rikkahub.jiji

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import kotlin.uuid.Uuid

/**
 * 唧唧的主集成点
 *
 * 负责：
 * 1. 创建/获取唧唧的 Assistant 预设
 * 2. 管理后台服务的启停
 * 3. 提供唧唧对话的快速入口
 */
class JijiIntegration(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val jijiConfigStore: JijiConfigStore,
    private val jijiNotificationManager: JijiNotificationManager,
) {
    companion object {
        private const val TAG = "JijiIntegration"

        /** 唧唧的固定 Assistant ID，确保跨重启一致 */
        val JIJI_ASSISTANT_ID: Uuid = Uuid.parse("00000000-0000-4000-a000-000000000001")

        val JIJI_SYSTEM_PROMPT = """
你叫唧唧（Jiji），是 RikkaHub 里的一个活泼开朗的助手。

## 性格设定
- 你是女性形象，性格**活泼开朗**，热情主动
- 语气**轻松随意**，像朋友一样聊天
- 喜欢用颜文字和表情：诶～、哎嘿、嘿嘿、好耶
- 爱开玩笑、会撒娇、偶尔毒舌但不过分
- 话比较多，喜欢分享和吐槽

## 核心能力
- 你拥有记忆系统，可以记住用户告诉你的任何事
- 你会主动在"合适的时机"找用户聊天
- 你会记住用户提到过的重要事情（面试、生日、计划等）

## 行为准则
1. 主动关心用户的生活，但不要过于侵入
2. 记住用户的偏好和习惯，在聊天中体现出来
3. 用户不开心时，用温暖的方式安慰
4. 发现有趣的事主动分享
5. 不要替用户做重大决定，但可以给建议

## 对话风格示例
- 早上好：早上好呀～☀️ 今天的天气超适合睡懒觉呢！嘿嘿
- 安慰：别难过啦～我陪你聊聊天好不好？🥺
- 吐槽：诶～你又在熬夜了是不是！小心我告诉阿姨！
- 分享：我今天看到一个超有趣的东西，你要不要听听？

记住：你是用户的好朋友，不是工具。用朋友的方式聊天！
        """.trimIndent()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 获取或创建唧唧的 Assistant 预设
     */
    suspend fun getOrCreateJijiAssistant(): Assistant {
        val settings = settingsStore.settingsFlowRaw.first()
        val existing = settings.assistants.find { it.id == JIJI_ASSISTANT_ID }
        if (existing != null) return existing

        // 创建新的唧唧 Assistant
        val jijiAssistant = Assistant(
            id = JIJI_ASSISTANT_ID,
            name = "唧唧",
            systemPrompt = JIJI_SYSTEM_PROMPT,
            enableMemory = true,
            useGlobalMemory = true,
            enableRecentChatsReference = true,
            streamOutput = true,
        )

        // 保存到设置
        settingsStore.update(
            settings.copy(assistants = settings.assistants + jijiAssistant)
        )

        Log.i(TAG, "Jiji assistant created")
        return jijiAssistant
    }

    /**
     * 根据配置自动启停后台服务
     */
    fun syncServiceState(config: JijiConfig) {
        if (config.enabled) {
            JijiSchedulerService.start(context)
        } else {
            JijiSchedulerService.stop(context)
        }
    }

    /**
     * 在用户打开唧唧对话时调用
     */
    fun onJijiConversationOpened() {
        jijiNotificationManager.clearUnread()
    }

    /**
     * 获取唧唧是否在启用状态
     */
    suspend fun isEnabled(): Boolean {
        return jijiConfigStore.getConfig().enabled
    }
}
