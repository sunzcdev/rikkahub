package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class VibrationResult(
    val success: Boolean,
    val mode: String? = null,
    val actualDurationMs: Long? = null,
    val amplitudeSupported: Boolean? = null,
    val amplitudeUsed: Boolean? = null,
    val segments: Int? = null,
    val error: String? = null,
) {
    fun toJson(): String = buildJsonObject {
        put("success", success)
        if (mode != null) put("mode", mode)
        if (actualDurationMs != null) put("actual_duration_ms", actualDurationMs)
        if (amplitudeSupported != null) put("amplitude_supported", amplitudeSupported)
        if (amplitudeUsed != null) put("amplitude_used", amplitudeUsed)
        if (segments != null) put("segments", segments)
        if (error != null) put("error", error)
    }.toString()
}

class VibrationManager(context: Context) {
    companion object {
        const val MAX_TOTAL_MS = 10_000L
    }

    val hasVibrator: Boolean by lazy { vibrator.hasVibrator() }
    val hasAmplitudeControl: Boolean by lazy { Build.VERSION.SDK_INT >= Build.VERSION_CODES.O }
    val vibrationCapability: String by lazy {
        "hasVibrator=$hasVibrator, hasAmplitudeControl=$hasAmplitudeControl, apiLevel=${Build.VERSION.SDK_INT}"
    }

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    init {
        vibrator // eager init: trigger lazy to create vibrator immediately
    }

    fun oneShot(durationMs: Long, amplitude: Int? = null): VibrationResult {
        if (durationMs <= 0L) {
            return VibrationResult(
                success = false,
                error = "duration_ms must be > 0, got $durationMs"
            )
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
            return VibrationResult(
                success = true,
                mode = "one_shot",
                actualDurationMs = durationMs,
                amplitudeSupported = false,
                amplitudeUsed = false,
            )
        }

        val effect = if (amplitude != null && amplitude in 0..255) {
            VibrationEffect.createOneShot(durationMs, amplitude)
        } else {
            VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(effect)
        return VibrationResult(
            success = true,
            mode = "one_shot",
            actualDurationMs = durationMs,
            amplitudeSupported = true,
            amplitudeUsed = amplitude != null,
        )
    }

    fun waveform(timings: LongArray, amplitudes: IntArray? = null): VibrationResult {
        if (timings.isEmpty()) {
            return VibrationResult(
                success = false,
                error = "timings array must not be empty"
            )
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val totalOn = timings.withIndex()
                .filter { it.index % 2 == 1 }
                .sumOf { it.value }
            @Suppress("DEPRECATION")
            vibrator.vibrate(totalOn)
            return VibrationResult(
                success = true,
                mode = "waveform",
                actualDurationMs = totalOn,
                amplitudeSupported = false,
                amplitudeUsed = false,
                segments = timings.size / 2,
            )
        }

        val effect = if (amplitudes != null && amplitudes.size == timings.size) {
            // timings follow [off_ms, on_ms, off_ms, on_ms, ...] format
            // Amplitude controls ON segments; OFF segments (even indices) forced to 0
            val safeAmplitudes = IntArray(timings.size) { i ->
                if (i % 2 == 0) 0 else amplitudes[i].coerceIn(1, 255)
            }
            VibrationEffect.createWaveform(timings, safeAmplitudes, -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
        vibrator.vibrate(effect)
        return VibrationResult(
            success = true,
            mode = "waveform",
            amplitudeSupported = true,
            amplitudeUsed = amplitudes != null,
            segments = timings.size / 2,
        )
    }

    fun cancel(): VibrationResult {
        vibrator.cancel()
        return VibrationResult(
            success = true,
            mode = "cancel",
        )
    }
}
