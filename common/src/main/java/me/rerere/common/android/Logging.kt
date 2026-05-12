package me.rerere.common.android

import android.content.Context
import android.util.Log as AndroidLog
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_RECENT_LOGS = 2000
private const val LOG_FILE_MAX_SIZE = 5 * 1024 * 1024L  // 5MB per file, 保留最近的3份

@Serializable
sealed class LogEntry {
    abstract val id: Uuid
    abstract val timestamp: Long
    abstract val tag: String

    @Serializable
    data class TextLog(
        override val id: Uuid = Uuid.random(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val tag: String,
        val level: String = "D", // D/I/W/E/V
        val message: String,
    ) : LogEntry()

    @Serializable
    data class RequestLog(
        override val id: Uuid = Uuid.random(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val tag: String,
        val url: String,
        val method: String,
        val requestHeaders: Map<String, String> = emptyMap(),
        val requestBody: String? = null,
        val responseCode: Int? = null,
        val responseHeaders: Map<String, String> = emptyMap(),
        val durationMs: Long? = null,
        val error: String? = null,
    ) : LogEntry()
}

/**
 * 统一日志系统。
 * - 写入 [android.util.Log] 输出到 logcat
 * - 同时缓存到内存环中，供 [LogPage] 在 App 内查看
 * - 支持导出全部日志为文本
 */
object Logging {
    private val recentLogs = arrayListOf<LogEntry>()
    private var logDir: File? = null

    /** 初始化日志文件目录（在 Application.onCreate 中调用） */
    fun init(context: Context) {
        logDir = File(context.filesDir, "logs").also { it.mkdirs() }
        // 清理旧日志，只保留最近 3 个文件
        cleanupOldLogFiles()
    }

    /** 获取日志文件列表（按修改时间倒序） */
    fun getLogFiles(): List<File> {
        return logDir?.listFiles()
            ?.filter { it.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /** 获取当前日志写入的文件路径 */
    fun getCurrentLogFile(): File? {
        val dir = logDir ?: return null
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return File(dir, "rikkahub_$today.log")
    }

    private fun cleanupOldLogFiles() {
        val dir = logDir ?: return
        val files = dir.listFiles()
            ?.filter { it.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        // 保留最近 3 个文件，删更旧的
        if (files.size > 3) {
            files.drop(3).forEach { it.delete() }
        }
    }

    // ── 便捷方法：替代 android.util.Log ──

    fun d(tag: String, msg: String) {
        AndroidLog.d(tag, msg)
        addLog(LogEntry.TextLog(tag = tag, level = "D", message = msg))
    }

    fun d(tag: String, msg: String, throwable: Throwable?) {
        AndroidLog.d(tag, msg, throwable)
        val detail = throwable?.let { "${it::class.simpleName}: ${it.message}" } ?: "null"
        addLog(LogEntry.TextLog(tag = tag, level = "D", message = "$msg | $detail"))
    }

    fun i(tag: String, msg: String) {
        AndroidLog.i(tag, msg)
        addLog(LogEntry.TextLog(tag = tag, level = "I", message = msg))
    }

    fun w(tag: String, msg: String) {
        AndroidLog.w(tag, msg)
        addLog(LogEntry.TextLog(tag = tag, level = "W", message = msg))
    }

    fun w(tag: String, msg: String, throwable: Throwable?) {
        AndroidLog.w(tag, msg, throwable)
        val detail = throwable?.let { "${it::class.simpleName}: ${it.message}" } ?: "null"
        addLog(LogEntry.TextLog(tag = tag, level = "W", message = "$msg | $detail"))
    }

    fun e(tag: String, msg: String) {
        AndroidLog.e(tag, msg)
        addLog(LogEntry.TextLog(tag = tag, level = "E", message = msg))
    }

    fun e(tag: String, msg: String, throwable: Throwable?) {
        AndroidLog.e(tag, msg, throwable)
        val detail = throwable?.let { "${it::class.simpleName}: ${it.message}" } ?: "null"
        addLog(LogEntry.TextLog(tag = tag, level = "E", message = "$msg | $detail"))
    }

    fun v(tag: String, msg: String) {
        AndroidLog.v(tag, msg)
        addLog(LogEntry.TextLog(tag = tag, level = "V", message = msg))
    }

    // ── 原始方法 ──

    fun log(tag: String, message: String) {
        AndroidLog.d(tag, message)
        addLog(LogEntry.TextLog(tag = tag, level = "D", message = message))
    }

    fun logRequest(entry: LogEntry.RequestLog) {
        addLog(entry)
    }

    private fun addLog(entry: LogEntry) {
        synchronized(recentLogs) {
            recentLogs.add(0, entry)
            if (recentLogs.size > MAX_RECENT_LOGS) {
                recentLogs.removeLastOrNull()
            }
        }
        // 写入文件
        writeToFile(entry)
    }

    fun getRecentLogs(): List<LogEntry> {
        synchronized(recentLogs) {
            return recentLogs.toList()
        }
    }

    fun getTextLogs(): List<LogEntry.TextLog> {
        synchronized(recentLogs) {
            return recentLogs.filterIsInstance<LogEntry.TextLog>()
        }
    }

    fun getRequestLogs(): List<LogEntry.RequestLog> {
        synchronized(recentLogs) {
            return recentLogs.filterIsInstance<LogEntry.RequestLog>()
        }
    }

    /** 写入一条日志到文件 */
    private fun writeToFile(entry: LogEntry) {
        val file = getCurrentLogFile() ?: return

        // 超过大小上限则滚动：重命名为 .old（用 next available 后缀）
        if (file.length() > LOG_FILE_MAX_SIZE) {
            var seq = 1
            while (File(file.parentFile, "${file.nameWithoutExtension}.$seq.log").exists()) {
                seq++
            }
            file.renameTo(File(file.parentFile, "${file.nameWithoutExtension}.$seq.log"))
        }

        val time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
            .format(Date(entry.timestamp))
        val line = when (entry) {
            is LogEntry.TextLog ->
                "$time ${entry.level}/${entry.tag}: ${entry.message}\n"
            is LogEntry.RequestLog ->
                "$time ${entry.method} ${entry.url} [${entry.responseCode ?: "?"}] ${entry.durationMs?.let { "${it}ms" } ?: ""}\n"
        }
        file.appendText(line)
    }

    fun clear() {
        synchronized(recentLogs) {
            recentLogs.clear()
        }
        // 同时清理日志文件
        logDir?.listFiles()
            ?.filter { it.name.endsWith(".log") }
            ?.forEach { it.delete() }
        AndroidLog.d("Logging", "Log buffer cleared")
    }

    /** 导出全部日志为纯文本 */
    fun exportAsText(): String {
        val logs = getRecentLogs()
        val sb = StringBuilder()
        sb.appendLine("=== RikkaHub Logs ===")
        sb.appendLine("Exported: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
        sb.appendLine("Total entries: ${logs.size}")
        sb.appendLine("======================")
        sb.appendLine()
        for (log in logs) {
            val time = java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault())
                .format(java.util.Date(log.timestamp))
            when (log) {
                is LogEntry.TextLog -> {
                    sb.appendLine("$time ${log.level}/${log.tag}: ${log.message}")
                }
                is LogEntry.RequestLog -> {
                    sb.appendLine("$time ${log.method} ${log.url} [${log.responseCode ?: "?"}] ${log.durationMs?.let { "${it}ms" } ?: ""}")
                    log.error?.let { sb.appendLine("  ERROR: $it") }
                }
            }
        }
        return sb.toString()
    }
}
