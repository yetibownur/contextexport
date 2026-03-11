package com.garrettmcbride.contextexport

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class AutoSkipEmptyTest {

    enum class Section { HAS_DATA, EMPTY, ALSO_EMPTY }

    data class Data(val items: List<String> = listOf("a", "b"))

    // Builder with autoSkipEmpty = false (default behavior)
    private val defaultBuilder = object : AppPromptBuilder<Data, Section>() {
        override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
            Section.HAS_DATA to { sb: StringBuilder, data: Data ->
                sb.appendLine("## Has Data")
                data.items.forEach { sb.appendLine("- $it") }
                sb.appendLine()
            },
            Section.EMPTY to { sb: StringBuilder, _: Data ->
                sb.appendLine("## Empty Section")
                sb.appendLine("This always renders even if count is 0.")
                sb.appendLine()
            },
            Section.ALSO_EMPTY to { sb: StringBuilder, _: Data ->
                sb.appendLine("## Also Empty")
                sb.appendLine()
            }
        )

        override fun sectionItemCount(section: Section, data: Data): Int = when (section) {
            Section.HAS_DATA -> data.items.size
            Section.EMPTY -> 0
            Section.ALSO_EMPTY -> 0
        }
    }

    // Builder with autoSkipEmpty = true
    private val skipBuilder = object : AppPromptBuilder<Data, Section>() {
        override val autoSkipEmpty = true

        override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
            Section.HAS_DATA to { sb: StringBuilder, data: Data ->
                sb.appendLine("## Has Data")
                data.items.forEach { sb.appendLine("- $it") }
                sb.appendLine()
            },
            Section.EMPTY to { sb: StringBuilder, _: Data ->
                sb.appendLine("## Empty Section")
                sb.appendLine("This should be skipped.")
                sb.appendLine()
            },
            Section.ALSO_EMPTY to { sb: StringBuilder, _: Data ->
                sb.appendLine("## Also Empty")
                sb.appendLine()
            }
        )

        override fun sectionItemCount(section: Section, data: Data): Int = when (section) {
            Section.HAS_DATA -> data.items.size
            Section.EMPTY -> 0
            Section.ALSO_EMPTY -> 0
        }
    }

    private val data = Data()
    private val allSections = Section.entries.toSet()

    @Test
    fun `default autoSkipEmpty false renders all sections including empty`() {
        val prompt = defaultBuilder.buildPrompt(data, allSections)
        assertContains(prompt, "## Has Data")
        assertContains(prompt, "## Empty Section")
        assertContains(prompt, "## Also Empty")
    }

    @Test
    fun `autoSkipEmpty true skips sections with item count 0`() {
        val prompt = skipBuilder.buildPrompt(data, allSections)
        assertContains(prompt, "## Has Data")
        assertTrue("## Empty Section" !in prompt, "Empty section should be skipped")
        assertTrue("## Also Empty" !in prompt, "Also empty section should be skipped")
    }

    @Test
    fun `autoSkipEmpty true still renders sections with items`() {
        val prompt = skipBuilder.buildPrompt(data, allSections)
        assertContains(prompt, "## Has Data")
        assertContains(prompt, "- a")
        assertContains(prompt, "- b")
    }

    @Test
    fun `autoSkipEmpty true works with buildJsonPrompt`() {
        val json = skipBuilder.buildJsonPrompt(data, allSections)
        assertContains(json, "\"HAS_DATA\":")
        assertTrue("\"EMPTY\":" !in json, "Empty section should not appear in JSON")
        assertTrue("\"ALSO_EMPTY\":" !in json, "Also empty section should not appear in JSON")
    }

    @Test
    fun `autoSkipEmpty true works with buildPromptResult`() {
        val result = skipBuilder.buildPromptResult(data, allSections)
        assertContains(result.fullText, "## Has Data")
        assertTrue("## Empty Section" !in result.fullText)
    }
}
