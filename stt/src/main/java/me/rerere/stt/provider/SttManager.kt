package me.rerere.stt.provider

import me.rerere.stt.model.SttAudioFormat
import me.rerere.stt.model.SttResult
import me.rerere.stt.provider.providers.GeminiSttProvider
import me.rerere.stt.provider.providers.OpenAIWhisperProvider

class SttManager {
    private val openAIWhisperProvider = OpenAIWhisperProvider()
    private val geminiProvider = GeminiSttProvider()

    suspend fun transcribe(
        audioData: ByteArray,
        format: SttAudioFormat,
        sampleRate: Int,
        providerSetting: SttProviderSetting,
    ): SttResult {
        return when (providerSetting) {
            is SttProviderSetting.OpenAIWhisper ->
                openAIWhisperProvider.transcribe(audioData, format, sampleRate, providerSetting)
            is SttProviderSetting.Gemini ->
                geminiProvider.transcribe(audioData, format, sampleRate, providerSetting)
        }
    }
}
