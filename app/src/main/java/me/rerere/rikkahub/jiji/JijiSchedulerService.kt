package me.rerere.rikkahub.jiji

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import org.koin.android.ext.android.inject
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.Uuid

/**
 * 唧唧的后台调度服务
 *
 * 每15分钟执行一次完整的"感知→读取→判断→行动→通知"循环
 */
class JijiSchedulerService : Service(), CoroutineScope {
    companion object {
        private const val TAG = "JijiScheduler"
        private const val NOTIFICATION_ID = 3002

        const val ACTION_START = "me.rerere.rikkahub.jiji.action.START"
        const val ACTION_STOP = "me.rerere.rikkahub.jiji.action.STOP"
        const val ACTION_CHECK_NOW = "me.rerere.rikkahub.jiji.action.CHECK_NOW"

        fun start(context: Context) {
            val intent = Intent(context, JijiSchedulerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, JijiSchedulerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    // DI
    private val perceptionManager: PerceptionManager by inject()
    private val deviationDetector: DeviationDetector by inject()
    private val baselineManager: BaselineManager by inject()
    private val jijiNotificationManager: JijiNotificationManager by inject()
    private val memoryRepository: MemoryRepository by inject()
    private val conversationRepository: ConversationRepository by inject()
    private val jijiConfigStore: JijiConfigStore by inject()
    private val settingsStore: SettingsStore by inject()
    private val providerManager: me.rerere.ai.provider.ProviderManager by inject()

    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.IO
    private var checkJob: Job? = null
    private var isRunning = false

    /** 唧唧的固定会话 ID（同一个助手只有一个会话） */
    private val jijiConversationId: Uuid = Uuid.parse("00000000-0000-4000-a000-000000000002")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isRunning) {
                    isRunning = true
                    startForegroundCompat()
                    startCheckCycle()
                    Log.i(TAG, "Jiji scheduler started")
                }
            }
            ACTION_STOP -> {
                stopCheckCycle()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                isRunning = false
                Log.i(TAG, "Jiji scheduler stopped")
            }
            ACTION_CHECK_NOW -> {
                launch { runCheckCycle() }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCheckCycle()
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): android.app.Notification {
        val stopIntent = Intent(this, JijiSchedulerService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE,
        )
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val launchPendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, JijiNotificationManager.JIJI_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle("唧唧")
            .setContentText("唧唧正在后台感知中…")
            .setContentIntent(launchPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "停止", stopPendingIntent)
            .build()
    }

    private fun startCheckCycle() {
        checkJob?.cancel()
        checkJob = launch {
            Log.i(TAG, "Check cycle started")
            while (isActive) {
                runCheckCycle()
                val config = jijiConfigStore.getConfig()
                Log.d(TAG, "Cycle done, next check in ${config.checkIntervalMinutes}min")
                delay(config.checkIntervalMinutes * 60 * 1000L)
            }
        }
    }

    private fun stopCheckCycle() {
        checkJob?.cancel()
        checkJob = null
    }

    /**
     * 执行一次完整的检测循环
     */
    private suspend fun runCheckCycle() {
        try {
            val config = jijiConfigStore.getConfig()
            if (!config.enabled) return

            val timeDesc = perceptionManager.getTimeDescription()
            if (deviationDetector.isInDoNotDisturb(timeDesc)) return

            // 读取唧唧的记忆
            val memories = memoryRepository.getGlobalMemories()
            val memoryTexts = memories.map { it.content }

            // 感知上下文
            val context = perceptionManager.collectContext(
                config = config,
                lastInteractionMinutes = getLastInteractionMinutes(),
                recentMemories = memoryTexts,
            )

            // 基线 + 偏差检测
            val baseline = baselineManager.extractBaseline(memoryTexts)
            val dailyState = jijiConfigStore.getDailyState()
            val deviations = deviationDetector.detect(
                context = context, baseline = baseline,
                lastDeviationType = dailyState.lastDeviationType,
            )

            // 追加记忆提醒类偏差
            val reminderDeviation = detectReminderFromMemory(memoryTexts)
            val allDeviations = if (reminderDeviation != null) {
                listOf(reminderDeviation) + deviations
            } else {
                deviations
            }

            val bestDeviation = allDeviations.firstOrNull()
            if (bestDeviation != null && bestDeviation.relevance >= 0.5f) {
                val today = getTodayDate()
                if (dailyState.date != today) {
                    jijiConfigStore.resetDailyState(today)
                }

                val updatedState = jijiConfigStore.getDailyState()
                val cooldownMs = config.cooldownMinutes * 60 * 1000L
                val elapsed = System.currentTimeMillis() - updatedState.lastProactiveTime

                // 提醒类偏差无视冷却期和每日上限
                val isReminder = bestDeviation.type == DeviationType.UPCOMING_EVENT
                val canProceed = isReminder || (
                        updatedState.proactiveCount < config.dailyProactiveLimit &&
                                (elapsed >= cooldownMs || updatedState.lastProactiveTime == 0L)
                        )

                if (canProceed) {
                    // 读取唧唧会话的最后几条消息，提取话题
                    val lastTopic = getJijiRecentTopic()
                    
                    // 优先用 AI 生成，规则方案作为降级
                    val message = generateWithAI(bestDeviation, context, lastTopic, memoryTexts)
                        ?: generateProactiveMessage(bestDeviation, context, lastTopic, memoryTexts)

                    // 写入唧唧会话（创建一条 ASSISTANT 消息）
                    val conversationIdStr = ensureConversationAndAddMessage(message)

                    // 发系统通知（携带 conversationId）
                    jijiNotificationManager.sendProactiveNotification(message, conversationIdStr)

                    // 更新日状态
                    jijiConfigStore.updateDailyState(
                        JijiDailyState(
                            date = today,
                            proactiveCount = updatedState.proactiveCount + 1,
                            lastProactiveTime = System.currentTimeMillis(),
                            lastDeviationType = "", // 测试阶段清空类型去重，以便每次循环都能推送
                        )
                    )

                    Log.i(TAG, "Jiji proactive: ${bestDeviation.description} -> $message")
                } else if (bestDeviation != null) {
                    Log.d(TAG, "Deferred: ${bestDeviation.type} rel=${bestDeviation.relevance} (cooldown/daily limit)")
                }
            } else if (bestDeviation != null) {
                Log.d(TAG, "Skipped: ${bestDeviation.type} rel=${bestDeviation.relevance} (< 0.5)")
            } else {
                Log.d(TAG, "No deviation detected (lastInteraction=${context.lastInteractionMinutes}min)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Jiji check cycle failed", e)
        }
    }

    /**
     * 确保唧唧会话存在，并将主动消息添加到会话中
     * @return 会话 ID 的字符串
     */
    private suspend fun ensureConversationAndAddMessage(message: String): String {
        // 查找或创建唧唧会话
        var conversation = conversationRepository.getConversationById(jijiConversationId)
        if (conversation == null) {
            conversation = Conversation(
                id = jijiConversationId,
                assistantId = JijiIntegration.JIJI_ASSISTANT_ID,
                title = "与唧唧的对话",
                messageNodes = emptyList(),
            )
            conversationRepository.insertConversation(conversation)
            Log.i(TAG, "Jiji conversation created")
        }

        // 添加一条 ASSISTANT 消息
        val messageNode = MessageNode(
            messages = listOf(UIMessage.assistant(message)),
        )
        val updatedConversation = conversation.copy(
            messageNodes = conversation.messageNodes + messageNode,
            updateAt = java.time.Instant.now(),
        )
        conversationRepository.updateConversation(updatedConversation)

        return jijiConversationId.toString()
    }

    /**
     * 获取真实沉默时间：查询唧唧会话的最后更新时间
     */
    private suspend fun getLastInteractionMinutes(): Int {
        val conversation = conversationRepository.getConversationById(jijiConversationId) ?: return 9999
        val lastTime = conversation.updateAt.toEpochMilli()
        val elapsed = System.currentTimeMillis() - lastTime
        return (elapsed / 60000).toInt()
    }

    /**
     * 从记忆中检测即将发生的事件（日程提醒）
     */
    private fun detectReminderFromMemory(memories: List<String>): Deviation? {
        val now = java.util.Calendar.getInstance()
        val todayStr = String.format("%02d月%02d日", now.get(java.util.Calendar.MONTH) + 1, now.get(java.util.Calendar.DAY_OF_MONTH))
        val tomorrow = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_MONTH, 1) }
        val tomorrowStr = String.format("%02d月%02d日", tomorrow.get(java.util.Calendar.MONTH) + 1, tomorrow.get(java.util.Calendar.DAY_OF_MONTH))

        for (memory in memories) {
            val lower = memory.lowercase()
            // 检测明天或今天的事件
            if (lower.contains("明天") || lower.contains(tomorrowStr) || lower.contains("下周") ||
                lower.contains("面试") || lower.contains("考试") || lower.contains("会议") ||
                lower.contains("生日") || lower.contains("deadline") || lower.contains("截止")) {
                return Deviation(
                    type = DeviationType.UPCOMING_EVENT,
                    description = "记忆中有关键事件即将到来",
                    relevance = 0.95f, // 高优先级
                    suggestedMessage = memory,
                )
            }
        }
        return null
    }

