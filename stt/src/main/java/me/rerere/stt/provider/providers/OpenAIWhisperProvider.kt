package me.rerere.stt.provider.providers

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.stt.model.SttAudioFormat
import me.rerere.stt.model.SttResult
import me.rerere.stt.provider.SttProvider
import me.rerere.stt.provider.SttProviderSetting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class OpenAIWhisperProvider : SttProvider<SttProviderSetting.OpenAIWhisper> {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class WhisperResponse(
        val text: String,
    )

    override suspend fun transcribe(
        audioData: ByteArray,
        format: SttAudioFormat,
        sampleRate: Int,
        providerSetting: SttProviderSetting.OpenAIWhisper,
    ): SttResult {
        val mimeType = when (format) {
            SttAudioFormat.WAV -> "audio/wav"
            SttAudioFormat.MP3 -> "audio/mpeg"
            SttAudioFormat.M4A -> "audio/mp4"
            SttAudioFormat.OGG_OPUS -> "audio/ogg"
        }
        val extension = when (format) {
            SttAudioFormat.WAV -> "wav"
            SttAudioFormat.MP3 -> "mp3"
            SttAudioFormat.M4A -> "m4a"
            SttAudioFormat.OGG_OPUS -> "ogg"
        }

        val audioBody = audioData.toRequestBody(mimeType.toMediaType())
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "audio.$extension", audioBody)
            .addFormDataPart("model", providerSetting.model)
            .build()

        val request = Request.Builder()
            .url("${providerSetting.baseUrl}/audio/transcriptions")
            .header("Authorization", "Bearer ${providerSetting.apiKey}")
            .post(requestBody)
            .build()

        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }
        val body = response.body?.string()
            ?: throw RuntimeException("Empty response from Whisper API")

        val whisperResponse = json.decodeFromString<WhisperResponse>(body)

        return SttResult(
            text = whisperResponse.text.trim(),
            durationMs = 0L,
        )
    }
}
