package com.garrettmcbride.contextexport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertTrue

class SectionGroupTest {

    enum class Section { LORE, LOCATIONS, PCS, NPCS, NOTES }

    data class Data(val text: String = "test")

    private val builder = object : AppPromptBuilder<Data, Section>() {
        override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
            Section.LORE to { sb: StringBuilder, _: Data ->
                sb.appendLine("## Lore")
                sb.appendLine()
            },
            Section.LOCATIONS to { sb: StringBuilder, _: Data ->
                sb.appendLine("## Locations")
                sb.appendLine()
            },
            Section.PCS to { sb: StringBuilder, _: Data ->
                sb.appendLine("## PCs")
                sb.appendLine()
            },
            Section.NPCS to { sb: StringBuilder, _: Data ->
                sb.appendLine("## NPCs")
                sb.appendLine()
            },
            Section.NOTES to { sb: StringBuilder, _: Data ->
                sb.appendLine("## Notes")
                sb.appendLine()
            }
        )

        override fun sectionGroups() = mapOf(
            "World" to setOf(Section.LORE, Section.LOCATIONS),
            "Characters" to setOf(Section.PCS, Section.NPCS),
            "Session" to setOf(Section.NOTES)
        )
    }

    private val data = Data()

    @Test
    fun `sectionsFromGroups returns correct sections for single group`() {
        val sections = builder.sectionsFromGroups("World")
        assertEquals(setOf(Section.LORE, Section.LOCATIONS), sections)
    }

    @Test
    fun `sectionsFromGroups returns union for multiple groups`() {
        val sections = builder.sectionsFromGroups("World", "Characters")
        assertEquals(setOf(Section.LORE, Section.LOCATIONS, Section.PCS, Section.NPCS), sections)
    }

    @Test
    fun `sectionsFromGroups returns empty for unknown group`() {
        val sections = builder.sectionsFromGroups("Nonexistent")
        assertTrue(sections.isEmpty())
    }

    @Test
    fun `sectionsFromGroups with all groups gives all sections`() {
        val sections = builder.sectionsFromGroups("World", "Characters", "Session")
        assertEquals(Section.entries.toSet(), sections)
    }

    @Test
    fun `availableGroups returns all group names`() {
        val groups = builder.availableGroups()
        assertEquals(setOf("World", "Characters", "Session"), groups)
    }

    @Test
    fun `building prompt with group sections works`() {
        val sections = builder.sectionsFromGroups("Characters")
        val prompt = builder.buildPrompt(data, sections)
        assertContains(prompt, "## PCs")
        assertContains(prompt, "## NPCs")
        assertTrue("## Lore" !in prompt)
        assertTrue("## Notes" !in prompt)
    }

    @Test
    fun `empty sectionGroups returns empty available groups`() {
        val emptyBuilder = object : AppPromptBuilder<Data, Section>() {
            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                Section.LORE to { sb: StringBuilder, _: Data -> sb.appendLine("lore") }
            )
        }
        assertTrue(emptyBuilder.availableGroups().isEmpty())
    }
}