    /**
     * 从唧唧会话中提取最近的话题（最后一条用户消息的关键词）
     */
    private suspend fun getJijiRecentTopic(): String {
        val conversation = conversationRepository.getConversationById(jijiConversationId) ?: return ""
        // 取最后3条消息，找用户发的消息
        val recentMessages = conversation.messageNodes.takeLast(3)
            .mapNotNull { node ->
                val msg = node.messages.getOrNull(node.selectIndex) ?: return@mapNotNull null
                val text = msg.toText()?.take(120)?.trim() ?: return@mapNotNull null
                if (text.isBlank()) null else text
            }
        if (recentMessages.isEmpty()) return ""
        
        // 取最后一条非空消息
        val lastMsg = recentMessages.last()
        // 简单关键词提取：去掉问候语，取最长实义词片段
        val cleaned = lastMsg
            .replace(Regex("[😊🤗🥺💪😯😄☀️❄️☔️🌤🥵🥶🤔🙏✨🎉\\d+分钟前]"), "")
            .trim()
        return cleaned.take(40)
    }

    /**
     * 用 AI 生成搭话消息（使用 suggestion model 做一次性文本生成）
     * 比规则模板更自然多变，每天 100 条约消耗 ~25K tokens，几乎免费
     * @return 生成的文本，失败返回 null 走规则降级
     */
    private suspend fun generateWithAI(
        deviation: Deviation, context: JijiContext,
        lastTopic: String, recentMemories: List<String>,
    ): String? {
        return try {
            val settings = settingsStore.settingsFlow.value
            val model = settings.findModelById(settings.suggestionModelId) ?: return null
            val provider = model.findProvider(settings.providers) ?: return null
            val handler = providerManager.getProviderByType(provider)

            // 构建场景描述（给 AI 的上下文）
            val city = context.location?.city ?: ""
            val district = context.location?.district ?: ""
            val weatherInfo = context.weather?.let { w ->
                "${w.condition} ${w.temperature}°C ${w.description}"
            } ?: "未获取"
            
            val cal = java.util.Calendar.getInstance()
            val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            val dayOfWeek = when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
                java.util.Calendar.MONDAY -> "周一"
                java.util.Calendar.TUESDAY -> "周二"
                java.util.Calendar.WEDNESDAY -> "周三"
                java.util.Calendar.THURSDAY -> "周四"
                java.util.Calendar.FRIDAY -> "周五"
                java.util.Calendar.SATURDAY -> "周六"
                java.util.Calendar.SUNDAY -> "周日"
                else -> ""
            }
            val isWeekend = cal.get(java.util.Calendar.DAY_OF_WEEK) in 
                setOf(java.util.Calendar.SATURDAY, java.util.Calendar.SUNDAY)

            val deviationTypeCN = when (deviation.type) {
                DeviationType.LONG_SILENCE -> "用户好久没和唧唧聊天了"
                DeviationType.WEATHER_CHANGE -> "天气变化"
                DeviationType.TIME_MISMATCH -> "用户在反常时间出现在家"
                DeviationType.PREFERENCE_GAP -> "用户行为与偏好不符（如户外爱好者却在宅家）"
                DeviationType.POSITIVE_OPPORTUNITY -> "好天气适合外出活动"
                DeviationType.UPCOMING_EVENT -> "用户记忆中有即将到来的事件"
            }

            // 沉默时长
            val silentMinutes = deviation.description.substringAfter("用户已")
                .substringBefore("分钟").toIntOrNull() ?: 0
            val silentHours = silentMinutes / 60

            val prompt = """你是一个名叫「唧唧」的 AI 助手，用户是你的好朋友。你现在要主动给用户发一条搭话消息。

当前场景：
- 时间：$dayOfWeek ${hour}点
${if (isWeekend) "- 今天是周末" else ""}
${if (city.isNotEmpty()) "- 位置：$city $district" else ""}
- 天气：$weatherInfo
- 用户上次和你聊天是 ${silentHours}小时前
- 触发原因：$deviationTypeCN
${if (lastTopic.isNotBlank()) "- 上次聊天话题：$lastTopic" else ""}

要求：
1. 语气像好朋友随口说话，自然口语化，简短（20-50字）
2. 结合场景信息自然地融入对话中
3. 如果天气不好（下雨/下雪/太热/太冷），关心一下
4. 如果周末+好天气+用户在家，推荐出去玩
5. 如果用户有话题，自然引用上次聊的内容
6. 如果是工作日白天，暗示用户该上班/该下班了
7. **不要用「亲」「亲爱的」「宝宝」等肉麻称呼**
8. 一句话说完，不要分段

直接输出搭话内容本身："""

            val result = handler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(prompt)),
                params = me.rerere.ai.provider.TextGenerationParams(
                    model = model,
                    temperature = 0.8f,
                    maxTokens = 100,
                    reasoningLevel = me.rerere.ai.core.ReasoningLevel.OFF,
                ),
            )
            
            val text = result.choices.firstOrNull()?.message?.toText()?.trim()
            if (text.isNullOrBlank()) {
                Log.d(TAG, "AI returned empty response, using rule-based fallback")
                return null
            }
            
            Log.i(TAG, "AI generated message: $text")
            text
        } catch (e: Exception) {
            Log.w(TAG, "AI generation failed, using rule-based fallback", e)
            null
        }
    }

    /**
     * 生成上下文感知的主动搭话消息
     * 
     * 策略：先根据偏差类型 + 上下文决定"想说什么"，
     * 再收集"有什么可说的"，最后组装成自然语言。
     *
     * @param deviation 偏差类型（触发时机）
     * @param context 感知上下文
     * @param lastTopic 最近聊天话题
     * @param recentMemories 全局记忆
     */
    private fun generateProactiveMessage(
        deviation: Deviation, context: JijiContext,
        lastTopic: String, recentMemories: List<String>,
    ): String {
        // ========== 1. 收集当前场景事实 ==========
        val cityStr = context.location?.city?.take(6) ?: ""
        val districtStr = context.location?.district?.take(6) ?: ""
        val locName = if (districtStr.isNotEmpty()) districtStr else cityStr
        
        val cal = java.util.Calendar.getInstance()
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = cal.get(java.util.Calendar.MINUTE)
        val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val isWeekend = dayOfWeek == java.util.Calendar.SATURDAY || dayOfWeek == java.util.Calendar.SUNDAY
        val isWorkHours = hour in 9..17 && !isWeekend  // 工作日9-17点为工作时间
        
        // 天气描述（更自然的中文）
        val weatherDesc = context.weather?.let { w ->
            when {
                w.condition.contains("Rain", ignoreCase = true) ||
                w.description.contains("雨", ignoreCase = true) -> "下雨了"
                w.condition.contains("Snow", ignoreCase = true) ||
                w.description.contains("雪", ignoreCase = true) -> "下雪了"
                w.temperature > 35 -> "今天${w.temperature}°C好热啊"
                w.temperature > 30 -> "今天${w.temperature}°C有点热"
                w.temperature < 0 -> "今天${w.temperature}°C好冷"
                w.temperature < 10 -> "今天${w.temperature}°C有点冷"
                else -> null  // 舒适温度不提天气
            }
        } ?: ""
        
        val isRaining = weatherDesc.contains("雨")
        val isSnowing = weatherDesc.contains("雪")
        
        // 时间段
        val period = when (hour) {
            in 0..5 -> "凌晨"
            in 6..8 -> "早上"
            in 9..11 -> "上午"
            12 -> "中午"
            in 13..17 -> "下午"
            in 18..23 -> "晚上"
            else -> ""
        }
        
        // 话题引用
        val topicRef = if (lastTopic.isNotBlank()) {
            when {
                lastTopic.contains("方案") || lastTopic.contains("计划") ->
                    "\u300C${lastTopic}\u300D" to "方案"
                lastTopic.contains("代码") || lastTopic.contains("bug") || lastTopic.contains("修") ->
                    "\u300C${lastTopic}\u300D" to "代码"
                lastTopic.contains("工作") || lastTopic.contains("项目") ->
                    "\u300C${lastTopic}\u300D" to "工作"
                lastTopic.contains("娃") || lastTopic.contains("孩子") || lastTopic.contains("儿子") ->
                    "\u300C${lastTopic}\u300D" to "带娃"
                else -> "\u300C${lastTopic}\u300D" to "其他"
            }
        } else null
        val topicStr = topicRef?.first ?: ""
        val topicCategory = topicRef?.second ?: ""
        
        // 沉默时长
        val silentHours = deviation.description.substringAfter("用户已").substringBefore("分钟")
            .toIntOrNull()?.let { it / 60 } ?: 0
        val isLongAbsence = silentHours > 48  // 超过2天算久别
        
        // ========== 2. 策略决策 ==========
        // 根据偏差类型 + 场景事实，决定消息的"意图"
        data class Strategy(
            val greeting: String,          // 开场问候
            val mentionWeather: Boolean,   // 是否提天气
            val mentionTopic: Boolean,     // 是否提话题
            val mentionTime: Boolean,      // 是否提时间
            val mentionLocation: Boolean,  // 是否提位置
            val suggestion: String,        // 建议/策略
        )
        
        val strategy = when (deviation.type) {
            // ---- 沉默太久 ----
            DeviationType.LONG_SILENCE -> {
                val suggest = when {
                    isRaining && isWorkHours && locName.isNotEmpty() ->
                        "下雨了，你要是还在${locName}的话，等下班早点走吧⚠️"
                    isRaining && isWorkHours ->
                        "下雨了，等下班早点走吧⚠️"
                    isRaining && !isWorkHours ->
                        "下雨了，出门记得带伞🌂"
                    isRaining && isWeekend ->
                        "下雨了，宅家放松也不错～"
                    isLongAbsence ->
                        "最近在忙啥呢，是不是把我忘了！"
                    else -> ""
                }
                Strategy(
                    greeting = if (isLongAbsence) "嘿～好久不见！🤗" else "嘿嘿，感觉好久没跟你聊天了～",
                    mentionWeather = isRaining || isSnowing,
                    mentionTopic = topicStr.isNotEmpty(),
                    mentionTime = true,
                    mentionLocation = locName.isNotEmpty(),
                    suggestion = suggest,
                )
            }
            
            // ---- 天气变化 ----
            DeviationType.WEATHER_CHANGE -> {
                val suggest = when {
                    isRaining && isWorkHours && locName.isNotEmpty() ->
                        "你要是还在$locName，等下班赶紧走吧，别淋着了"
                    isRaining && !isWorkHours ->
                        "出门记得带伞，别淋雨了🥺"
                    isSnowing ->
                        "好浪漫～要出去走走吗？❄️"
                    else -> ""
                }
                Strategy(
                    greeting = if (isRaining) "呜呜${weatherDesc}🥺" else if (isSnowing) "哇${weatherDesc}诶！❄️" else "今天天气有点特别呢～😄",
                    mentionWeather = true,
                    mentionTopic = topicStr.isNotEmpty(),
                    mentionTime = false,
                    mentionLocation = false,
                    suggestion = suggest,
                )
            }
            
            // ---- 时间反常 ----
            DeviationType.TIME_MISMATCH -> {
                // 场景分析：周末+好天气+在家 → 推荐出去玩
                val isGoodWeather = !isRaining && !isSnowing && weatherDesc.isEmpty()
                val isMorning = hour in 6..11
                val isAfternoon = hour in 12..17
                
                val greeting = when {
                    isWeekend && isGoodWeather && isMorning ->
                        "都${period}${hour}点了，难得周末好天气，你咋还窝在家？😯"
                    isWeekend && isGoodWeather && isAfternoon ->
                        "周末下午${hour}点了还不出去浪？浪费好天气呀！😯"
                    isWeekend && (isRaining || isSnowing) ->
                        "周末${period}${hour}点了还宅着，下雨天就适合躺平～☺️"
                    else -> "诶～${period}${hour}点你居然在家，好稀奇诶！😯"
                }
                
                val suggest = when {
                    isWeekend && isGoodWeather && isMorning -> {
                        val activity = when (hour) {
                            in 6..8 -> "去公园跑跑步，呼吸新鲜空气🌳"
                            in 9..10 -> "去爬个山或者逛公园，多舒服🌿"
                            in 11..12 -> "收拾收拾出发，中午正好到个好地方吃饭🍜"
                            else -> "出去走走，别辜负好天气☀️"
                        }
                        if (topicCategory == "带娃") "带娃$activity"
                        else activity
                    }
                    isWeekend && isGoodWeather && isAfternoon -> {
                        when {
                            topicCategory == "带娃" -> "带娃去公园或者游乐场耍耍🎠"
                            topicStr.isNotEmpty() -> "要不要出去找个咖啡馆，边喝边聊$topicStr？☕"
                            else -> "出去逛逛，找个有意思的地方坐坐☕"
                        }
                    }
                    isWeekend && (isRaining || isSnowing) ->
                        if (topicStr.isNotEmpty()) "正好下雨宅家搞搞${topicStr}，也挺好～" else "正好休息一下，看看剧打打游戏🎮"
                    topicStr.isNotEmpty() -> "上次说到的${topicStr}琢磨了没？"
                    else -> ""
                }
                
                Strategy(
                    greeting = greeting,
                    mentionWeather = isRaining || isSnowing,
                    mentionTopic = false,  // 在 suggestion 里处理了
                    mentionTime = false,
                    mentionLocation = true,
                    suggestion = suggest,
                )
            }
            
            // ---- 偏好偏离 ----
            DeviationType.PREFERENCE_GAP -> {
                Strategy(
                    greeting = if (isRaining) "${weatherDesc}了🥺" else "",
                    mentionWeather = isRaining,
                    mentionTopic = topicStr.isNotEmpty(),
                    mentionTime = false,
                    mentionLocation = locName.isNotEmpty(),
                    suggestion = "我记得你喜欢户外活动来着，是不是憋坏了？",
                )
            }
            
            // ---- 积极机会 ----
            DeviationType.POSITIVE_OPPORTUNITY -> {
                Strategy(
                    greeting = if (weatherDesc.isNotEmpty()) "${weatherDesc}，" else "",
                    mentionWeather = weatherDesc.isNotEmpty(),
                    mentionTopic = topicStr.isNotEmpty(),
                    mentionTime = false,
                    mentionLocation = locName.isNotEmpty(),
                    suggestion = "没带娃出去耍？待在家里多浪费呀！☀️",
                )
            }
            
            // ---- 日程提醒 ----
            DeviationType.UPCOMING_EVENT -> {
                Strategy(
                    greeting = "",
                    mentionWeather = false,
                    mentionTopic = false,
                    mentionTime = false,
                    mentionLocation = false,
                    suggestion = "",
                )
            }
        }
        
        // ========== 3. 组装消息 ==========
        // 日程提醒：特殊处理，直接引用记忆
        if (deviation.type == DeviationType.UPCOMING_EVENT) {
            val event = deviation.suggestedMessage?.take(80) ?: ""
            val loc = if (locName.isNotEmpty()) "你现在在${locName}，" else ""
            return "诶诶！我记得你之前提到过\u300C${event}\u300D，是不是快到了？${loc}准备好了没？加油加油💪"
        }
        
        return buildString {
            // 问候
            append(strategy.greeting)
            if (!endsWith("～") && !endsWith("😯") && !endsWith("？") && !endsWith("。") && !endsWith("🥺") && !endsWith("！")) {
                append(" ")
            }
            
            // 位置
            if (strategy.mentionLocation && locName.isNotEmpty()) {
                append("你在${locName}吧，")
            }
            
            // 时间
            if (strategy.mentionTime) {
                append("${period}${hour}点了")
                if (isWeekend) append("（周末啦）")
                if (!endsWith("，") && !endsWith("）")) append("，")
            }
            
            // 话题
            if (strategy.mentionTopic && topicStr.isNotEmpty()) {
                when (topicCategory) {
                    "方案" -> append("上次那个${topicStr}有没有新思路？🧐 ")
                    "代码" -> append("上次说到的${topicStr}，搞定了没？🤔 ")
                    "工作" -> append("最近那个${topicStr}进展如何？💪 ")
                    "带娃" -> append("上次聊到${topicStr}，最近乖不乖呀？😊 ")
                    else -> append("上次聊到的${topicStr}，最近咋样了？😊 ")
                }
            }
            
            // 建议
            if (strategy.suggestion.isNotEmpty()) {
                append(strategy.suggestion)
                append(" ")
            }
            
            // 兜底问候
            if (deviation.type == DeviationType.LONG_SILENCE) {
                append(if (isLongAbsence) "在忙啥呢？" else "最近怎么样？😊")
            }
        }.trimEnd()
    }

    private fun getTodayDate(): String {
        val cal = java.util.Calendar.getInstance()
        return String.format("%04d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH))
    }
}
