package me.rerere.rikkahub.data.ai.transformers

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlin.time.Clock

private val THINKING_REGEX = Regex("<think>([\\s\\S]*?)(?:</think>|$)", RegexOption.DOT_MATCHES_ALL)
private val CLOSING_TAG_REGEX = Regex("</think>")

// 部分供应商不会返回reasoning parts, 所以需要这个transformer
object ThinkTagTransformer : OutputMessageTransformer {
    private fun processThinkTags(
        text: String,
        message: UIMessage,
        reasoningEnabled: Boolean,
        finishedAt: kotlin.time.Instant?,
    ): List<UIMessagePart> {
        if (!THINKING_REGEX.containsMatchIn(text)) return listOf(UIMessagePart.Text(text = text))
        val stripped = text.replace(THINKING_REGEX, "")
        if (!reasoningEnabled) {
            return listOf(UIMessagePart.Text(text = stripped))
        }
        val reasoning = THINKING_REGEX.find(text)?.groupValues?.getOrNull(1)?.trim() ?: ""
        return listOf(
            UIMessagePart.Reasoning(
                reasoning = reasoning,
                createdAt = message.createdAt.toInstant(timeZone = TimeZone.currentSystemDefault()),
                finishedAt = finishedAt,
            ),
            UIMessagePart.Text(text = stripped),
        )
    }

    override suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val reasoningEnabled = ctx.assistant.reasoningLevel.isEnabled
        return messages.map { message ->
            if (message.role == MessageRole.ASSISTANT && message.hasPart<UIMessagePart.Text>()) {
                message.copy(
                    parts = message.parts.flatMap { part ->
                        if (part is UIMessagePart.Text) {
                            val hasClosingTag = CLOSING_TAG_REGEX.containsMatchIn(part.text)
                            processThinkTags(
                                text = part.text,
                                message = message,
                                reasoningEnabled = reasoningEnabled,
                                finishedAt = if (hasClosingTag) Clock.System.now() else null,
                            )
                        } else {
                            listOf(part)
                        }
                    }
                )
            } else {
                message
            }
        }
    }

    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val reasoningEnabled = ctx.assistant.reasoningLevel.isEnabled
        val now = Clock.System.now()
        return messages.map { message ->
            if (message.role == MessageRole.ASSISTANT && message.hasPart<UIMessagePart.Text>()) {
                message.copy(
                    parts = message.parts.flatMap { part ->
                        if (part is UIMessagePart.Text) {
                            processThinkTags(
                                text = part.text,
                                message = message,
                                reasoningEnabled = reasoningEnabled,
                                finishedAt = now,
                            )
                        } else {
                            listOf(part)
                        }
                    }
                )
            } else {
                message
            }
        }
    }
}
