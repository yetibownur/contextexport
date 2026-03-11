package com.garrettmcbride.contextexport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class DiffExportTest {

    enum class Section { PROFILE, HISTORY, STATS }

    data class Data(
        val name: String = "Alice",
        val entries: List<String> = listOf("a", "b"),
        val score: Int = 42
    )

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
            Section.HISTORY to { sb: StringBuilder, data: Data ->
                sb.appendLine("## History")
                data.entries.forEach { sb.appendLine("- $it") }
                sb.appendLine()
            },
            Section.STATS to { sb: StringBuilder, data: Data ->
                sb.appendLine("## Stats")
                sb.appendLine("Score: ${data.score}")
                sb.appendLine()
            }
        )
    }

    private val data = Data()
    private val allSections = Section.entries.toSet()

    @Test
    fun `snapshot creates hashes for all enabled sections`() {
        val snap = builder.snapshot(data, allSections)
        assertEquals(3, snap.sectionHashes.size)
        assertTrue(snap.sectionHashes.containsKey("PROFILE"))
        assertTrue(snap.sectionHashes.containsKey("HISTORY"))
        assertTrue(snap.sectionHashes.containsKey("STATS"))
    }

    @Test
    fun `snapshot hashes are deterministic`() {
        val snap1 = builder.snapshot(data, allSections)
        val snap2 = builder.snapshot(data, allSections)
        assertEquals(snap1.sectionHashes, snap2.sectionHashes)
    }

    @Test
    fun `snapshot hashes change when data changes`() {
        val snap1 = builder.snapshot(data, allSections)
        val snap2 = builder.snapshot(data.copy(score = 99), allSections)
        assertEquals(snap1.sectionHashes["PROFILE"], snap2.sectionHashes["PROFILE"])
        assertTrue(snap1.sectionHashes["STATS"] != snap2.sectionHashes["STATS"])
    }

    @Test
    fun `buildPromptDiff with null previous is full export`() {
        val diff = builder.buildPromptDiff(data, allSections, null)
        assertTrue(diff.isFullExport)
        assertEquals(allSections, diff.changedSections)
        assertTrue(diff.unchangedSections.isEmpty())
        assertContains(diff.promptResult.fullText, "## Profile")
        assertContains(diff.promptResult.fullText, "## History")
        assertContains(diff.promptResult.fullText, "## Stats")
    }

    @Test
    fun `buildPromptDiff with same data shows no changes`() {
        val snap = builder.snapshot(data, allSections)
        val diff = builder.buildPromptDiff(data, allSections, snap)
        assertFalse(diff.isFullExport)
        assertTrue(diff.changedSections.isEmpty())
        assertEquals(allSections, diff.unchangedSections)
    }

    @Test
    fun `buildPromptDiff with changed data only includes changed sections`() {
        val snap = builder.snapshot(data, allSections)
        val newData = data.copy(score = 99)
        val diff = builder.buildPromptDiff(newData, allSections, snap)

        assertFalse(diff.isFullExport)
        assertEquals(setOf(Section.STATS), diff.changedSections)
        assertEquals(setOf(Section.PROFILE, Section.HISTORY), diff.unchangedSections)
        assertContains(diff.promptResult.fullText, "## Stats")
        assertTrue("## Profile" !in diff.promptResult.fullText)
        assertTrue("## History" !in diff.promptResult.fullText)
    }

    @Test
    fun `buildPromptDiff detects new sections not in snapshot`() {
        val snap = builder.snapshot(data, setOf(Section.PROFILE))
        val diff = builder.buildPromptDiff(data, allSections, snap)

        // HISTORY and STATS are new (not in snapshot), so they're "changed"
        assertTrue(Section.HISTORY in diff.changedSections)
        assertTrue(Section.STATS in diff.changedSections)
    }

    @Test
    fun `buildPromptDiff with multiple changes`() {
        val snap = builder.snapshot(data, allSections)
        val newData = data.copy(name = "Bob", score = 99)
        val diff = builder.buildPromptDiff(newData, allSections, snap)

        assertTrue(Section.PROFILE in diff.changedSections)
        assertTrue(Section.STATS in diff.changedSections)
        assertEquals(setOf(Section.HISTORY), diff.unchangedSections)
    }
}
