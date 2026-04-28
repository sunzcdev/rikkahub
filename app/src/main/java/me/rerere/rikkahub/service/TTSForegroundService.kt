package me.rerere.rikkahub.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.TTS_PLAYBACK_NOTIFICATION_CHANNEL_ID
import me.rerere.tts.controller.TtsController
import me.rerere.tts.model.PlaybackStatus
import org.koin.android.ext.android.inject

private const val TAG = "TTSForegroundService"

class TTSForegroundService : Service() {

    companion object {
        const val ACTION_PLAY_PAUSE = "me.rerere.rikkahub.action.TTS_PLAY_PAUSE"
        const val ACTION_STOP = "me.rerere.rikkahub.action.TTS_STOP"
        const val ACTION_RESTART = "me.rerere.rikkahub.action.TTS_RESTART"
        const val NOTIFICATION_ID = 3001

        fun createIntent(
            context: android.content.Context,
            action: String,
        ): Intent {
            return Intent(context, TTSForegroundService::class.java).apply {
                this.action = action
            }
        }
    }

    private val ttsController: TtsController by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        observePlaybackState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                val state = ttsController.playbackState.value
                if (state.status == PlaybackStatus.Paused) {
                    ttsController.resume()
                } else {
                    ttsController.pause()
                }
            }

            ACTION_STOP -> {
                ttsController.stop()
                stopForegroundCompat()
            }

            ACTION_RESTART -> {
                ttsController.stop()
                val provider = ttsController.playbackState.value.currentChunkIndex
                // Re-speak from beginning handled by caller — service just triggers
            }

            else -> {
                startForegroundCompat()
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed — keeping foreground service alive")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
    }

    private fun startForegroundCompat() {
        val notification = buildNotification(
            title = getString(R.string.notification_channel_tts_playback),
            text = "",
            isOngoing = true,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun observePlaybackState() {
        serviceScope.launch {
            ttsController.playbackState
                .map { it.status }
                .distinctUntilChanged()
                .collect { status ->
                    when (status) {
                        PlaybackStatus.Playing -> {
                            startForegroundCompat()
                            updateNotification(
                                buildNotification(
                                    title = getString(R.string.notification_channel_tts_playback),
                                    text = "",
                                    isOngoing = true,
                                )
                            )
                        }

                        PlaybackStatus.Paused -> {
                            updateNotification(
                                buildNotification(
                                    title = getString(R.string.tts),
                                    text = getString(R.string.tts_paused),
                                    isOngoing = true,
                                )
                            )
                        }

                        PlaybackStatus.Ended, PlaybackStatus.Idle, PlaybackStatus.Error -> {
                            stopForegroundCompat()
                        }

                        else -> { /* Buffering, Playing — keep current notification */ }
                    }
                }
        }
    }

    private fun buildNotification(title: String, text: String, isOngoing: Boolean): android.app.Notification {
        val playPauseIntent = PendingIntent.getService(
            this,
            0,
            createIntent(this, ACTION_PLAY_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            createIntent(this, ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val restartIntent = PendingIntent.getService(
            this,
            2,
            createIntent(this, ACTION_RESTART),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val state = ttsController.playbackState.value
        val playPauseLabel = if (state.status == PlaybackStatus.Paused) {
            getString(R.string.tts_resume)
        } else {
            getString(R.string.tts_pause)
        }

        return NotificationCompat.Builder(this, TTS_PLAYBACK_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(isOngoing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(0, playPauseLabel, playPauseIntent)
            .addAction(0, getString(R.string.tts_stop), stopIntent)
            .addAction(0, getString(R.string.tts_restart), restartIntent)
            .build()
    }

    private fun updateNotification(notification: android.app.Notification) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
