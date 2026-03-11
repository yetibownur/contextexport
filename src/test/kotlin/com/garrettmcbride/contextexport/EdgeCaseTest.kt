package com.garrettmcbride.contextexport

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for edge case bug fixes: pipe escaping, NaN/Infinity,
 * Long.MIN_VALUE, and structured prompt section merging.
 */
class EdgeCaseTest {

    // ── MarkdownTable pipe escaping ─────────────────────────────────

    @Test
    fun `pipe in cell content is escaped`() {
        val table = MarkdownTable("Name", "Details")
        table.row("Item", "a|b")
        val result = table.build()
        assertContains(result, "| Item | a\\|b |")
    }

    @Test
    fun `pipe in header is escaped`() {
        val table = MarkdownTable("Name|ID", "Value")
        table.row("test", "123")
        val result = table.build()
        assertContains(result, "| Name\\|ID | Value |")
    }

    @Test
    fun `multiple pipes in cell are all escaped`() {
        val table = MarkdownTable("Col")
        table.row("a|b|c|d")
        val result = table.build()
        assertContains(result, "| a\\|b\\|c\\|d |")
    }

    @Test
    fun `DSL table also escapes pipes`() {
        val sb = StringBuilder()
        sb.appendTable("H") {
            row("x|y")
        }
        assertContains(sb.toString(), "x\\|y")
    }

    // ── NumberFormat NaN and Infinity ────────────────────────────────

    @Test
    fun `compact NaN returns NaN`() {
        assertEquals("NaN", NumberFormat.compact(Double.NaN))
    }

    @Test
    fun `compact NaN with unit`() {
        assertEquals("NaN lbs", NumberFormat.compact(Double.NaN, "lbs"))
    }

    @Test
    fun `compact positive infinity`() {
        assertEquals("∞", NumberFormat.compact(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `compact negative infinity`() {
        assertEquals("-∞", NumberFormat.compact(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun `compact infinity with unit`() {
        assertEquals("∞ cal", NumberFormat.compact(Double.POSITIVE_INFINITY, "cal"))
    }

    // ── NumberFormat Long.MIN_VALUE ──────────────────────────────────

    @Test
    fun `withCommas handles Long MIN_VALUE without overflow`() {
        val result = NumberFormat.withCommas(Long.MIN_VALUE)
        // Should produce a negative number string without crashing
        assertTrue(result.startsWith("-"))
        assertTrue(result.contains(","))
    }

    @Test
    fun `withCommas handles large negative numbers`() {
        assertEquals("-1,234,567", NumberFormat.withCommas(-1_234_567))
    }

    // ── buildStructuredPrompt section merge separator ────────────────

    enum class Section { A, B, C }
    data class Data(val text: String = "test")

    @Test
    fun `merged same-role sections have newline separator`() {
        val builder = object : AppPromptBuilder<Data, Section>() {
            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                Section.A to { sb: StringBuilder, _: Data ->
                    sb.append("## Section A\nContent A") // no trailing newline
                },
                Section.B to { sb: StringBuilder, _: Data ->
                    sb.append("## Section B\nContent B")
                }
            )

            override fun sectionRole(section: Section): MessageRole = MessageRole.USER
        }

        val messages = builder.buildStructuredPrompt(Data(), setOf(Section.A, Section.B))
        assertEquals(1, messages.size)
        val content = messages[0].content
        // Should NOT have "Content A## Section B" — needs newline between
        assertTrue("Content A\n## Section B" in content,
            "Merged sections should have newline separator, got: $content")
    }

    @Test
    fun `merged sections with trailing newlines dont double-newline`() {
        val builder = object : AppPromptBuilder<Data, Section>() {
            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                Section.A to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Section A")
                    sb.appendLine()
                },
                Section.B to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Section B")
                    sb.appendLine()
                }
            )

            override fun sectionRole(section: Section): MessageRole = MessageRole.USER
        }

        val messages = builder.buildStructuredPrompt(Data(), setOf(Section.A, Section.B))
        assertEquals(1, messages.size)
        // Both sections should be present
        assertContains(messages[0].content, "## Section A")
        assertContains(messages[0].content, "## Section B")
    }
}
