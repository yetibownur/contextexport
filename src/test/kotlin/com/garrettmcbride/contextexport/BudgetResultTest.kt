package com.garrettmcbride.contextexport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BudgetResultTest {

    enum class Section { SMALL, MEDIUM, LARGE }

    data class Data(val items: List<String> = listOf("a", "b", "c"))

    private val builder = object : AppPromptBuilder<Data, Section>() {
        override fun appendHeader(sb: StringBuilder, data: Data) {
            sb.appendLine("# Header")
            sb.appendLine()
        }

        override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
            Section.SMALL to { sb: StringBuilder, _: Data ->
                sb.appendLine("## Small")
                sb.appendLine("Tiny.")
                sb.appendLine()
            },
            Section.MEDIUM to { sb: StringBuilder, _: Data ->
                sb.appendLine("## Medium")
                sb.appendLine("A".repeat(200))
                sb.appendLine()
            },
            Section.LARGE to { sb: StringBuilder, _: Data ->
                sb.appendLine("## Large")
                sb.appendLine("B".repeat(2000))
                sb.appendLine()
            }
        )
    }

    private val data = Data()
    private val allSections = Section.entries.toSet()

    @Test
    fun `within budget returns all sections with no drops`() {
        val result = builder.buildPromptWithBudget(data, allSections, maxTokens = 100_000)
        assertTrue(result.withinBudget)
        assertTrue(result.droppedSections.isEmpty())
        assertEquals(allSections, result.includedSections)
        assertEquals(100_000, result.budgetTokens)
    }

    @Test
    fun `tight budget drops lowest priority sections`() {
        // Get full token count first
        val fullResult = builder.buildPromptResult(data, allSections)
        // Set budget to something that forces at least one drop
        // LARGE is last in sectionRenderers → lowest priority → dropped first
        val smallBudget = fullResult.estimatedTokens / 2
        val result = builder.buildPromptWithBudget(data, allSections, maxTokens = smallBudget)

        assertTrue(result.droppedSections.isNotEmpty(), "Should drop at least one section")
        // LARGE has lowest priority (listed last), should be dropped first
        assertTrue(Section.LARGE in result.droppedSections, "LARGE (lowest priority) should be dropped first")
    }

    @Test
    fun `very tight budget may drop all sections`() {
        // Budget of 1 token — can't even fit the header
        val result = builder.buildPromptWithBudget(data, allSections, maxTokens = 1)
        // All sections should be dropped, and it still may not fit
        assertEquals(allSections, result.droppedSections)
        assertTrue(result.includedSections.isEmpty())
        assertFalse(result.withinBudget, "Header alone should exceed 1 token")
    }

    @Test
    fun `budget result prompt is smaller than full prompt`() {
        val fullResult = builder.buildPromptResult(data, allSections)
        val tightBudget = fullResult.estimatedTokens / 2
        val budgetResult = builder.buildPromptWithBudget(data, allSections, maxTokens = tightBudget)

        assertTrue(
            budgetResult.promptResult.estimatedTokens <= fullResult.estimatedTokens,
            "Budget result should be smaller or equal to full result"
        )
    }

    @Test
    fun `empty sections set returns within budget`() {
        val result = builder.buildPromptWithBudget(data, emptySet(), maxTokens = 100)
        assertTrue(result.droppedSections.isEmpty())
        assertTrue(result.includedSections.isEmpty())
    }

    @Test
    fun `custom priority via override changes drop order`() {
        // Builder where SMALL has lowest priority instead of LARGE
        val customBuilder = object : AppPromptBuilder<Data, Section>() {
            override fun appendHeader(sb: StringBuilder, data: Data) {
                sb.appendLine("# Header")
                sb.appendLine()
            }

            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                Section.SMALL to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Small")
                    sb.appendLine("Tiny.")
                    sb.appendLine()
                },
                Section.MEDIUM to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Medium")
                    sb.appendLine("A".repeat(200))
                    sb.appendLine()
                },
                Section.LARGE to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Large")
                    sb.appendLine("B".repeat(2000))
                    sb.appendLine()
                }
            )

            override fun sectionPriority(section: Section): Int = when (section) {
                Section.LARGE -> 100   // highest priority
                Section.MEDIUM -> 50
                Section.SMALL -> 1     // lowest priority — dropped first
            }
        }

        val fullResult = customBuilder.buildPromptResult(data, allSections)
        // Set budget that forces drops but can still fit LARGE
        val budget = fullResult.estimatedTokens - 10
        val result = customBuilder.buildPromptWithBudget(data, allSections, maxTokens = budget)

        if (result.droppedSections.isNotEmpty()) {
            // SMALL should be dropped first since it has lowest priority
            assertTrue(Section.SMALL in result.droppedSections, "SMALL (lowest priority) should be dropped first")
        }
    }

    @Test
    fun `budget result contains valid prompt result`() {
        val result = builder.buildPromptWithBudget(data, allSections, maxTokens = 100_000)
        assertTrue(result.promptResult.fullText.isNotEmpty())
        assertTrue(result.promptResult.estimatedTokens > 0)
        assertTrue(result.promptResult.chunks.isNotEmpty())
    }
}
