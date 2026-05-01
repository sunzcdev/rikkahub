package me.rerere.rikkahub.ui.components.voice

import android.Manifest
import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getSelectedSTTProvider
import me.rerere.rikkahub.ui.components.ui.permission.PermissionMicrophone
import me.rerere.rikkahub.ui.components.ui.permission.PermissionStatus
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.utils.AudioRecorder
import me.rerere.stt.model.SttAudioFormat
import me.rerere.stt.provider.SttManager
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiMagic
import org.koin.compose.koinInject

private const val TAG = "VoiceInputButton"

enum class VoiceInputState {
    Idle,
    Recording,
    Processing,
}

@SuppressLint("MissingPermission")
@Composable
fun VoiceInputButton(
    onVoiceResult: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsStore: SettingsStore = koinInject()
    val sttManager: SttManager = koinInject()
    val scope = rememberCoroutineScope()

    var voiceState by remember { mutableStateOf(VoiceInputState.Idle) }
    val audioRecorder = remember { AudioRecorder() }

    DisposableEffect(Unit) {
        onDispose {
            audioRecorder.cancel()
        }
    }

    val micPermissionState = rememberPermissionState(PermissionMicrophone)

    val rippleColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(36.dp)
            .drawWithCache {
                onDrawBehind {
                    drawCircle(
                        color = if (voiceState == VoiceInputState.Recording) rippleColor.copy(alpha = 0.15f) else bgColor,
                        radius = size.minDimension / 2,
                        center = Offset(size.width / 2, size.height / 2),
                    )
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        if (voiceState != VoiceInputState.Idle) return@detectTapGestures

                        val micStatus = micPermissionState.permissionStates[Manifest.permission.RECORD_AUDIO]
                        if (micStatus != PermissionStatus.Granted) {
                            // First tap: request permission. User presses again after granting.
                            micPermissionState.requestPermission(Manifest.permission.RECORD_AUDIO)
                            tryAwaitRelease()
                            return@detectTapGestures
                        }

                        // Permission granted — start recording
                        scope.launch {
                            val result = audioRecorder.startRecording()
                            if (result.isSuccess) {
                                voiceState = VoiceInputState.Recording
                            }
                        }

                        tryAwaitRelease()

                        // On release — transcribe
                        if (voiceState == VoiceInputState.Recording) {
                            voiceState = VoiceInputState.Processing
                            scope.launch {
                                val recordResult = audioRecorder.stopRecording()
                                if (recordResult.isSuccess) {
                                    val wavData = recordResult.getOrThrow()
                                    val settings = settingsStore.settingsFlow.first()
                                    val provider = settings.getSelectedSTTProvider()
                                    if (provider != null) {
                                        try {
                                            val sttResult = withContext(Dispatchers.IO) {
                                                sttManager.transcribe(
                                                    audioData = wavData,
                                                    format = SttAudioFormat.WAV,
                                                    sampleRate = 16000,
                                                    providerSetting = provider,
                                                )
                                            }
                                            if (sttResult.text.isNotBlank()) {
                                                onVoiceResult(sttResult.text)
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "STT failed", e)
                                        }
                                    }
                                }
                                voiceState = VoiceInputState.Idle
                            }
                        }
                    }
                )
            }
    ) {
        Icon(
            imageVector = HugeIcons.AiMagic,
            contentDescription = "Voice input",
            tint = if (voiceState == VoiceInputState.Recording)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
    }
}
