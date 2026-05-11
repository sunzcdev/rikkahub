package me.rerere.rikkahub.utils

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import me.rerere.common.android.Logging
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

private const val TAG = "AudioRecorder"

enum class RecorderState {
    Idle,
    Recording,
    Completed,
    Error,
}

class AudioRecorder(
    private val sampleRate: Int = 16000,
) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    private val _state = MutableStateFlow(RecorderState.Idle)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val _amplitude = MutableStateFlow(0.0)
    val amplitude: StateFlow<Double> = _amplitude.asStateFlow()

    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
    ).coerceAtLeast(4096)

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun startRecording(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            audioRecord?.release()
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2,
            )

            val record = audioRecord ?: return@withContext Result.failure(Exception("Failed to create AudioRecord"))

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                return@withContext Result.failure(Exception("AudioRecord not initialized"))
            }

            record.startRecording()
            isRecording = true
            _state.value = RecorderState.Recording
            Logging.d(TAG, "Recording started: ${sampleRate}Hz, 16-bit mono")

            Result.success(Unit)
        } catch (e: Exception) {
            Logging.e(TAG, "Failed to start recording", e)
            _state.value = RecorderState.Error
            Result.failure(e)
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun stopRecording(): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            isRecording = false

            // Take ownership of the AudioRecord instance, preventing concurrent cancel()
            val record = audioRecord
            audioRecord = null
            if (record == null) return@withContext Result.failure(Exception("AudioRecord not initialized"))

            // Stop hardware recording first, then drain remaining buffered data
            record.stop()

            val baos = ByteArrayOutputStream()
            val buffer = ByteArray(bufferSize)

            // Single drain read — after stop(), read() returns remaining buffered data or -1
            val bytesRead = record.read(buffer, 0, buffer.size)
            if (bytesRead > 0) {
                baos.write(buffer, 0, bytesRead)
            }
            baos.flush()

            val pcmData = baos.toByteArray()
            record.release()

            val wavData = pcmToWav(pcmData, sampleRate)
            _state.value = RecorderState.Completed
            Logging.d(TAG, "Recording stopped: ${pcmData.size} bytes PCM → ${wavData.size} bytes WAV")

            Result.success(wavData)
        } catch (e: Exception) {
            Logging.e(TAG, "Failed to stop recording", e)
            _state.value = RecorderState.Error
            Result.failure(e)
        }
    }

    fun cancel() {
        isRecording = false
        val record = audioRecord
        audioRecord = null
        try {
            record?.stop()
        } catch (_: Exception) { }
        try {
            record?.release()
        } catch (_: Exception) { }
        _state.value = RecorderState.Idle
    }

    private fun pcmToWav(pcmData: ByteArray, sampleRate: Int): ByteArray {
        val channels = 1 // mono
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val headerSize = 44
        val totalSize = headerSize + dataSize

        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalSize - 8)
        buffer.put("WAVE".toByteArray())

        // fmt chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // chunk size
        buffer.putShort(1) // PCM format
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())

        // data chunk
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)
        buffer.put(pcmData)

        return buffer.array()
    }

    companion object {
        const val MAX_RECORDING_DURATION_MS = 60_000L
    }
}
