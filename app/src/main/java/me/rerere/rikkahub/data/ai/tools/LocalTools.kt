package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.HardwareKeyConfig
import me.rerere.rikkahub.data.perception.PerceptionStore
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.model.findHardwareKey
import me.rerere.rikkahub.utils.readClipboardText
import me.rerere.rikkahub.utils.writeClipboardText
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

@Serializable
sealed class LocalToolOption {
    @Serializable
    @SerialName("javascript_engine")
    data object JavascriptEngine : LocalToolOption()

    @Serializable
    @SerialName("time_info")
    data object TimeInfo : LocalToolOption()

    @Serializable
    @SerialName("clipboard")
    data object Clipboard : LocalToolOption()

    @Serializable
    @SerialName("tts")
    data object Tts : LocalToolOption()

    @Serializable
    @SerialName("ask_user")
    data object AskUser : LocalToolOption()

    @Serializable
    @SerialName("phone_bridge")
    data object PhoneBridge : LocalToolOption()

    @Serializable
    @SerialName("weather")
    data object Weather : LocalToolOption()

    @Serializable
    @SerialName("vibrate")
    data object Vibrate : LocalToolOption()
}

class LocalTools(
    private val context: Context,
    private val eventBus: AppEventBus,
    private val getHardwareKeys: () -> List<HardwareKeyConfig>,
    private val perceptionStore: PerceptionStore? = null,
    private val weatherFetcher: WeatherFetcher = WeatherFetcher(),
    private val getAmapApiKey: () -> String?,
    private val vibrationManager: VibrationManager,
) {
    val phoneBridge by lazy { PhoneBridge(context, eventBus, getHardwareKeys) }
    val weatherTool by lazy { WeatherTool(weatherFetcher, getHardwareKeys) }

    val queryPerceptionTool by lazy { createQueryPerceptionTool() }

    val javascriptTool by lazy {
        Tool(
            name = "eval_javascript",
            description = """
                Execute JavaScript code using QuickJS engine (ES2020).
                The result is the value of the last expression in the code.
                For calculations with decimals, use toFixed() to control precision.
            """.trimIndent(),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("code", buildJsonObject {
                            put("type", "string")
                            put("description", "The JavaScript code to execute")
                        })
                    },
                    required = listOf("code")
                )
            },
            execute = { args ->
                val code = args.jsonObject["code"]?.jsonPrimitive?.contentOrNull ?: ""
                if (code.isBlank()) return@Tool listOf(UIMessagePart.Text(""))
                try {
                    val quickjs = QuickJSContext.create()
                    val result = quickjs.evaluate(code)
                    listOf(UIMessagePart.Text(result.toString()))
                } catch (e: Exception) {
                    listOf(UIMessagePart.Text(e.message ?: "JavaScript execution failed"))
                }
            }
        )
    }

    val timeTool: Tool by lazy {
        Tool(
            name = "time_info",
            description = "Get current time info, you can use this to get current date and time.",
            execute = {
                val now = ZonedDateTime.now()
                listOf(UIMessagePart.Text(
                    buildJsonObject {
                        put("date", now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE))
                        put("time", now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")))
                        put("timezone", now.zone.id)
                        put("day_of_week", now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINESE))
                        put("unix_timestamp", now.toInstant().toEpochMilli())
                    }.toString()
                ))
            }
        )
    }

    val clipboardTool: Tool by lazy {
        Tool(
            name = "clipboard",
            description = "Read or write clipboard content.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("action", buildJsonObject {
                            put("type", "string")
                            put("description", "Action to perform: 'read' or 'write'")
                            put("enum", buildJsonArray {
                                add(JsonPrimitive("read"))
                                add(JsonPrimitive("write"))
                            })
                        })
                        put("content", buildJsonObject {
                            put("type", "string")
                            put("description", "Content to write (only used when action is 'write')")
                        })
                    },
                    required = listOf("action")
                )
            },
            execute = { args ->
                val params = args.jsonObject
                val action = params["action"]?.jsonPrimitive?.contentOrNull ?: ""
                when (action) {
                    "read" -> {
                        val content = context.readClipboardText()
                        listOf(UIMessagePart.Text(content ?: ""))
                    }
                    "write" -> {
                        val content = params["content"]?.jsonPrimitive?.contentOrNull ?: ""
                        context.writeClipboardText(content)
                        listOf(UIMessagePart.Text("Clipboard updated."))
                    }
                    else -> listOf(UIMessagePart.Text("Unknown action."))
                }
            }
        )
    }

    val ttsTool: Tool by lazy {
        Tool(
            name = "text_to_speech",
            description = "Convert text to speech and play it. Only use this when the user explicitly asks you to speak something out loud.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("text", buildJsonObject {
                            put("type", "string")
                            put("description", "The text to convert to speech")
                        })
                    },
                    required = listOf("text")
                )
            },
            execute = { args ->
                val text = args.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: ""
                eventBus.emit(AppEvent.Speak(text))
                listOf(UIMessagePart.Text("I'm speaking..."))
            }
        )
    }

    val askUserTool: Tool by lazy {
        Tool(
            name = "continue_conversation",
            description = "Ask the user a question to continue the conversation. Use this when you need to clarify something or need more information from the user.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("question", buildJsonObject {
                            put("type", "string")
                            put("description", "The question to ask the user")
                        })
                    },
                    required = listOf("question")
                )
            },
            execute = { args ->
                val question = args.jsonObject["question"]?.jsonPrimitive?.contentOrNull ?: ""
                listOf(UIMessagePart.Text("**${question}**"))
            }
        )
    }

    private fun createQueryPerceptionTool(): Tool {
        return Tool(
            name = "query_perception",
            description = "Query historical perception data (location history and weather history) collected by Jiji. Use this when the user asks about past locations, weather changes, or wants to recall where they were at a specific time.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("type", buildJsonObject {
                            put("type", "string")
                            put("description", "Type of data to query: 'location' or 'weather'")
                            put("enum", buildJsonArray {
                                add(JsonPrimitive("location"))
                                add(JsonPrimitive("weather"))
                            })
                        })
                        put("since", buildJsonObject {
                            put("type", "string")
                            put("description", "Optional start time in ISO format (e.g. '2024-01-15T10:00'). Defaults to 24 hours ago.")
                        })
                        put("until", buildJsonObject {
                            put("type", "string")
                            put("description", "Optional end time in ISO format. Defaults to now.")
                        })
                    },
                    required = listOf("type")
                )
            },
            execute = { args ->
                val perceptionMemory = perceptionStore?.getMemory() ?: return@Tool listOf(
                    UIMessagePart.Text(buildJsonObject {
                        put("error", true)
                        put("message", "感知数据不可用")
                    }.toString())
                )

                val params = args.jsonObject
                val type = params["type"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(
                    UIMessagePart.Text(buildJsonObject {
                        put("error", true)
                        put("message", "Missing type parameter")
                    }.toString())
                )

                val now = System.currentTimeMillis()
                val defaultSince = now - 24 * 60 * 60 * 1000L
                val sinceMs = params["since"]?.jsonPrimitive?.contentOrNull?.let { parseTimestamp(it) } ?: defaultSince
                val untilMs = params["until"]?.jsonPrimitive?.contentOrNull?.let { parseTimestamp(it) } ?: now

                when (type) {
                    "location" -> {
                        val entries = perceptionMemory.locationHistory.filter {
                            it.timestamp in sinceMs..untilMs
                        }
                        val summary = if (entries.isEmpty()) {
                            "该时间段内没有位置记录"
                        } else {
                            buildString {
                                appendLine("位置记录 (${formatTime(sinceMs)} ~ ${formatTime(untilMs)})：")
                                entries.forEach { loc ->
                                    appendLine("  - ${formatTime(loc.timestamp)}: ${loc.city} ${loc.district?.let { "- $it" } ?: ""}")
                                }
                            }
                        }
                        listOf(UIMessagePart.Text(buildJsonObject {
                            put("type", "location")
                            put("count", entries.size)
                            put("summary", summary)
                        }.toString()))
                    }
                    "weather" -> {
                        val entries = perceptionMemory.weatherHistory.filter {
                            it.timestamp in sinceMs..untilMs
                        }
                        val summary = if (entries.isEmpty()) {
                            "该时间段内没有天气记录"
                        } else {
                            buildString {
                                appendLine("天气记录 (${formatTime(sinceMs)} ~ ${formatTime(untilMs)})：")
                                entries.forEach { w ->
                                    appendLine("  - ${formatTime(w.timestamp)}: ${w.condition} ${w.temperature}°C")
                                }
                            }
                        }
                        listOf(UIMessagePart.Text(buildJsonObject {
                            put("type", "weather")
                            put("count", entries.size)
                            put("summary", summary)
                        }.toString()))
                    }
                    else -> listOf(UIMessagePart.Text(buildJsonObject {
                        put("error", true)
                        put("message", "Unknown type: $type")
                    }.toString()))
                }
            }
        )
    }

    private fun parseTimestamp(iso: String): Long {
        return try {
            java.time.LocalDateTime.parse(iso)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        }
    }

    private fun formatTime(ms: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(ms))
    }

    val vibrateTool by lazy {
        Tool(
            name = "vibrate",
            description = """Vibrate the device with precise timing.

mode=one_shot (default): {duration_ms: int(1-10000, default 500), amplitude?: int(0-255)}
mode=waveform: {timings: [off_ms, on_ms, off_ms, on_ms, ...], amplitudes?: int[]}

Cancel ongoing vibration with vibrate_cancel.""",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("mode", buildJsonObject {
                            put("type", "string")
                            put("enum", buildJsonArray {
                                add("one_shot")
                                add("waveform")
                            })
                            put("description", "Vibration mode (default: one_shot)")
                        })
                        put("duration_ms", buildJsonObject {
                            put("type", "integer")
                            put("description", "Duration in ms for one_shot mode (default: 500)")
                        })
                        put("amplitude", buildJsonObject {
                            put("type", "integer")
                            put("description", "0-255, optional")
                        })
                        put("timings", buildJsonObject {
                            put("type", "array")
                            put("items", buildJsonObject { put("type", "integer") })
                            put("description", "Waveform pattern [off,on,off,on,...]")
                        })
                        put("amplitudes", buildJsonObject {
                            put("type", "array")
                            put("items", buildJsonObject { put("type", "integer") })
                            put("description", "Amplitude for ON segments only (1-255). OFF segments are silent. Same length as timings.")
                        })
                    }
                )
            },
            execute = {
                val jsonObj = it.jsonObject
                val mode = jsonObj["mode"]?.jsonPrimitive?.contentOrNull ?: "one_shot"

                when (mode) {
                    "one_shot" -> {
                        val durationMs = jsonObj["duration_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 500L
                        val amplitude = jsonObj["amplitude"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                        val result = vibrationManager.oneShot(durationMs, amplitude)
                        listOf(UIMessagePart.Text(result.toJson()))
                    }

                    "waveform" -> {
                        val timingsArray = jsonObj["timings"]?.jsonArray?.map {
                            it.jsonPrimitive.contentOrNull?.toLongOrNull()
                                ?: error("invalid timing value in timings array")
                        }?.toLongArray()
                            ?: error("timings is required for waveform mode")
                        val amplitudesArray = jsonObj["amplitudes"]?.jsonArray?.map {
                            it.jsonPrimitive.contentOrNull?.toIntOrNull()
                                ?: error("invalid amplitude value in amplitudes array")
                        }?.toIntArray()
                        val result = vibrationManager.waveform(timingsArray, amplitudesArray)
                        listOf(UIMessagePart.Text(result.toJson()))
                    }

                    else -> listOf(UIMessagePart.Text(
                        """{"success":false,"error":"Unknown mode: $mode"}"""
                    ))
                }
            }
        )
    }

    val vibrateCancelTool by lazy {
        Tool(
            name = "vibrate_cancel",
            description = "Stop all ongoing vibration immediately. No parameters needed.",
            parameters = {
                InputSchema.Obj(properties = buildJsonObject { })
            },
            execute = {
                listOf(UIMessagePart.Text(vibrationManager.cancel().toJson()))
            }
        )
    }

    fun getTools(options: List<LocalToolOption>): List<Tool> {
        val tools = mutableListOf<Tool>()
        if (options.contains(LocalToolOption.JavascriptEngine)) {
            tools.add(javascriptTool)
        }
        if (options.contains(LocalToolOption.TimeInfo)) {
            tools.add(timeTool)
        }
        if (options.contains(LocalToolOption.Clipboard)) {
            tools.add(clipboardTool)
        }
        if (options.contains(LocalToolOption.Tts)) {
            tools.add(ttsTool)
        }
        if (options.contains(LocalToolOption.AskUser)) {
            tools.add(askUserTool)
        }
        if (options.contains(LocalToolOption.PhoneBridge)) {
            tools.addAll(phoneBridge.getAllTools())
        }
        if (options.contains(LocalToolOption.Weather)) {
            tools.add(weatherTool.tool)
        }
        // 感知数据查询工具总是可用
        if (perceptionStore != null) {
            tools.add(queryPerceptionTool)
        }
        if (options.contains(LocalToolOption.Vibrate)) {
            tools.add(vibrateTool)
            tools.add(vibrateCancelTool)
        }
        return tools
    }
}
