package com.garrettmcbride.contextexport

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests that verify multiple SDK features work correctly together.
 */
class CrossFeatureTest {

    enum class Section { LORE, INVENTORY, NOTES, EMPTY_SECTION }

    data class Data(
        val lore: String = "Ancient history.",
        val items: List<String> = listOf("sword", "shield"),
        val notes: String = "Session 1 notes."
    )

    // ── cacheOptimized + autoSkipEmpty ──────────────────────────────

    @Test
    fun `cacheOptimized with autoSkipEmpty skips empty then sorts remaining`() {
        val builder = object : AppPromptBuilder<Data, Section>() {
            override val autoSkipEmpty = true
            override val cacheOptimized = true

            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                // Listed in "wrong" order: VOLATILE first
                Section.NOTES to { sb: StringBuilder, data: Data ->
                    sb.appendLine("## Notes")
                    sb.appendLine(data.notes)
                    sb.appendLine()
                },
                Section.EMPTY_SECTION to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Empty")
                    sb.appendLine("Should not appear.")
                    sb.appendLine()
                },
                Section.LORE to { sb: StringBuilder, data: Data ->
                    sb.appendLine("## Lore")
                    sb.appendLine(data.lore)
                    sb.appendLine()
                },
                Section.INVENTORY to { sb: StringBuilder, data: Data ->
                    sb.appendLine("## Inventory")
                    data.items.forEach { sb.appendLine("- $it") }
                    sb.appendLine()
                }
            )

            override fun sectionItemCount(section: Section, data: Data): Int = when (section) {
                Section.LORE -> 1
                Section.INVENTORY -> data.items.size
                Section.NOTES -> 1
                Section.EMPTY_SECTION -> 0  // always empty
            }

