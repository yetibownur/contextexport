package com.garrettmcbride.contextexport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SectionStatsTest {

    enum class Section { ALPHA, BETA, GAMMA }

    data class Data(val items: List<String> = listOf("item1", "item2", "item3"))

    private val builder = object : AppPromptBuilder<Data, Section>() {
        override fun appendHeader(sb: StringBuilder, data: Data) {
            sb.appendLine("# Header")
            sb.appendLine()
        }

        override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
            Section.ALPHA to { sb: StringBuilder, _: Data ->
                sb.appendLine("## Alpha")
                sb.appendLine("Short content.")
                sb.appendLine()
            },
            Section.BETA to { sb: StringBuilder, data: Data ->
                sb.appendLine("## Beta")
                for (item in data.items) sb.appendLine("- $item")
                sb.appendLine()
            },
            Section.GAMMA to { sb: StringBuilder, _: Data ->
                sb.appendLine("## Gamma")
                sb.appendLine("A".repeat(500))
                sb.appendLine()
            }
        )
    }

    private val data = Data()
    private val allSections = Section.entries.toSet()

    @Test
    fun `sectionBreakdown has entries for each enabled section`() {
        val result = builder.buildPromptResult(data, allSections)
        assertEquals(3, result.sectionBreakdown.size)
        assertTrue("ALPHA" in result.sectionBreakdown)
        assertTrue("BETA" in result.sectionBreakdown)
        assertTrue("GAMMA" in result.sectionBreakdown)
    }

    @Test
    fun `sectionBreakdown is empty when no sections enabled`() {
        val result = builder.buildPromptResult(data, emptySet())
        assertTrue(result.sectionBreakdown.isEmpty())
    }

    @Test
    fun `sectionBreakdown only includes enabled sections`() {
        val result = builder.buildPromptResult(data, setOf(Section.ALPHA))
        assertEquals(1, result.sectionBreakdown.size)
        assertTrue("ALPHA" in result.sectionBreakdown)
        assertTrue("BETA" !in result.sectionBreakdown)
    }

    @Test
    fun `chars and tokens are positive for non-empty sections`() {
        val result = builder.buildPromptResult(data, allSections)
        for ((_, stats) in result.sectionBreakdown) {
            assertTrue(stats.chars > 0, "Chars should be positive")
            assertTrue(stats.tokens > 0, "Tokens should be positive")
        }
    }

    @Test
    fun `larger section has higher chars and tokens`() {
        val result = builder.buildPromptResult(data, allSections)
        val gamma = result.sectionBreakdown["GAMMA"]!!
        val alpha = result.sectionBreakdown["ALPHA"]!!
        assertTrue(gamma.chars > alpha.chars, "Gamma (500+ chars) should be larger than Alpha")
        assertTrue(gamma.tokens > alpha.tokens)
    }

    @Test
    fun `percentage is between 0 and 100`() {
        val result = builder.buildPromptResult(data, allSections)
        for ((_, stats) in result.sectionBreakdown) {
            assertTrue(stats.percentage > 0.0)
            assertTrue(stats.percentage <= 100.0)
        }
    }

    @Test
    fun `default sectionBreakdown is emptyMap for backwards compatibility`() {
        // Construct PromptResult with old-style constructor (no sectionBreakdown)
        val result = PromptResult(
            fullText = "test",
            chunks = listOf("test"),
            sizeBytes = 4,
            estimatedTokens = 1,
            enabledSectionCount = 0,
            chunkSize = 15000
        )
        assertTrue(result.sectionBreakdown.isEmpty())
    }
}
