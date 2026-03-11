package com.garrettmcbride.contextexport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExportPresetTest {

    enum class Section { PROFILE, HISTORY, STATS }

    data class Data(val name: String = "Alice")

    private val builder = object : AppPromptBuilder<Data, Section>() {
        override fun appendHeader(sb: StringBuilder, data: Data) {
            sb.appendLine("# ${data.name}")
            sb.appendLine()
        }

        override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
            Section.PROFILE to { sb: StringBuilder, data: Data ->
                sb.appendLine("## Profile")
                sb.appendLine("Name: ${data.name}")
                sb.appendLine()
            },
            Section.HISTORY to { sb: StringBuilder, _: Data ->
                sb.appendLine("## History")
                sb.appendLine("Entry 1")
                sb.appendLine()
            },
            Section.STATS to { sb: StringBuilder, _: Data ->
                sb.appendLine("## Stats")
                sb.appendLine("Score: 42")
                sb.appendLine()
            }
        )

        override fun exportPresets() = listOf(
            ExportPreset("Full Export", Section.entries.toSet()),
            ExportPreset("Quick Summary", setOf(Section.PROFILE, Section.STATS)),
            ExportPreset("For Claude", Section.entries.toSet(), targetModel = ContextWindow.CLAUDE_3_5)
        )
    }

    private val data = Data()

    @Test
    fun `getPreset returns correct preset by name`() {
        val preset = builder.getPreset("Full Export")
        assertEquals("Full Export", preset?.name)
        assertEquals(Section.entries.toSet(), preset?.sections)
    }

    @Test
    fun `getPreset returns null for unknown name`() {
        assertNull(builder.getPreset("Nonexistent"))
    }

    @Test
    fun `buildFromPreset with no target model uses all sections`() {
        val result = builder.buildFromPreset(data, "Full Export")
        assertTrue(result != null)
        assertContains(result.fullText, "## Profile")
        assertContains(result.fullText, "## History")
        assertContains(result.fullText, "## Stats")
    }

    @Test
    fun `buildFromPreset with subset preset only includes those sections`() {
        val result = builder.buildFromPreset(data, "Quick Summary")
        assertTrue(result != null)
        assertContains(result.fullText, "## Profile")
        assertContains(result.fullText, "## Stats")
        assertTrue("## History" !in result.fullText)
    }

    @Test
    fun `buildFromPreset with target model fits in budget`() {
        val result = builder.buildFromPreset(data, "For Claude")
        assertTrue(result != null)
        // Our tiny test data easily fits in Claude's 170K budget
        assertTrue(result.estimatedTokens < ContextWindow.effectiveInput(ContextWindow.CLAUDE_3_5))
    }

    @Test
    fun `buildFromPreset returns null for unknown preset`() {
        assertNull(builder.buildFromPreset(data, "Nonexistent"))
    }

    @Test
    fun `preset with target model still includes content`() {
        val result = builder.buildFromPreset(data, "For Claude")
        assertTrue(result != null)
        assertContains(result.fullText, "## Profile")
        assertTrue(result.fullText.isNotEmpty())
    }
}
