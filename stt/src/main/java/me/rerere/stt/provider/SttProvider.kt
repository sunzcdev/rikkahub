package me.rerere.stt.provider

import me.rerere.stt.model.SttAudioFormat
import me.rerere.stt.model.SttResult

interface SttProvider<T : SttProviderSetting> {
    suspend fun transcribe(
        audioData: ByteArray,
        format: SttAudioFormat,
        sampleRate: Int,
        providerSetting: T,
    ): SttResult
}
