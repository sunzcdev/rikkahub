package me.rerere.rikkahub.data.export

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.perception.PerceptionMemory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object PerceptionMemorySerializer : ExportSerializer<PerceptionMemory> {
    override val type = "perception_memory"

    override fun getExportFileName(data: PerceptionMemory): String {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"))
        return "perception_$now.json"
    }

    override fun export(data: PerceptionMemory): ExportData {
        return ExportData(
            version = 1,
            type = type,
            data = DefaultJson.encodeToJsonElement(PerceptionMemory.serializer(), data),
        )
    }

    override fun import(context: Context, uri: Uri): Result<PerceptionMemory> {
        return runCatching {
            val json = readUri(context, uri)
            tryImportNative(json)
                ?: throw IllegalArgumentException("Unsupported format")
        }
    }

    private fun tryImportNative(json: String): PerceptionMemory? {
        return runCatching {
            // 先尝试包装格式
            val exportData = DefaultJson.decodeFromString(
                ExportData.serializer(), json
            )
            if (exportData.type != type) return null
            DefaultJson.decodeFromJsonElement(
                PerceptionMemory.serializer(), exportData.data
            )
        }.getOrNull() ?: runCatching {
            // 再尝试裸格式（直接 PerceptionMemory JSON）
            DefaultJson.decodeFromString(PerceptionMemory.serializer(), json)
        }.getOrNull()
    }

    private val DefaultJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
}
