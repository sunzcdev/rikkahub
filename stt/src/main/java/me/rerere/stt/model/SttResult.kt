package me.rerere.stt.model

data class SttResult(
    val text: String,
    val language: String? = null,
    val durationMs: Long = 0L,
)

enum class SttAudioFormat {
    WAV,
    MP3,
    M4A,
    OGG_OPUS,
}
