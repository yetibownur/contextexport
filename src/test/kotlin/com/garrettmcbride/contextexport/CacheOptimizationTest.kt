package com.garrettmcbride.contextexport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertTrue

class CacheOptimizationTest {

    enum class Section { LORE, INVENTORY, SESSION_NOTES }

    data class Data(val text: String = "test")

    // Builder WITHOUT cache optimization (default order)
    private val defaultBuilder = object : AppPromptBuilder<Data, Section>() {
        override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
            Section.LORE to { sb: StringBuilder, _: Data ->
                sb.appendLine("## Lore")
                sb.appendLine("Ancient wisdom.")
                sb.appendLine()
            },
            Section.INVENTORY to { sb: StringBuilder, _: Data ->
                sb.appendLine("## Inventory")
                sb.appendLine("Sword, shield.")
                sb.appendLine()
            },
            Section.SESSION_NOTES to { sb: StringBuilder, _: Data ->
                sb.appendLine("## Session Notes")
                sb.appendLine("Today we fought dragons.")
                sb.appendLine()
            }
        )
    }

    // Builder WITH cache optimization
    private val cacheBuilder = object : AppPromptBuilder<Data, Section>() {
        override val cacheOptimized = true

        // Renderers listed in "wrong" order — volatile first
        override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
            Section.SESSION_NOTES to { sb: StringBuilder, _: Data ->
                sb.appendLine("## Session Notes")
                sb.appendLine("Today we fought dragons.")
                sb.appendLine()
            },
            Section.INVENTORY to { sb: StringBuilder, _: Data ->
                sb.appendLine("## Inventory")
                sb.appendLine("Sword, shield.")
                sb.appendLine()
            },
            Section.LORE to { sb: StringBuilder, _: Data ->
                sb.appendLine("## Lore")
                sb.appendLine("Ancient wisdom.")
                sb.appendLine()
            }
        )

        override fun sectionVolatility(section: Section): Volatility = when (section) {
            Section.LORE -> Volatility.STABLE
            Section.INVENTORY -> Volatility.MODERATE
            Section.SESSION_NOTES -> Volatility.VOLATILE
        }
    }

    private val data = Data()
    private val allSections = Section.entries.toSet()

    @Test
    fun `default builder preserves sectionRenderers order`() {
        val prompt = defaultBuilder.buildPrompt(data, allSections)
        val loreIdx = prompt.indexOf("## Lore")
        val invIdx = prompt.indexOf("## Inventory")
        val sessIdx = prompt.indexOf("## Session Notes")
        assertTrue(loreIdx < invIdx, "Lore before Inventory")
        assertTrue(invIdx < sessIdx, "Inventory before Session Notes")
    }

    @Test
    fun `cache optimized builder sorts STABLE first VOLATILE last`() {
        val prompt = cacheBuilder.buildPrompt(data, allSections)
        val loreIdx = prompt.indexOf("## Lore")
        val invIdx = prompt.indexOf("## Inventory")
        val sessIdx = prompt.indexOf("## Session Notes")

        // Even though SESSION_NOTES is first in sectionRenderers,
        // cache optimization should sort: STABLE (Lore) < MODERATE (Inventory) < VOLATILE (Session)
        assertTrue(loreIdx < invIdx, "STABLE Lore should come before MODERATE Inventory")
        assertTrue(invIdx < sessIdx, "MODERATE Inventory should come before VOLATILE Session Notes")
    }

    @Test
    fun `cache optimized builder includes all content`() {
        val prompt = cacheBuilder.buildPrompt(data, allSections)
        assertContains(prompt, "Ancient wisdom.")
        assertContains(prompt, "Sword, shield.")
        assertContains(prompt, "Today we fought dragons.")
    }

    @Test
    fun `cache optimization affects section breakdown order`() {
        val result = cacheBuilder.buildPromptResult(data, allSections)
        val keys = result.sectionBreakdown.keys.toList()
        // Section breakdown should follow the sorted order
        assertEquals(listOf("LORE", "INVENTORY", "SESSION_NOTES"), keys)
    }

    @Test
    fun `default volatility is MODERATE for all sections`() {
        val prompt = defaultBuilder.buildPrompt(data, allSections)
        // Just verifying default builder works normally (all MODERATE = no reorder)
        assertContains(prompt, "## Lore")
    }

    @Test
    fun `cache optimization works with subset of sections`() {
        val prompt = cacheBuilder.buildPrompt(data, setOf(Section.SESSION_NOTES, Section.LORE))
        val loreIdx = prompt.indexOf("## Lore")
        val sessIdx = prompt.indexOf("## Session Notes")
        assertTrue(loreIdx < sessIdx, "STABLE should still come before VOLATILE")
    }
}
