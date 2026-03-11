package com.garrettmcbride.contextexport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenizerTest {

    enum class Section { ALPHA }

    data class Data(val text: String = "test")

    @Test
    fun `default tokenizer uses TokenEstimator`() {
        val tokens = Tokenizer.DEFAULT.countTokens("Hello, world!")
        assertEquals(TokenEstimator.estimate("Hello, world!"), tokens)
    }

    @Test
    fun `custom tokenizer is used for buildPromptResult`() {
        // Custom tokenizer that counts words instead of chars/4
        val builder = object : AppPromptBuilder<Data, Section>() {
            override val tokenizer = Tokenizer { text ->
                text.split(Regex("\\s+")).count { it.isNotBlank() }
            }

            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                Section.ALPHA to { sb: StringBuilder, _: Data ->
                    sb.appendLine("one two three four five")
                }
            )
        }

        val result = builder.buildPromptResult(Data(), setOf(Section.ALPHA))
        // "one two three four five" = 5 words
        assertEquals(5, result.estimatedTokens)
    }

    @Test
    fun `custom tokenizer affects section breakdown`() {
        val builder = object : AppPromptBuilder<Data, Section>() {
            override val tokenizer = Tokenizer { text -> text.length } // 1 char = 1 token

            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                Section.ALPHA to { sb: StringBuilder, _: Data ->
                    sb.append("abcdef") // 6 chars = 6 tokens
                }
            )
        }

        val result = builder.buildPromptResult(Data(), setOf(Section.ALPHA))
        val stats = result.sectionBreakdown["ALPHA"]
        assertTrue(stats != null)
        assertEquals(6, stats.tokens)
    }

    @Test
    fun `custom tokenizer affects budget calculations`() {
        // Tokenizer that returns huge numbers so budget always triggers
        val builder = object : AppPromptBuilder<Data, Section>() {
            override val tokenizer = Tokenizer { 999_999 }

            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                Section.ALPHA to { sb: StringBuilder, _: Data ->
                    sb.appendLine("content")
                }
            )
        }

        val result = builder.buildPromptWithBudget(Data(), setOf(Section.ALPHA), maxTokens = 100)
        // Should drop everything because tokenizer says each section is 999K tokens
        assertTrue(result.droppedSections.isNotEmpty())
    }

    @Test
    fun `custom tokenizer affects JSON metadata`() {
        val builder = object : AppPromptBuilder<Data, Section>() {
            override val tokenizer = Tokenizer { 42 } // always returns 42

            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                Section.ALPHA to { sb: StringBuilder, _: Data ->
                    sb.appendLine("content")
                }
            )
        }

        val json = builder.buildJsonPrompt(Data(), setOf(Section.ALPHA))
        assertTrue("\"estimatedTokens\": 42" in json)
    }

    @Test
    fun `Tokenizer DEFAULT is not null`() {
        assertTrue(Tokenizer.DEFAULT.countTokens("test") > 0)
    }
}
