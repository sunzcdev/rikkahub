package me.rerere.rikkahub.data.ai.groupchat

import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.GroupChatConfig
import me.rerere.rikkahub.data.model.GroupChatParticipant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.uuid.Uuid

class MentionDetectionTest {

    private lateinit var assistant1: Assistant
    private lateinit var assistant2: Assistant
    private lateinit var assistant3: Assistant

    @Before
    fun setup() {
        assistant1 = Assistant(
            id = Uuid.random(),
            name = "Socrates",
            systemPrompt = ""
        )
        assistant2 = Assistant(
            id = Uuid.random(),
            name = "Claude",
            systemPrompt = ""
        )
        assistant3 = Assistant(
            id = Uuid.random(),
            name = "Gemini",
            systemPrompt = ""
        )
    }

    @Test
    fun `should detect single mention`() {
        val p1 = GroupChatParticipant(id = Uuid.random(), assistantId = assistant1.id)
        val p2 = GroupChatParticipant(id = Uuid.random(), assistantId = assistant2.id)
        val config = GroupChatConfig(isGroupChat = true, participants = listOf(p1, p2))
        val conversation = Conversation(
            id = Uuid.random(),
            assistantId = assistant1.id,
            messageNodes = emptyList(),
            groupChatConfig = config
        )

        val mentioned = extractMentionedParticipants(
            content = "Hello @Socrates, how are you?",
            conversation = conversation,
            selfParticipantId = Uuid.random(),
            getAssistant = { id -> if (id == assistant1.id) assistant1 else assistant2 },
            getModelDisplayName = { "Model" }
        )

        assertEquals(1, mentioned.size)
        assertEquals(p1.id, mentioned[0])
    }

    @Test
    fun `should detect multiple mentions`() {
        val p1 = GroupChatParticipant(id = Uuid.random(), assistantId = assistant1.id)
        val p2 = GroupChatParticipant(id = Uuid.random(), assistantId = assistant2.id)
        val p3 = GroupChatParticipant(id = Uuid.random(), assistantId = assistant3.id)
        val config = GroupChatConfig(isGroupChat = true, participants = listOf(p1, p2, p3))
        val conversation = Conversation(
            id = Uuid.random(),
            assistantId = assistant1.id,
            messageNodes = emptyList(),
            groupChatConfig = config
        )

        val mentioned = extractMentionedParticipants(
            content = "Hey @Claude and @Gemini!",
            conversation = conversation,
            selfParticipantId = p1.id,
            getAssistant = { id ->
                when(id) {
                    assistant1.id -> assistant1
                    assistant2.id -> assistant2
                    assistant3.id -> assistant3
                    else -> null
                }
            },
            getModelDisplayName = { "Model" }
        )

        assertEquals(2, mentioned.size)
        assertTrue(mentioned.contains(p2.id))
        assertTrue(mentioned.contains(p3.id))
    }

    @Test
    fun `should not detect partial matches`() {
        val p1 = GroupChatParticipant(id = Uuid.random(), assistantId = assistant1.id)
        val config = GroupChatConfig(isGroupChat = true, participants = listOf(p1))
        val conversation = Conversation(
            id = Uuid.random(),
            assistantId = assistant1.id,
            messageNodes = emptyList(),
            groupChatConfig = config
        )

        val mentioned = extractMentionedParticipants(
            content = "Hello @SocratesExtra",
            conversation = conversation,
            selfParticipantId = Uuid.random(),
            getAssistant = { assistant1 },
            getModelDisplayName = { "Model" }
        )

        assertTrue(mentioned.isEmpty())
    }

    @Test
    fun `should detect mention at end of sentence`() {
        val p1 = GroupChatParticipant(id = Uuid.random(), assistantId = assistant1.id)
        val config = GroupChatConfig(isGroupChat = true, participants = listOf(p1))
        val conversation = Conversation(
            id = Uuid.random(),
            assistantId = assistant1.id,
            messageNodes = emptyList(),
            groupChatConfig = config
        )

        val mentioned = extractMentionedParticipants(
            content = "What do you think, @Socrates?",
            conversation = conversation,
            selfParticipantId = Uuid.random(),
            getAssistant = { assistant1 },
            getModelDisplayName = { "Model" }
        )

        assertEquals(1, mentioned.size)
        assertEquals(p1.id, mentioned[0])
    }

    @Test
    fun `should detect mention with full-width at`() {
        val p1 = GroupChatParticipant(id = Uuid.random(), assistantId = assistant1.id)
        val config = GroupChatConfig(isGroupChat = true, participants = listOf(p1))
        val conversation = Conversation(
            id = Uuid.random(),
            assistantId = assistant1.id,
            messageNodes = emptyList(),
            groupChatConfig = config
        )

        val mentioned = extractMentionedParticipants(
            content = "你好 ＠Socrates",
            conversation = conversation,
            selfParticipantId = Uuid.random(),
            getAssistant = { assistant1 },
            getModelDisplayName = { "Model" }
        )

        assertEquals(1, mentioned.size)
        assertEquals(p1.id, mentioned[0])
    }
}
