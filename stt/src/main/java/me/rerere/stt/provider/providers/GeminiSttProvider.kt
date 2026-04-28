package me.rerere.stt.provider.providers

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.stt.model.SttAudioFormat
import me.rerere.stt.model.SttResult
import me.rerere.stt.provider.SttProvider
import me.rerere.stt.provider.SttProviderSetting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class GeminiSttProvider : SttProvider<SttProviderSetting.Gemini> {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class GeminiResponse(
        val candidates: List<Candidate> = emptyList(),
    )

    @Serializable
    data class Candidate(
        val content: Content? = null,
    )

    @Serializable
    data class Content(
        val parts: List<Part> = emptyList(),
    )

    @Serializable
    data class Part(
        val text: String? = null,
    )

    override suspend fun transcribe(
        audioData: ByteArray,
        format: SttAudioFormat,
        sampleRate: Int,
        providerSetting: SttProviderSetting.Gemini,
    ): SttResult {
        val mimeType = when (format) {
            SttAudioFormat.WAV -> "audio/wav"
            SttAudioFormat.MP3 -> "audio/mpeg"
            SttAudioFormat.M4A -> "audio/mp4"
            SttAudioFormat.OGG_OPUS -> "audio/ogg"
        }

        val base64Audio = Base64.getEncoder().encodeToString(audioData)

        val requestBody = buildGeminiRequestBody(base64Audio, mimeType)
        val mediaType = "application/json".toMediaType()

        val request = Request.Builder()
            .url("${providerSetting.baseUrl}/models/${providerSetting.model}:generateContent")
            .header("X-Goog-Api-Key", providerSetting.apiKey)
            .post(requestBody.toRequestBody(mediaType))
            .build()

        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }
        val body = response.body?.string()
            ?: throw RuntimeException("Empty response from Gemini API")

        val geminiResponse = json.decodeFromString<GeminiResponse>(body)

        val text = geminiResponse.candidates
            ?.firstOrNull()?.content?.parts
            ?.firstOrNull()?.text
            ?: throw RuntimeException("No transcription in Gemini response")

        return SttResult(
            text = text.trim(),
            durationMs = 0L,
        )
    }

    private fun buildGeminiRequestBody(base64Audio: String, mimeType: String): String {
        return json.encodeToString(
            GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(inlineData = GeminiInlineData(mimeType = mimeType, data = base64Audio)),
                            GeminiPart(text = "Transcribe the audio, including the language detection."),
                        )
                    )
                )
            )
        )
    }

    @Serializable
    data class GeminiRequest(
        val contents: List<GeminiContent>,
    )

    @Serializable
    data class GeminiContent(
        val parts: List<GeminiPart>,
    )

    @Serializable
    data class GeminiPart(
        val text: String? = null,
        val inlineData: GeminiInlineData? = null,
    )

    @Serializable
    data class GeminiInlineData(
        val mimeType: String,
        val data: String,
    )
}
