package com.garrettmcbride.contextexport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertTrue

class StructuredMessageTest {

    enum class Section { LORE, QUERY, DATA }

    data class Data(val text: String = "test")

    private val builder = object : AppPromptBuilder<Data, Section>() {
        override fun appendHeader(sb: StringBuilder, data: Data) {
            sb.appendLine("# System Instructions")
            sb.appendLine("You are a helpful assistant.")
            sb.appendLine()
        }

        override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
            Section.LORE to { sb: StringBuilder, _: Data ->
                sb.appendLine("## World Lore")
                sb.appendLine("Ancient history.")
                sb.appendLine()
            },
            Section.QUERY to { sb: StringBuilder, _: Data ->
                sb.appendLine("## Query")
                sb.appendLine("Tell me about dragons.")
                sb.appendLine()
            },
            Section.DATA to { sb: StringBuilder, _: Data ->
                sb.appendLine("## Data")
                sb.appendLine("Dragon stats here.")
                sb.appendLine()
            }
        )

        override fun appendFooter(sb: StringBuilder, data: Data) {
            sb.appendLine()
            sb.appendLine("Please respond in markdown.")
        }

        override fun sectionRole(section: Section): MessageRole = when (section) {
            Section.LORE -> MessageRole.SYSTEM
            Section.QUERY -> MessageRole.USER
            Section.DATA -> MessageRole.USER
        }
    }

    private val data = Data()
    private val allSections = Section.entries.toSet()

    @Test
    fun `buildStructuredPrompt returns messages with correct roles`() {
        val messages = builder.buildStructuredPrompt(data, allSections)
        assertTrue(messages.isNotEmpty())
        // First message should be SYSTEM (header + LORE)
        assertEquals(MessageRole.SYSTEM, messages[0].role)
    }

    @Test
    fun `header is always SYSTEM role`() {
        val messages = builder.buildStructuredPrompt(data, allSections)
        assertContains(messages[0].content, "System Instructions")
        assertEquals(MessageRole.SYSTEM, messages[0].role)
    }

    @Test
    fun `footer is always USER role`() {
        val messages = builder.buildStructuredPrompt(data, allSections)
        val lastMessage = messages.last()
        assertEquals(MessageRole.USER, lastMessage.role)
        assertContains(lastMessage.content, "respond in markdown")
    }

    @Test
    fun `adjacent same-role sections are merged`() {
        val messages = builder.buildStructuredPrompt(data, allSections)
        // SYSTEM: header + LORE (merged)
        // USER: QUERY + DATA + footer (merged)
        assertEquals(2, messages.size)
    }

    @Test
    fun `SYSTEM message contains header and lore`() {
        val messages = builder.buildStructuredPrompt(data, allSections)
        val systemMsg = messages.first { it.role == MessageRole.SYSTEM }
        assertContains(systemMsg.content, "System Instructions")
        assertContains(systemMsg.content, "World Lore")
    }

    @Test
    fun `USER message contains query and data`() {
        val messages = builder.buildStructuredPrompt(data, allSections)
        val userMsg = messages.first { it.role == MessageRole.USER }
        assertContains(userMsg.content, "Query")
        assertContains(userMsg.content, "Data")
    }

    @Test
    fun `all sections default to USER role`() {
        val defaultBuilder = object : AppPromptBuilder<Data, Section>() {
            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                Section.LORE to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Lore")
                    sb.appendLine()
                },
                Section.QUERY to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Query")
                    sb.appendLine()
                }
            )
        }

        val messages = defaultBuilder.buildStructuredPrompt(data, setOf(Section.LORE, Section.QUERY))
        // No header, all USER → single USER message
        assertEquals(1, messages.size)
        assertEquals(MessageRole.USER, messages[0].role)
    }

    @Test
    fun `role transitions create separate messages`() {
        val alternatingBuilder = object : AppPromptBuilder<Data, Section>() {
            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                Section.LORE to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Lore")
                    sb.appendLine()
                },
                Section.QUERY to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Query")
                    sb.appendLine()
                },
                Section.DATA to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Data")
                    sb.appendLine()
                }
            )

            override fun sectionRole(section: Section): MessageRole = when (section) {
                Section.LORE -> MessageRole.SYSTEM
                Section.QUERY -> MessageRole.USER
                Section.DATA -> MessageRole.SYSTEM  // back to system
            }
        }

        val messages = alternatingBuilder.buildStructuredPrompt(data, allSections)
        // SYSTEM (Lore) → USER (Query) → SYSTEM (Data) = 3 messages
        assertEquals(3, messages.size)
        assertEquals(MessageRole.SYSTEM, messages[0].role)
        assertEquals(MessageRole.USER, messages[1].role)
        assertEquals(MessageRole.SYSTEM, messages[2].role)
    }
}
