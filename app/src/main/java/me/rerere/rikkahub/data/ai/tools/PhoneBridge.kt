package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import kotlinx.coroutines.suspendCancellableCoroutine
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
import java.io.File
import kotlin.coroutines.resume

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
                - get_location: Get current GPS coordinates (using Amap).
                - take_photo: Capture an image using the camera.
                - open_external_app: Open an app by package name or URL.
                - list_files: List contents of a directory (Read-only).
                - read_file_info: Get metadata/basic info about a file (Read-only).
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
                                add("read_file_info")
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
                            put("description", "File or directory path for file_system actions. Use '/' for root of allowed storage.")
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
                    "list_files" -> handleListFiles(params["path"]?.jsonPrimitive?.contentOrNull ?: "/")
                    "read_file_info" -> handleReadFileInfo(params["path"]?.jsonPrimitive?.contentOrNull ?: error("path is required"))
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

    private suspend fun handleGetLocation(): List<UIMessagePart> {
        val apiKey = getAmapApiKey()
        if (apiKey.isNullOrBlank()) {
            return listOf(UIMessagePart.Text("{\"error\": \"Amap API Key is not configured in settings. Go to Settings -> Phone Hardware Bridge to set it.\"}"))
        }

        return try {
            AMapLocationClient.setApiKey(apiKey)
            AMapLocationClient.updatePrivacyShow(context, true, true)
            AMapLocationClient.updatePrivacyAgree(context, true)

            val location = suspendCancellableCoroutine { continuation ->
                val client = AMapLocationClient(context)
                val option = AMapLocationClientOption().apply {
                    locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                    isOnceLocation = true
                    isNeedAddress = true
                }
                client.setLocationOption(option)
                client.setLocationListener { loc ->
                    if (loc != null && loc.errorCode == 0) {
                        continuation.resume(loc)
                    } else {
                        continuation.resume(null)
                    }
                    client.stopLocation()
                    client.onDestroy()
                }
                client.startLocation()

                continuation.invokeOnCancellation {
                    client.stopLocation()
                    client.onDestroy()
                }
            }

            if (location != null) {
                val payload = buildJsonObject {
                    put("latitude", location.latitude)
                    put("longitude", location.longitude)
                    put("address", location.address)
                    put("poi_name", location.poiName)
                    put("city", location.city)
                    put("province", location.province)
                    put("success", true)
                }
                listOf(UIMessagePart.Text(payload.toString()))
            } else {
                listOf(UIMessagePart.Text("{\"error\": \"Failed to obtain GPS location. Check permissions and API key.\"}"))
            }
        } catch (e: Exception) {
            listOf(UIMessagePart.Text("{\"error\": \"Location error: ${e.message}\"}"))
        }
    }

    private suspend fun handleTakePhoto(): List<UIMessagePart> {
        return suspendCancellableCoroutine { continuation ->
            eventBus.emit(AppEvent.TakePhoto { uri ->
                if (uri != null) {
                    val payload = buildJsonObject {
                        put("success", true)
                        put("image_url", uri.toString())
                    }
                    continuation.resume(listOf(UIMessagePart.Text(payload.toString()), UIMessagePart.Image(uri.toString())))
                } else {
                    continuation.resume(listOf(UIMessagePart.Text("{\"error\": \"User cancelled camera capture or error occurred.\"}")))
                }
            })
        }
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

    private fun handleListFiles(relativePath: String): List<UIMessagePart> {
        val root = Environment.getExternalStorageDirectory()
        val targetDir = if (relativePath == "/" || relativePath.isBlank()) root else File(root, relativePath)

        if (!targetDir.exists() || !targetDir.isDirectory) {
            return listOf(UIMessagePart.Text("{\"error\": \"Path does not exist or is not a directory.\"}"))
        }

        val files = targetDir.listFiles()?.map { file ->
            buildJsonObject {
                put("name", file.name)
                put("is_directory", file.isDirectory)
                put("size", file.length())
                put("last_modified", file.lastModified())
            }
        } ?: emptyList()

        val payload = buildJsonObject {
            put("current_path", targetDir.absolutePath.replace(root.absolutePath, ""))
            put("files", buildJsonArray { files.forEach { add(it) } })
        }
        return listOf(UIMessagePart.Text(payload.toString()))
    }

    private fun handleReadFileInfo(relativePath: String): List<UIMessagePart> {
        val root = Environment.getExternalStorageDirectory()
        val file = File(root, relativePath)

        if (!file.exists()) {
            return listOf(UIMessagePart.Text("{\"error\": \"File not found.\"}"))
        }

        val payload = buildJsonObject {
            put("name", file.name)
            put("size", file.length())
            put("last_modified", file.lastModified())
            put("is_file", file.isFile)
            put("extension", file.extension)
            put("note", "File content reading is restricted to metadata for security.")
        }
        return listOf(UIMessagePart.Text(payload.toString()))
    }
}
