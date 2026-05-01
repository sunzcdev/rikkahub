package me.rerere.rikkahub.data.ai.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import kotlinx.coroutines.suspendCancellableCoroutine
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
    val vibrateTool by lazy {
        Tool(
            name = "vibrate_device",
            description = "Trigger phone vibration. Specify duration in milliseconds (default 500ms).",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("duration_ms", buildJsonObject {
                            put("type", "integer")
                            put("description", "Vibration duration in milliseconds (default 500)")
                        })
                    }
                )
            },
            execute = {
                val ms = it.jsonObject["duration_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 500L
                handleVibrate(ms)
            }
        )
    }

    val callTool by lazy {
        Tool(
            name = "make_phone_call",
            description = "Open the phone dialer with a specified phone number. The user must confirm before the call is placed.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("phone_number", buildJsonObject {
                            put("type", "string")
                            put("description", "The phone number to dial")
                        })
                    },
                    required = listOf("phone_number")
                )
            },
            needsApproval = true,
            execute = {
                val number = it.jsonObject["phone_number"]?.jsonPrimitive?.contentOrNull
                    ?: error("phone_number is required")
                handleMakeCall(number)
            }
        )
    }

    val locationTool by lazy {
        Tool(
            name = "get_current_location",
            description = "Get the device's current GPS coordinates and address information using Amap (高德) location service. Returns latitude, longitude, address, city, and province. Requires Amap API key configured in settings and ACCESS_FINE_LOCATION permission.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject { }
                )
            },
            execute = {
                handleGetLocation()
            }
        )
    }

    val photoTool by lazy {
        Tool(
            name = "take_photo_camera",
            description = "Launch the system camera app to take a photo. The captured image will be returned and displayed in the chat.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject { }
                )
            },
            execute = {
                handleTakePhoto()
            }
        )
    }

    val openAppTool by lazy {
        Tool(
            name = "open_external_app",
            description = "Open an external app by package name (e.g., 'com.whatsapp') or a deep-link URL (e.g., 'https://...' or 'tel:123').",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("app_identifier", buildJsonObject {
                            put("type", "string")
                            put("description", "Package name or deep-link URL to open")
                        })
                    },
                    required = listOf("app_identifier")
                )
            },
            execute = {
                val id = it.jsonObject["app_identifier"]?.jsonPrimitive?.contentOrNull
                    ?: error("app_identifier is required")
                handleOpenApp(id)
            }
        )
    }

    val listFilesTool by lazy {
        Tool(
            name = "list_directory_contents",
            description = "List files and directories at a specified path. Returns file names, sizes, and last modified timestamps. Read-only operations — no file content is read. Use '/' for the root of external storage.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "Directory path relative to external storage root. Use '/' for root.")
                        })
                    },
                    required = listOf("path")
                )
            },
            execute = {
                val path = it.jsonObject["path"]?.jsonPrimitive?.contentOrNull ?: "/"
                handleListFiles(path)
            }
        )
    }

    val fileInfoTool by lazy {
        Tool(
            name = "get_file_info",
            description = "Get metadata about a specific file or directory: name, size, last modified, type. Read-only — no file content is read.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "File path relative to external storage root")
                        })
                    },
                    required = listOf("path")
                )
            },
            execute = {
                val path = it.jsonObject["path"]?.jsonPrimitive?.contentOrNull
                    ?: error("path is required")
                handleReadFileInfo(path)
            }
        )
    }

    fun getAllTools(): List<Tool> = listOf(
        vibrateTool,
        callTool,
        locationTool,
        photoTool,
        openAppTool,
        listFilesTool,
        fileInfoTool
    )

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

        return listOf(UIMessagePart.Text(
            buildJsonObject {
                put("success", true)
                put("duration_ms", ms)
            }.toString()
        ))
    }

    private fun handleMakeCall(number: String): List<UIMessagePart> {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$number")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return listOf(UIMessagePart.Text(
            buildJsonObject {
                put("success", true)
                put("action", "dial_initiated")
                put("number", number)
                put("note", "Dialer opened. User must tap call button.")
            }.toString()
        ))
    }

    private suspend fun handleGetLocation(): List<UIMessagePart> {
        val permissionCheck = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            return listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", "GPS permission (ACCESS_FINE_LOCATION) not granted. Please grant location permission in Settings -> Apps -> RikkaHub -> Permissions.")
                }.toString()
            ))
        }

        val apiKey = getAmapApiKey()
        if (apiKey.isNullOrBlank()) {
            return listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", "Amap API Key is not configured. Go to Settings -> Phone Hardware Bridge to set it.")
                }.toString()
            ))
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
                listOf(UIMessagePart.Text(
                    buildJsonObject {
                        put("success", true)
                        put("latitude", location.latitude)
                        put("longitude", location.longitude)
                        put("address", location.address)
                        put("poi_name", location.poiName)
                        put("city", location.city)
                        put("province", location.province)
                    }.toString()
                ))
            } else {
                listOf(UIMessagePart.Text(
                    buildJsonObject {
                        put("error", true)
                        put("message", "Failed to obtain GPS location. Check that GPS is enabled and try again.")
                    }.toString()
                ))
            }
        } catch (e: Exception) {
            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", "Location error: ${e.message}")
                }.toString()
            ))
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
                    continuation.resume(
                        listOf(
                            UIMessagePart.Text(payload.toString()),
                            UIMessagePart.Image(uri.toString())
                        )
                    )
                } else {
                    continuation.resume(
                        listOf(UIMessagePart.Text(
                            buildJsonObject {
                                put("error", true)
                                put("message", "Camera capture was cancelled or failed.")
                            }.toString()
                        ))
                    )
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
                listOf(UIMessagePart.Text(
                    buildJsonObject {
                        put("success", true)
                        put("launched", id)
                    }.toString()
                ))
            } else {
                listOf(UIMessagePart.Text(
                    buildJsonObject {
                        put("error", true)
                        put("message", "App not found or invalid URL: $id")
                    }.toString()
                ))
            }
        } catch (e: Exception) {
            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", e.message)
                }.toString()
            ))
        }
    }

    private fun handleListFiles(relativePath: String): List<UIMessagePart> {
        val permissionCheck = ContextCompat.checkSelfPermission(
            context,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_IMAGES
            else
                Manifest.permission.READ_EXTERNAL_STORAGE
        )
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            return listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", "Storage read permission not granted.")
                }.toString()
            ))
        }

        val root = Environment.getExternalStorageDirectory()
        val targetDir = if (relativePath == "/" || relativePath.isBlank()) root else File(root, relativePath)

        if (!targetDir.exists() || !targetDir.isDirectory) {
            return listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", "Directory does not exist: $relativePath")
                }.toString()
            ))
        }

        val files = targetDir.listFiles()?.map { file ->
            buildJsonObject {
                put("name", file.name)
                put("is_directory", file.isDirectory)
                put("size", file.length())
                put("last_modified", file.lastModified())
            }
        } ?: emptyList()

        return listOf(UIMessagePart.Text(
            buildJsonObject {
                put("current_path", targetDir.absolutePath.removePrefix(root.absolutePath).ifEmpty { "/" })
                put("file_count", files.size)
                put("files", buildJsonArray { files.forEach { add(it) } })
            }.toString()
        ))
    }

    private fun handleReadFileInfo(relativePath: String): List<UIMessagePart> {
        val permissionCheck = ContextCompat.checkSelfPermission(
            context,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_IMAGES
            else
                Manifest.permission.READ_EXTERNAL_STORAGE
        )
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            return listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", "Storage read permission not granted.")
                }.toString()
            ))
        }

        val root = Environment.getExternalStorageDirectory()
        val file = File(root, relativePath)

        if (!file.exists()) {
            return listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", "File not found: $relativePath")
                }.toString()
            ))
        }

        return listOf(UIMessagePart.Text(
            buildJsonObject {
                put("name", file.name)
                put("size", file.length())
                put("last_modified", file.lastModified())
                put("is_file", file.isFile)
                put("extension", file.extension)
                put("note", "File content reading is restricted to metadata for security.")
            }.toString()
        ))
    }
}
