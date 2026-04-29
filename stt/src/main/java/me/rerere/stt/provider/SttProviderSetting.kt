package me.rerere.stt.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
sealed class SttProviderSetting {
    abstract val id: Uuid
    abstract val name: String

    abstract fun copyProvider(
        id: Uuid = this.id,
        name: String = this.name,
    ): SttProviderSetting

    @Serializable
    @SerialName("openai_whisper")
    data class OpenAIWhisper(
        override var id: Uuid = Uuid.random(),
        override var name: String = "OpenAI Whisper",
        val apiKey: String = "",
        val baseUrl: String = "https://api.openai.com/v1",
        val model: String = "whisper-1",
    ) : SttProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): SttProviderSetting {
            return this.copy(id = id, name = name)
        }
    }

    @Serializable
    @SerialName("gemini")
    data class Gemini(
        override var id: Uuid = Uuid.random(),
        override var name: String = "Gemini STT",
        val apiKey: String = "",
        val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
        val model: String = "gemini-2.5-flash",
    ) : SttProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): SttProviderSetting {
            return this.copy(id = id, name = name)
        }
    }

    companion object {
        val Types by lazy {
            listOf(
                OpenAIWhisper::class,
                Gemini::class,
            )
        }
    }
}