            override fun sectionVolatility(section: Section): Volatility = when (section) {
                Section.LORE -> Volatility.STABLE
                Section.INVENTORY -> Volatility.MODERATE
                Section.NOTES -> Volatility.VOLATILE
                Section.EMPTY_SECTION -> Volatility.MODERATE
            }
        }

        val data = Data()
        val allSections = Section.entries.toSet()
        val prompt = builder.buildPrompt(data, allSections)

        // EMPTY_SECTION should be skipped
        assertTrue("## Empty" !in prompt, "Empty section should be skipped")

        // Remaining sections should be sorted: STABLE (Lore) < MODERATE (Inventory) < VOLATILE (Notes)
        val loreIdx = prompt.indexOf("## Lore")
        val invIdx = prompt.indexOf("## Inventory")
        val notesIdx = prompt.indexOf("## Notes")
        assertTrue(loreIdx < invIdx, "STABLE Lore should come before MODERATE Inventory")
        assertTrue(invIdx < notesIdx, "MODERATE Inventory should come before VOLATILE Notes")
    }

    // ── interceptors + buildPromptDiff ──────────────────────────────

    @Test
    fun `buildPromptDiff with deterministic interceptor detects real changes`() {
        val builder = object : AppPromptBuilder<Data, Section>() {
            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                Section.LORE to { sb: StringBuilder, data: Data ->
                    sb.appendLine("## Lore")
                    sb.appendLine(data.lore)
                    sb.appendLine()
                },
                Section.NOTES to { sb: StringBuilder, data: Data ->
                    sb.appendLine("## Notes")
                    sb.appendLine(data.notes)
                    sb.appendLine()
                }
            )

            override fun sectionInterceptors() = listOf(
                // Deterministic interceptor: uppercase everything
                SectionInterceptor { _, content -> content.uppercase() }
            )
        }

        val data = Data()
        val sections = setOf(Section.LORE, Section.NOTES)

        // Take snapshot
        val snap = builder.snapshot(data, sections)

        // Same data → no changes
        val diff1 = builder.buildPromptDiff(data, sections, snap)
        assertFalse(diff1.isFullExport)
        assertTrue(diff1.changedSections.isEmpty(), "Same data should show no changes")

        // Changed data → should detect change
        val newData = data.copy(notes = "Session 2 notes.")
        val diff2 = builder.buildPromptDiff(newData, sections, snap)
        assertTrue(Section.NOTES in diff2.changedSections, "Notes should be detected as changed")
        assertEquals(setOf(Section.LORE), diff2.unchangedSections)
    }

    // ── presets + autoSkipEmpty ──────────────────────────────────────

    @Test
    fun `preset with autoSkipEmpty filters empty sections`() {
        val builder = object : AppPromptBuilder<Data, Section>() {
            override val autoSkipEmpty = true

            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                Section.LORE to { sb: StringBuilder, data: Data ->
                    sb.appendLine("## Lore")
                    sb.appendLine(data.lore)
                    sb.appendLine()
                },
                Section.EMPTY_SECTION to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Empty")
                    sb.appendLine("Should not appear.")
                    sb.appendLine()
                },
                Section.NOTES to { sb: StringBuilder, data: Data ->
                    sb.appendLine("## Notes")
                    sb.appendLine(data.notes)
                    sb.appendLine()
                }
            )

            override fun sectionItemCount(section: Section, data: Data): Int = when (section) {
                Section.LORE -> 1
                Section.NOTES -> 1
                Section.EMPTY_SECTION -> 0
                else -> 0
            }

            override fun exportPresets() = listOf(
                ExportPreset("Full", Section.entries.toSet()),
                ExportPreset("Lore Only", setOf(Section.LORE))
            )
        }

        val result = builder.buildFromPreset(Data(), "Full")
        assertTrue(result != null)
        assertContains(result.fullText, "## Lore")
        assertContains(result.fullText, "## Notes")
        assertTrue("## Empty" !in result.fullText, "Empty section should be skipped even in Full preset")
    }

    // ── JSON escaping with control characters ───────────────────────

    @Test
    fun `buildJsonPrompt escapes control characters`() {
        val builder = object : AppPromptBuilder<Data, Section>() {
            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                Section.LORE to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Lore")
                    // Put control chars mid-string so trimEnd() doesn't strip them
                    sb.appendLine("A\u0000B\u001FC\u2028D\u2029E")
                }
            )
        }

        val json = builder.buildJsonPrompt(Data(), setOf(Section.LORE))
        // Control characters should be escaped as \uXXXX
        assertContains(json, "\\u0000")
        assertContains(json, "\\u001f")
        assertContains(json, "\\u2028")
        assertContains(json, "\\u2029")
    }

    // ── interceptors + JSON output keys unchanged ───────────────────

    @Test
    fun `interceptors transform content but preserve section keys in JSON`() {
        val builder = object : AppPromptBuilder<Data, Section>() {
            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                Section.LORE to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Lore")
                    sb.appendLine("Original lore text.")
                    sb.appendLine()
                }
            )

            override fun sectionInterceptors() = listOf(
                SectionInterceptor { _, content -> content.uppercase() }
            )
        }

        val json = builder.buildJsonPrompt(Data(), setOf(Section.LORE))
        // Key should still be "LORE" (enum name, not transformed)
        assertContains(json, "\"LORE\":")
        // Content should be uppercased
        assertContains(json, "ORIGINAL LORE TEXT")
    }

    // ── structured messages + cacheOptimized ────────────────────────

    @Test
    fun `buildStructuredPrompt respects cache-optimized ordering`() {
        val builder = object : AppPromptBuilder<Data, Section>() {
            override val cacheOptimized = true

            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                // Listed volatile first
                Section.NOTES to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Notes")
                    sb.appendLine()
                },
                Section.LORE to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Lore")
                    sb.appendLine()
                }
            )

            override fun sectionVolatility(section: Section): Volatility = when (section) {
                Section.LORE -> Volatility.STABLE
                Section.NOTES -> Volatility.VOLATILE
                else -> Volatility.MODERATE
            }

            override fun sectionRole(section: Section): MessageRole = when (section) {
                Section.LORE -> MessageRole.SYSTEM
                Section.NOTES -> MessageRole.USER
                else -> MessageRole.USER
            }
        }

        val messages = builder.buildStructuredPrompt(Data(), setOf(Section.LORE, Section.NOTES))
        // STABLE (Lore/SYSTEM) should come before VOLATILE (Notes/USER)
        assertEquals(2, messages.size)
        assertEquals(MessageRole.SYSTEM, messages[0].role)
        assertContains(messages[0].content, "Lore")
        assertEquals(MessageRole.USER, messages[1].role)
        assertContains(messages[1].content, "Notes")
    }

    // ── pluggable tokenizer + interceptors ──────────────────────────

    @Test
    fun `custom tokenizer counts tokens after interceptor transformation`() {
        val builder = object : AppPromptBuilder<Data, Section>() {
            // Tokenizer that counts characters (1 char = 1 token)
            override val tokenizer = Tokenizer { text -> text.length }

            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                Section.LORE to { sb: StringBuilder, _: Data ->
                    sb.append("short") // 5 chars
                }
            )

            override fun sectionInterceptors() = listOf(
                // Interceptor that makes content much longer
                SectionInterceptor { _, _ -> "a".repeat(100) } // 100 chars
            )
        }

        val result = builder.buildPromptResult(Data(), setOf(Section.LORE))
        val stats = result.sectionBreakdown["LORE"]
        assertTrue(stats != null)
        // Tokenizer should count post-interception content (100 chars = 100 tokens)
        assertEquals(100, stats.tokens)
        assertEquals(100, stats.chars)
    }

    // ── PromptCompressor.savings() returns correct token count ──────

    @Test
    fun `savings returns accurate token estimate for saved characters`() {
        // Create a prompt with lots of redundant whitespace
        val input = "line1" + "\n".repeat(10) + "line2" + "   ".repeat(20) + "\n".repeat(5)
        val (charsSaved, tokensSaved) = PromptCompressor.savings(input)

        assertTrue(charsSaved > 0, "Should save characters")
        // Token savings should be roughly charsSaved / 4
        val expectedTokens = (charsSaved / TokenEstimator.DEFAULT_CHARS_PER_TOKEN).toInt()
        assertEquals(expectedTokens, tokensSaved, "Token savings should be charsSaved / 4.0")
    }

    // ── section groups + presets ─────────────────────────────────────

    @Test
    fun `section groups can feed into buildPromptResult`() {
        val builder = object : AppPromptBuilder<Data, Section>() {
            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                Section.LORE to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Lore")
                    sb.appendLine()
                },
                Section.INVENTORY to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Inventory")
                    sb.appendLine()
                },
                Section.NOTES to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Notes")
                    sb.appendLine()
                }
            )

            override fun sectionGroups() = mapOf(
                "World" to setOf(Section.LORE),
                "Session" to setOf(Section.NOTES, Section.INVENTORY)
            )
        }

        val sections = builder.sectionsFromGroups("World")
        val result = builder.buildPromptResult(Data(), sections)
        assertContains(result.fullText, "## Lore")
        assertTrue("## Notes" !in result.fullText)
        assertTrue("## Inventory" !in result.fullText)
    }
}
