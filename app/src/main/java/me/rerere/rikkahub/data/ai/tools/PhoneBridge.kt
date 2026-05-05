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
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

private const val TAG = "PhoneBridge"

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
            description = "List files and directories at a specified path. Returns file names, sizes, and last modified timestamps. Read-only operations — no file content is read. Use '/' for the root of external storage, or use special paths like 'DOWNLOADS', 'DCIM', 'PICTURES', 'DOCUMENTS' to access app-specific storage directories.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "Directory path relative to external storage root. Use '/' for root, or 'DOWNLOADS', 'DCIM', 'PICTURES', 'DOCUMENTS' for standard directories.")
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

    val searchContactsTool by lazy {
        Tool(
            name = "search_contacts",
            description = "Search contacts by name. Returns matching contacts with their phone numbers. Requires READ_CONTACTS permission.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("name", buildJsonObject {
                            put("type", "string")
                            put("description", "Name to search for in contacts (partial match supported)")
                        })
                    },
                    required = listOf("name")
                )
            },
            execute = {
                val name = it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                    ?: error("name is required")
                handleSearchContacts(name)
            }
        )
    }

    val callContactTool by lazy {
        Tool(
            name = "call_contact_by_name",
            description = "Call a contact by their name. Searches contacts first, then opens dialer with the matching number. If multiple matches found, returns them instead of dialing. Requires READ_CONTACTS permission.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("contact_name", buildJsonObject {
                            put("type", "string")
                            put("description", "Name of the contact to call")
                        })
                    },
                    required = listOf("contact_name")
                )
            },
            needsApproval = true,
            execute = {
                val name = it.jsonObject["contact_name"]?.jsonPrimitive?.contentOrNull
                    ?: error("contact_name is required")
                handleCallContactByName(name)
            }
        )
    }

    val amapLinkTool by lazy {
        Tool(
            name = "amap_link",
            description = "Generate an Amap (高德地图) deeplink that the user can tap to open Amap directly. Can show a single location OR navigate from→to. IMPORTANT: Return the deeplink directly to the user as a normal message, DO NOT wrap it in a code block. Locations can be: coordinates ('lat,lng'), place name, or 'current' for current GPS location. Route type (for navigation): driving (default), transit, or walking.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("location", buildJsonObject {
                            put("type", "string")
                            put("description", "Single location to show: 'lat,lng' or place name. Use either this OR to/from_location, not both.")
                        })
                        put("from_location", buildJsonObject {
                            put("type", "string")
                            put("description", "Start location for navigation: 'lat,lng', place name, or 'current'. Use with to_location.")
                        })
                        put("to_location", buildJsonObject {
                            put("type", "string")
                            put("description", "End location for navigation: 'lat,lng', place name, or 'current'. Use with from_location.")
                        })
                        put("route_type", buildJsonObject {
                            put("type", "string")
                            put("description", "Route type (for navigation): 'driving' (default), 'transit', 'walking'")
                        })
                    }
                )
            },
            needsApproval = false,
            execute = {
                val location = it.jsonObject["location"]?.jsonPrimitive?.contentOrNull
                val fromLocation = it.jsonObject["from_location"]?.jsonPrimitive?.contentOrNull
                val toLocation = it.jsonObject["to_location"]?.jsonPrimitive?.contentOrNull
                val routeType = it.jsonObject["route_type"]?.jsonPrimitive?.contentOrNull ?: "driving"

                when {
                    location != null -> handleAmapShow(location)
                    fromLocation != null && toLocation != null -> handleAmapNavigate(fromLocation, toLocation, routeType)
                    else -> error("Either 'location' OR both 'from_location' and 'to_location' are required")
                }
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
        fileInfoTool,
        searchContactsTool,
        callContactTool,
        amapLinkTool
    )

    private fun handleVibrate(ms: Long): List<UIMessagePart> {
        Log.d(TAG, "handleVibrate: Vibrating for $ms ms")
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
        Log.d(TAG, "handleMakeCall: Opening dialer for number: $number")
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$number")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", true)
                    put("action", "dial_initiated")
                    put("number", number)
                    put("note", "Dialer opened. User must tap call button.")
                }.toString()
            ))
        } catch (e: Exception) {
            Log.e(TAG, "handleMakeCall: Failed to open dialer", e)
            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", "Failed to open dialer: ${e.message}")
                    put("number", number)
                }.toString()
            ))
        }
    }

    private suspend fun handleGetLocation(): List<UIMessagePart> {
        Log.d(TAG, "handleGetLocation: Starting location request")

        val permissionCheck = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "handleGetLocation: ACCESS_FINE_LOCATION permission not granted")
            return listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", "GPS permission (ACCESS_FINE_LOCATION) not granted. Please grant location permission in Settings -> Apps -> RikkaHub -> Permissions.")
                }.toString()
            ))
        }

        val apiKey = getAmapApiKey()
        Log.d(TAG, "handleGetLocation: API Key from settings: ${if (apiKey.isNullOrBlank()) "BLANK/NULL" else "first 8 chars: ${apiKey.take(8)}..."}")
        if (apiKey.isNullOrBlank()) {
            Log.w(TAG, "handleGetLocation: Amap API Key is not configured")
            return listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", "Amap API Key is not configured. Go to Settings -> Phone Hardware Bridge to set it.")
                }.toString()
            ))
        }

        Log.d(TAG, "handleGetLocation: Initializing Amap location client")

        return try {
            AMapLocationClient.setApiKey(apiKey)
            AMapLocationClient.updatePrivacyShow(context, true, true)
            AMapLocationClient.updatePrivacyAgree(context, true)
            Log.d(TAG, "handleGetLocation: Privacy agreements updated")

            val locationResult = suspendCancellableCoroutine { continuation ->
                val client = AMapLocationClient(context)
                val option = AMapLocationClientOption().apply {
                    locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                    isOnceLocation = true
                    isNeedAddress = true
                    httpTimeOut = 30000
                    isLocationCacheEnable = false
                }
                client.setLocationOption(option)
                client.setLocationListener { loc ->
                    Log.d(TAG, "handleGetLocation: Location listener called with loc=${loc?.hashCode()}")
                    if (loc != null) {
                        Log.d(TAG, "handleGetLocation: Got location result: errorCode=${loc.errorCode}, errorInfo=${loc.errorInfo}")
                        if (loc.errorCode == 0) {
                            Log.d(TAG, "handleGetLocation: Location success")
                            continuation.resume(Result.success(loc))
                        } else {
                            Log.e(TAG, "handleGetLocation: Location error ${loc.errorCode}: ${loc.errorInfo}")
                            continuation.resume(Result.failure(RuntimeException("Amap error ${loc.errorCode}: ${loc.errorInfo}")))
                        }
                    } else {
                        Log.w(TAG, "handleGetLocation: Location result is null")
                        continuation.resume(Result.failure(RuntimeException("Location result is null")))
                    }
                    client.stopLocation()
                    client.onDestroy()
                }
                client.startLocation()
                Log.d(TAG, "handleGetLocation: Location client started")

                continuation.invokeOnCancellation {
                    Log.d(TAG, "handleGetLocation: Location request cancelled")
                    client.stopLocation()
                    client.onDestroy()
                }
            }

            locationResult.fold(
                onSuccess = { location ->
                    Log.d(TAG, "handleGetLocation: Location success: lat=${location.latitude}, lng=${location.longitude}, address=${location.address}")
                    listOf(UIMessagePart.Text(
                        buildJsonObject {
                            put("success", true)
                            put("latitude", location.latitude)
                            put("longitude", location.longitude)
                            put("address", location.address)
                            put("poi_name", location.poiName)
                            put("city", location.city)
                            put("province", location.province)
                            put("country", location.country)
                            put("district", location.district)
                            put("street", location.street)
                            put("streetNum", location.streetNum)
                        }.toString()
                    ))
                },
                onFailure = { e ->
                    Log.e(TAG, "handleGetLocation: Failed to obtain GPS location", e)
                    listOf(UIMessagePart.Text(
                        buildJsonObject {
                            put("error", true)
                            put("message", "Failed to obtain GPS location: ${e.message}. Check that GPS is enabled and try again. Make sure Amap API Key is correct and has location permissions.")
                        }.toString()
                    ))
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "handleGetLocation: Exception during location request", e)
            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", "Location error: ${e.javaClass.simpleName}: ${e.message}")
                    put("stackTrace", Log.getStackTraceString(e).take(1000))
                }.toString()
            ))
        }
    }

    private suspend fun handleTakePhoto(): List<UIMessagePart> {
        Log.d(TAG, "handleTakePhoto: Requesting photo capture")
        return suspendCancellableCoroutine { continuation ->
            eventBus.emit(AppEvent.TakePhoto { uri ->
                if (uri != null) {
                    Log.d(TAG, "handleTakePhoto: Photo captured: $uri")
                    val payload = buildJsonObject {
                        put("success", true)
                        put("image_url", uri.toString())
                        put("note", "Image captured successfully. Displaying to model now.")
                    }
                    continuation.resume(
                        listOf(
                            UIMessagePart.Text(payload.toString()),
                            UIMessagePart.Image(uri.toString())
                        )
                    )
                } else {
                    Log.w(TAG, "handleTakePhoto: Photo capture cancelled or failed")
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
        Log.d(TAG, "handleOpenApp: Opening app/url: $id")
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
                        put("type", if (id.contains("://")) "url" else "package")
                    }.toString()
                ))
            } else {
                Log.w(TAG, "handleOpenApp: App not found or invalid URL: $id")
                listOf(UIMessagePart.Text(
                    buildJsonObject {
                        put("error", true)
                        put("message", "App not found or invalid URL: $id")
                    }.toString()
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleOpenApp: Failed to open app/url", e)
            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", "Failed to open: ${e.message}")
                    put("identifier", id)
                }.toString()
            ))
        }
    }

    private fun handleListFiles(relativePath: String): List<UIMessagePart> {
        Log.d(TAG, "handleListFiles: Listing files at path: $relativePath")

        val permissionCheck = ContextCompat.checkSelfPermission(
            context,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_IMAGES
            else
                Manifest.permission.READ_EXTERNAL_STORAGE
        )
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "handleListFiles: Storage permission not granted")
            return listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", "Storage read permission not granted.")
                }.toString()
            ))
        }

        val targetDir: File?
        val rootPath: String

        when (relativePath.uppercase()) {
            "DOWNLOADS", "DOWNLOAD" -> {
                targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                rootPath = targetDir?.absolutePath ?: ""
            }
            "DCIM" -> {
                targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DCIM)
                rootPath = targetDir?.absolutePath ?: ""
            }
            "PICTURES" -> {
                targetDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                rootPath = targetDir?.absolutePath ?: ""
            }
            "DOCUMENTS" -> {
                targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                rootPath = targetDir?.absolutePath ?: ""
            }
            else -> {
                val root = Environment.getExternalStorageDirectory()
                rootPath = root.absolutePath
                targetDir = if (relativePath == "/" || relativePath.isBlank()) root else File(root, relativePath)
            }
        }

        if (targetDir == null) {
            return listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", "Failed to access directory: $relativePath")
                    put("note", "Try using app-specific directories like DOWNLOADS, DCIM, PICTURES, or DOCUMENTS")
                }.toString()
            ))
        }

        Log.d(TAG, "handleListFiles: Resolved directory: ${targetDir.absolutePath}")

        if (!targetDir.exists() || !targetDir.isDirectory) {
            Log.w(TAG, "handleListFiles: Directory does not exist: ${targetDir.absolutePath}")
            return listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", "Directory does not exist: $relativePath")
                    put("resolved_path", targetDir.absolutePath)
                    put("note", "Try using app-specific directories like DOWNLOADS, DCIM, PICTURES, or DOCUMENTS")
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

        val displayPath = targetDir.absolutePath.removePrefix(rootPath).ifEmpty { "/" }

        Log.d(TAG, "handleListFiles: Found ${files.size} files in $displayPath")

        // List app-specific directories as options
        val appDirs = buildJsonArray {
            add(JsonPrimitive("DOWNLOADS"))
            add(JsonPrimitive("DCIM"))
            add(JsonPrimitive("PICTURES"))
            add(JsonPrimitive("DOCUMENTS"))
        }

        return listOf(UIMessagePart.Text(
            buildJsonObject {
                put("current_path", displayPath)
                put("resolved_path", targetDir.absolutePath)
                put("file_count", files.size)
                put("files", buildJsonArray { files.forEach { add(it) } })
                put("available_app_dirs", appDirs)
                put("note", "Due to scoped storage restrictions, use DOWNLOADS, DCIM, PICTURES, or DOCUMENTS paths for reliable file access. These are app-specific directories.")
            }.toString()
        ))
    }

    private fun handleReadFileInfo(relativePath: String): List<UIMessagePart> {
        Log.d(TAG, "handleReadFileInfo: Getting file info for: $relativePath")

        val permissionCheck = ContextCompat.checkSelfPermission(
            context,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_IMAGES
            else
                Manifest.permission.READ_EXTERNAL_STORAGE
        )
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "handleReadFileInfo: Storage permission not granted")
            return listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", "Storage read permission not granted.")
                }.toString()
            ))
        }

        val file: File?
        val rootPath: String

        when {
            relativePath.uppercase().startsWith("DOWNLOADS/") || relativePath.uppercase().startsWith("DOWNLOAD/") -> {
                val prefix = if (relativePath.uppercase().startsWith("DOWNLOADS/")) "DOWNLOADS/" else "DOWNLOAD/"
                val subPath = relativePath.substring(prefix.length)
                val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                file = if (baseDir != null) File(baseDir, subPath) else null
                rootPath = baseDir?.absolutePath ?: ""
            }
            relativePath.uppercase().startsWith("DCIM/") -> {
                val subPath = relativePath.substring("DCIM/".length)
                val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DCIM)
                file = if (baseDir != null) File(baseDir, subPath) else null
                rootPath = baseDir?.absolutePath ?: ""
            }
            relativePath.uppercase().startsWith("PICTURES/") -> {
                val subPath = relativePath.substring("PICTURES/".length)
                val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                file = if (baseDir != null) File(baseDir, subPath) else null
                rootPath = baseDir?.absolutePath ?: ""
            }
            relativePath.uppercase().startsWith("DOCUMENTS/") -> {
                val subPath = relativePath.substring("DOCUMENTS/".length)
                val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                file = if (baseDir != null) File(baseDir, subPath) else null
                rootPath = baseDir?.absolutePath ?: ""
            }
            else -> {
                val root = Environment.getExternalStorageDirectory()
                rootPath = root.absolutePath
                file = File(root, relativePath)
            }
        }

        if (file == null) {
            return listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", "Failed to access file: $relativePath")
                }.toString()
            ))
        }

        if (!file.exists()) {
            Log.w(TAG, "handleReadFileInfo: File not found: ${file.absolutePath}")
            return listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", "File not found: $relativePath")
                    put("resolved_path", file.absolutePath)
                }.toString()
            ))
        }

        Log.d(TAG, "handleReadFileInfo: Got info for: ${file.absolutePath}")

        return listOf(UIMessagePart.Text(
            buildJsonObject {
                put("name", file.name)
                put("size", file.length())
                put("last_modified", file.lastModified())
                put("is_file", file.isFile)
                put("is_directory", file.isDirectory)
                put("extension", file.extension)
                put("absolute_path", file.absolutePath)
                put("note", "File content reading is restricted to metadata for security.")
            }.toString()
        ))
    }

    private fun handleSearchContacts(name: String): List<UIMessagePart> {
        Log.d(TAG, "handleSearchContacts: Searching for: $name")

        val permissionCheck = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        )
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "handleSearchContacts: READ_CONTACTS permission not granted")
            return listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", "Contacts permission (READ_CONTACTS) not granted. Please grant contacts permission in Settings -> Apps -> RikkaHub -> Permissions.")
                }.toString()
            ))
        }

        val contacts = mutableListOf<JsonObject>()
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$name%")

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone._ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
            ),
            selection,
            selectionArgs,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

            while (it.moveToNext()) {
                val contactName = if (nameIndex >= 0) it.getString(nameIndex) ?: "" else ""
                val phoneNumber = if (numberIndex >= 0) it.getString(numberIndex) ?: "" else ""
                val photoUri = if (photoIndex >= 0) it.getString(photoIndex) ?: "" else ""

                contacts.add(buildJsonObject {
                    put("name", contactName)
                    put("phone_number", phoneNumber)
                    if (photoUri.isNotBlank()) {
                        put("photo_uri", photoUri)
                    }
                })
            }
        }

        Log.d(TAG, "handleSearchContacts: Found ${contacts.size} contacts")

        return listOf(UIMessagePart.Text(
            buildJsonObject {
                put("success", true)
                put("query", name)
                put("match_count", contacts.size)
                put("contacts", buildJsonArray { contacts.forEach { add(it) } })
            }.toString()
        ))
    }

    private fun handleCallContactByName(name: String): List<UIMessagePart> {
        Log.d(TAG, "handleCallContactByName: Calling contact: $name")

        val permissionCheck = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        )
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "handleCallContactByName: READ_CONTACTS permission not granted")
            return listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", "Contacts permission (READ_CONTACTS) not granted. Please grant contacts permission in Settings -> Apps -> RikkaHub -> Permissions.")
                }.toString()
            ))
        }

        val contacts = mutableListOf<Pair<String, String>>()
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$name%")

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            selection,
            selectionArgs,
            null
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val contactName = if (nameIndex >= 0) it.getString(nameIndex) ?: "" else ""
                val phoneNumber = if (numberIndex >= 0) it.getString(numberIndex) ?: "" else ""
                if (phoneNumber.isNotBlank()) {
                    contacts.add(contactName to phoneNumber)
                }
            }
        }

        Log.d(TAG, "handleCallContactByName: Found ${contacts.size} matches")

        return when {
            contacts.isEmpty() -> {
                listOf(UIMessagePart.Text(
                    buildJsonObject {
                        put("error", true)
                        put("message", "No contacts found matching: $name")
                        put("query", name)
                    }.toString()
                ))
            }
            contacts.size == 1 -> {
                val (contactName, phoneNumber) = contacts.first()
                Log.d(TAG, "handleCallContactByName: Single match: $contactName -> $phoneNumber")
                handleMakeCall(phoneNumber)
            }
            else -> {
                Log.d(TAG, "handleCallContactByName: Multiple matches, returning list")
                listOf(UIMessagePart.Text(
                    buildJsonObject {
                        put("error", true)
                        put("message", "Multiple contacts found. Please specify more clearly or use make_phone_call with a specific number.")
                        put("query", name)
                        put("match_count", contacts.size)
                        put("matches", buildJsonArray {
                            contacts.forEach { (cName, cNumber) ->
                                add(buildJsonObject {
                                    put("name", cName)
                                    put("phone_number", cNumber)
                                })
                            }
                        })
                    }.toString()
                ))
            }
        }
    }

    private suspend fun handleAmapNavigate(
        fromLocation: String,
        toLocation: String,
        routeType: String
    ): List<UIMessagePart> {
        Log.d(TAG, "handleAmapNavigate: from=$fromLocation, to=$toLocation, type=$routeType")

        // Parse from location
        val fromResult = parseLocation(fromLocation)
        if (fromResult.isFailure) {
            return listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", "Invalid from location: ${fromResult.exceptionOrNull()?.message}")
                }.toString()
            ))
        }

        // Parse to location
        val toResult = parseLocation(toLocation)
        if (toResult.isFailure) {
            return listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", "Invalid to location: ${toResult.exceptionOrNull()?.message}")
                }.toString()
            ))
        }

        val (fromLat, fromLng, fromName) = fromResult.getOrNull() ?: return listOf(UIMessagePart.Text(
            buildJsonObject { put("error", true); put("message", "Failed to parse from location") }.toString()
        ))

        val (toLat, toLng, toName) = toResult.getOrNull() ?: return listOf(UIMessagePart.Text(
            buildJsonObject { put("error", true); put("message", "Failed to parse to location") }.toString()
        ))

        // Build Amap URI - support both from and to coordinates in route plan
        val uri = buildString {
            if (fromLat != null && fromLng != null && toLat != null && toLng != null) {
                // Both coordinates available - use route plan
                append("androidamap://route?")
                append("sourceApplication=RikkaHub&")
                append("slat=$fromLat&")
                append("slon=$fromLng&")
                append("sname=${Uri.encode(fromName ?: "Start")}&")
                append("dlat=$toLat&")
                append("dlon=$toLng&")
                append("dname=${Uri.encode(toName ?: "Destination")}&")
                append("dev=0&")
                val t = when (routeType.lowercase()) {
                    "transit" -> 1
                    "walking" -> 2
                    else -> 0 // driving
                }
                append("t=$t")
            } else if (toLat != null && toLng != null) {
                // Destination coordinates available - use route plan (will use current location as start if no start coord)
                append("androidamap://route?")
                append("sourceApplication=RikkaHub&")
                if (fromLat != null && fromLng != null) {
                    append("slat=$fromLat&")
                    append("slon=$fromLng&")
                    if (!fromName.isNullOrBlank()) {
                        append("sname=${Uri.encode(fromName)}&")
                    }
                }
                append("dlat=$toLat&")
                append("dlon=$toLng&")
                append("dname=${Uri.encode(toName ?: "Destination")}&")
                append("dev=0&")
                val t = when (routeType.lowercase()) {
                    "transit" -> 1
                    "walking" -> 2
                    else -> 0 // driving
                }
                append("t=$t")
            } else {
                // Only place names - use keyword navigation with destination, user can select start point in app
                append("androidamap://keywordNavi?")
                append("sourceApplication=RikkaHub&")
                append("keyword=${Uri.encode(toName ?: toLocation)}&")
                append("style=2")
            }
        }
        Log.d(TAG, "handleAmapNavigate: built uri=$uri")

        return listOf(UIMessagePart.Text(uri))
    }

    private suspend fun handleAmapShow(
        location: String
    ): List<UIMessagePart> {
        Log.d(TAG, "handleAmapShow: location=$location")

        // Parse location
        val locationResult = parseLocation(location)
        if (locationResult.isFailure) {
            return listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("error", true)
                    put("message", "Invalid location: ${locationResult.exceptionOrNull()?.message}")
                }.toString()
            ))
        }

        val (lat, lng, name) = locationResult.getOrNull() ?: return listOf(UIMessagePart.Text(
            buildJsonObject { put("error", true); put("message", "Failed to parse location") }.toString()
        ))

        // Build Amap URI
        val uri = if (lat != null && lng != null) {
            "androidamap://viewMap?sourceApplication=RikkaHub&poiname=${Uri.encode(name ?: "Location")}&lat=$lat&lon=$lng&dev=0"
        } else {
            "androidamap://keywordNavi?sourceApplication=RikkaHub&keyword=${Uri.encode(name ?: location)}&style=2"
        }

        Log.d(TAG, "handleAmapShow: built uri=$uri")

        return listOf(UIMessagePart.Text(uri))
    }

    private suspend fun parseLocation(location: String): Result<Triple<Double?, Double?, String?>> {
        return try {
            when {
                location.equals("current", ignoreCase = true) -> {
                    // Fetch current location
                    val permissionCheck = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    )
                    if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                        return Result.failure(RuntimeException("GPS permission not granted"))
                    }

                    val apiKey = getAmapApiKey()
                    if (apiKey.isNullOrBlank()) {
                        return Result.failure(RuntimeException("Amap API key not configured"))
                    }

                    AMapLocationClient.setApiKey(apiKey)
                    AMapLocationClient.updatePrivacyShow(context, true, true)
                    AMapLocationClient.updatePrivacyAgree(context, true)

                    val locResult = suspendCancellableCoroutine { continuation ->
                        val client = AMapLocationClient(context)
                        val option = AMapLocationClientOption().apply {
                            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                            isOnceLocation = true
                            isNeedAddress = true
                            httpTimeOut = 30000
                            isLocationCacheEnable = false
                        }
                        client.setLocationOption(option)
                        client.setLocationListener { loc ->
                            if (loc != null && loc.errorCode == 0) {
                                continuation.resume(Result.success(loc))
                            } else {
                                continuation.resume(Result.failure(RuntimeException(loc?.errorInfo ?: "Failed to get location")))
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

                    locResult.fold(
                        onSuccess = { result ->
                            Result.success(Triple(result.latitude, result.longitude, result.address ?: "Current location"))
                        },
                        onFailure = { e ->
                            Result.failure(e)
                        }
                    )
                }

                location.contains(",") -> {
                    // Try parse as lat,lng
                    val parts = location.split(",")
                    if (parts.size >= 2) {
                        val lat = parts[0].trim().toDoubleOrNull()
                        val lng = parts[1].trim().toDoubleOrNull()
                        if (lat != null && lng != null) {
                            Result.success(Triple(lat, lng, null))
                        } else {
                            // Treat as place name
                            Result.success(Triple(null, null, location))
                        }
                    } else {
                        Result.success(Triple(null, null, location))
                    }
                }

                else -> {
                    // Treat as place name
                    Result.success(Triple(null, null, location))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseLocation: error", e)
            Result.failure(e)
        }
    }
}
