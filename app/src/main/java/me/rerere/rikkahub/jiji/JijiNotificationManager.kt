package me.rerere.rikkahub.jiji

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import me.rerere.rikkahub.R

/**
 * 唧唧的通知管理器
 *
 * 负责：
 * 1. 创建唧唧通知渠道
 * 2. 发送系统通知栏提醒（携带 conversationId 以支持点击跳转）
 * 3. 维护聊天列表的未读状态
 */
class JijiNotificationManager(private val context: Context) {
    companion object {
        private const val TAG = "JijiNotification"
        const val JIJI_NOTIFICATION_CHANNEL_ID = "jiji_proactive"
        const val JIJI_NOTIFICATION_ID = 3001

        const val PREF_JIJI_UNREAD_COUNT = "jiji_unread_count"
        const val PREF_JIJI_LAST_MESSAGE = "jiji_last_message"
        const val PREF_JIJI_CONVERSATION_ID = "jiji_conversation_id"

        /** Intent extra: 唧唧的会话 ID，通知点击后直接跳转到该会话 */
        const val EXTRA_JIJI_CONVERSATION_ID = "conversationId"
    }

    private val notificationManager = NotificationManagerCompat.from(context)

    fun createNotificationChannel() {
        val channel = NotificationChannelCompat
            .Builder(JIJI_NOTIFICATION_CHANNEL_ID, NotificationManager.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.notification_channel_jiji_proactive))
            .setDescription(context.getString(R.string.notification_channel_jiji_proactive_desc))
            .setVibrationEnabled(true)
            .setShowBadge(true)
            .build()
        notificationManager.createNotificationChannel(channel)
        Log.i(TAG, "Jiji notification channel created")
    }

    /**
     * 发送唧唧的主动搭话通知
     * @param message 搭话内容
     * @param conversationId 唧唧对话的 ID，点击通知将直接跳转到该会话
     */
    fun sendProactiveNotification(message: String, conversationId: String) {
        // 使用 Deep Link URI（rikkahub://chat?conversationId=xxx），比 intent extra 更可靠
        val deepLinkUri = android.net.Uri.parse("rikkahub://chat?conversationId=$conversationId")
        val intent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, JIJI_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle("唧唧")
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        notificationManager.notify(JIJI_NOTIFICATION_ID, notification)

        // 更新未读状态
        incrementUnreadCount(message, conversationId)
    }

    fun getUnreadCount(): Int {
        val prefs = context.getSharedPreferences("jiji", Context.MODE_PRIVATE)
        return prefs.getInt(PREF_JIJI_UNREAD_COUNT, 0)
    }

    private fun incrementUnreadCount(message: String, conversationId: String) {
        val prefs = context.getSharedPreferences("jiji", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(PREF_JIJI_UNREAD_COUNT, getUnreadCount() + 1)
            .putString(PREF_JIJI_LAST_MESSAGE, message)
            .putString(PREF_JIJI_CONVERSATION_ID, conversationId)
            .apply()
    }

    /** 用户打开唧唧对话后调用，清空未读 */
    fun clearUnread() {
        val prefs = context.getSharedPreferences("jiji", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(PREF_JIJI_UNREAD_COUNT, 0)
            .apply()
        notificationManager.cancel(JIJI_NOTIFICATION_ID)
    }

    fun getLastMessagePreview(): String? {
        val prefs = context.getSharedPreferences("jiji", Context.MODE_PRIVATE)
        return prefs.getString(PREF_JIJI_LAST_MESSAGE, null)
    }

    fun getConversationId(): String? {
        val prefs = context.getSharedPreferences("jiji", Context.MODE_PRIVATE)
        return prefs.getString(PREF_JIJI_CONVERSATION_ID, null)
    }
}
