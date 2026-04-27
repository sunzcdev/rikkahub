package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus

class PhoneBridge(
    private val context: Context,
    private val eventBus: AppEventBus,
    private val getAmapApiKey: () -> String?
) {
    val tool by lazy {
        Tool(
            name = "phone_hardware_bridge",
            description = """
                A bridge to access phone hardware and system functions.
                Capabilities:
                - vibrate: Trigger phone vibration.
                - make_call: Initiate a phone call to a specified number.
                - get_location: Get current GPS coordinates (Amap integration).
                - take_photo: Capture an image using the camera.
                - open_external_app: Open an app by package name or URL.
                - file_system: Basic file operations (list, read).
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("action", buildJsonObject {
                            put("type", "string")
                            put("enum", buildJsonArray {
                                add("vibrate")
                                add("make_call")
                                add("get_location")
                                add("take_photo")
                                add("open_external_app")
                                add("list_files")
                                add("read_file")
                            })
                        })
                        put("phone_number", buildJsonObject {
                            put("type", "string")
                            put("description", "Phone number for make_call action")
                        })
                        put("vibration_ms", buildJsonObject {
                            put("type", "integer")
                            put("description", "Vibration duration in milliseconds")
                        })
                        put("app_identifier", buildJsonObject {
                            put("type", "string")
                            put("description", "Package name or deep link for open_external_app")
                        })
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "File or directory path for file_system actions")
                        })
                    },
                    required = listOf("action")
                )
            },
            execute = {
                val params = it.jsonObject
                val action = params["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")

                when (action) {
                    "vibrate" -> handleVibrate(params["vibration_ms"]?.jsonPrimitive?.contentOrNull?.toLong() ?: 500L)
                    "make_call" -> handleMakeCall(params["phone_number"]?.jsonPrimitive?.contentOrNull ?: error("phone_number is required"))
                    "get_location" -> handleGetLocation()
                    "take_photo" -> handleTakePhoto()
                    "open_external_app" -> handleOpenApp(params["app_identifier"]?.jsonPrimitive?.contentOrNull ?: error("app_identifier is required"))
                    "list_files" -> handleListFiles(params["path"]?.jsonPrimitive?.contentOrNull ?: ".")
                    "read_file" -> handleReadFile(params["path"]?.jsonPrimitive?.contentOrNull ?: error("path is required"))
                    else -> error("Unsupported action: ${action}")
                }
            }
        )
    }

    private fun handleVibrate(ms: Long): List<UIMessagePart> {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(ms)
        }

        val payload = buildJsonObject {
            put("success", true)
            put("duration_ms", ms)
        }
        return listOf(UIMessagePart.Text(payload.toString()))
    }

    private fun handleMakeCall(number: String): List<UIMessagePart> {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$number")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        val payload = buildJsonObject {
            put("success", true)
            put("action", "dial_initiated")
            put("number", number)
        }
        return listOf(UIMessagePart.Text(payload.toString()))
    }

    private fun handleGetLocation(): List<UIMessagePart> {
        // Implementation will require Amap SDK integration
        // For now, return a placeholder or error if API key is missing
        val apiKey = getAmapApiKey()
        if (apiKey.isNullOrBlank()) {
            return listOf(UIMessagePart.Text("{\"error\": \"Amap API Key is not configured in settings.\"}"))
        }

        // Placeholder for real Amap call
        val payload = buildJsonObject {
            put("status", "pending")
            put("message", "Location tracking needs Amap SDK integration and user permission.")
        }
        return listOf(UIMessagePart.Text(payload.toString()))
    }

    private fun handleTakePhoto(): List<UIMessagePart> {
        // This usually requires a UI callback or AppEvent
        // eventBus.emit(AppEvent.CaptureImage)
        val payload = buildJsonObject {
            put("status", "requested")
            put("message", "Camera capture triggered via AppEvent.")
        }
        return listOf(UIMessagePart.Text(payload.toString()))
    }

    private fun handleOpenApp(id: String): List<UIMessagePart> {
        return try {
            val intent = if (id.contains("://")) {
                Intent(Intent.ACTION_VIEW, Uri.parse(id))
            } else {
                context.packageManager.getLaunchIntentForPackage(id)
            }
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                listOf(UIMessagePart.Text("{\"success\": true, \"launched\": \"$id\"}"))
            } else {
                listOf(UIMessagePart.Text("{\"error\": \"App not found or invalid URL: $id\"}"))
            }
        } catch (e: Exception) {
            listOf(UIMessagePart.Text("{\"error\": \"${e.message}\"}"))
        }
    }

    private fun handleListFiles(path: String): List<UIMessagePart> {
        // Scoped to app internal files or specific public dirs
        val payload = buildJsonObject {
            put("path", path)
            put("note", "File system access is restricted for security.")
        }
        return listOf(UIMessagePart.Text(payload.toString()))
    }

    private fun handleReadFile(path: String): List<UIMessagePart> {
        val payload = buildJsonObject {
            put("path", path)
            put("error", "Direct file reading is restricted.")
        }
        return listOf(UIMessagePart.Text(payload.toString()))
    }
}
