package com.garrettmcbride.contextexport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertTrue

// ── Test fixtures ───────────────────────────────────────────────────

enum class TestSection(val label: String) {
    ALPHA("Alpha"),
    BETA("Beta"),
    GAMMA("Gamma")
}

data class TestData(
    val title: String = "Test Export",
    val items: List<String> = listOf("item1", "item2", "item3")
)

class TestBuilder : AppPromptBuilder<TestData, TestSection>() {

    override fun appendHeader(sb: StringBuilder, data: TestData) {
        sb.appendLine("# ${data.title}")
        sb.appendLine()
    }

    override fun sectionRenderers() = listOf(
        TestSection.ALPHA to ::appendAlpha,
        TestSection.BETA to ::appendBeta,
        TestSection.GAMMA to ::appendGamma
    )

    override fun appendFooter(sb: StringBuilder, data: TestData) {
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine("End of export.")
    }

    private fun appendAlpha(sb: StringBuilder, data: TestData) {
        sb.appendLine("## Alpha Section")
        sb.appendLine("Alpha content here.")
        sb.appendLine()
    }

    private fun appendBeta(sb: StringBuilder, data: TestData) {
        sb.appendLine("## Beta Section")
        for (item in data.items) {
            sb.appendLine("- $item")
        }
        sb.appendLine()
    }

    private fun appendGamma(sb: StringBuilder, data: TestData) {
        sb.appendLine("## Gamma Section")
        sb.appendLine("Gamma content here.")
        sb.appendLine()
    }
}

// ── Tests ───────────────────────────────────────────────────────────

class AppPromptBuilderTest {

    private val builder = TestBuilder()
    private val data = TestData()
    private val allSections = TestSection.entries.toSet()

    @Test
    fun `buildPrompt with all sections includes header, all sections, and footer`() {
        val prompt = builder.buildPrompt(data, allSections)

        assertContains(prompt, "# Test Export")
        assertContains(prompt, "## Alpha Section")
        assertContains(prompt, "## Beta Section")
        assertContains(prompt, "## Gamma Section")
        assertContains(prompt, "End of export.")
    }

    @Test
    fun `buildPrompt with no sections includes only header and footer`() {
        val prompt = builder.buildPrompt(data, emptySet())

        assertContains(prompt, "# Test Export")
        assertContains(prompt, "End of export.")
        assertTrue("## Alpha" !in prompt)
        assertTrue("## Beta" !in prompt)
        assertTrue("## Gamma" !in prompt)
    }

    @Test
    fun `buildPrompt with subset includes only enabled sections`() {
        val prompt = builder.buildPrompt(data, setOf(TestSection.ALPHA, TestSection.GAMMA))

        assertContains(prompt, "## Alpha Section")
        assertContains(prompt, "## Gamma Section")
        assertTrue("## Beta" !in prompt)
    }

    @Test
    fun `buildPrompt preserves section order from sectionRenderers`() {
        val prompt = builder.buildPrompt(data, allSections)

        val alphaIndex = prompt.indexOf("## Alpha")
        val betaIndex = prompt.indexOf("## Beta")
        val gammaIndex = prompt.indexOf("## Gamma")

        assertTrue(alphaIndex < betaIndex, "Alpha should come before Beta")
        assertTrue(betaIndex < gammaIndex, "Beta should come before Gamma")
    }

    @Test
    fun `buildPrompt renders data items in Beta section`() {
        val prompt = builder.buildPrompt(data, setOf(TestSection.BETA))

        assertContains(prompt, "- item1")
        assertContains(prompt, "- item2")
        assertContains(prompt, "- item3")
    }

    @Test
    fun `buildPrompt trims trailing whitespace`() {
        val prompt = builder.buildPrompt(data, allSections)
        assertEquals(prompt, prompt.trimEnd())
    }

    @Test
    fun `buildPromptResult returns correct metadata`() {
        val result = builder.buildPromptResult(data, allSections)

        assertEquals(result.fullText, builder.buildPrompt(data, allSections))
        assertEquals(3, result.enabledSectionCount)
        assertTrue(result.sizeBytes > 0)
        assertTrue(result.estimatedTokens > 0)
        assertTrue(result.sizeKb > 0.0)
    }

    @Test
    fun `buildPromptResult with small prompt returns single chunk`() {
        val result = builder.buildPromptResult(data, allSections)

        assertEquals(1, result.chunkCount)
        assertTrue(!result.isMultiPart)
        assertEquals(result.fullText, result.chunks[0])
    }

    @Test
    fun `buildPromptResult with tiny chunkSize returns multiple chunks`() {
        val result = builder.buildPromptResult(data, allSections, chunkSize = 50)

        assertTrue(result.chunkCount > 1, "Should have multiple chunks with chunkSize=50")
        assertTrue(result.isMultiPart)
    }

    @Test
    fun `buildJsonPrompt produces valid JSON structure`() {
        val json = builder.buildJsonPrompt(data, allSections, totalSectionCount = 3)

        assertContains(json, "\"header\":")
        assertContains(json, "\"sections\":")
        assertContains(json, "\"ALPHA\":")
        assertContains(json, "\"BETA\":")
        assertContains(json, "\"GAMMA\":")
        assertContains(json, "\"footer\":")
        assertContains(json, "\"metadata\":")
        assertContains(json, "\"enabledSections\": 3")
        assertContains(json, "\"totalSections\": 3")
        assertContains(json, "\"estimatedTokens\":")
        assertContains(json, "\"sizeBytes\":")
    }

    @Test
    fun `buildJsonPrompt with subset only includes enabled sections`() {
        val json = builder.buildJsonPrompt(data, setOf(TestSection.ALPHA), totalSectionCount = 3)

        assertContains(json, "\"ALPHA\":")
        assertTrue("\"BETA\":" !in json)
        assertTrue("\"GAMMA\":" !in json)
        assertContains(json, "\"enabledSections\": 1")
        assertContains(json, "\"totalSections\": 3")
    }

    @Test
    fun `buildJsonPrompt escapes special characters`() {
        val specialData = TestData(title = "Test \"with quotes\" & newlines\nhere")
        val json = builder.buildJsonPrompt(specialData, emptySet())

        assertContains(json, "\\\"with quotes\\\"")
        assertContains(json, "\\n")
    }

    @Test
    fun `builder with no header or footer overrides works`() {
        val minimalBuilder = object : AppPromptBuilder<String, TestSection>() {
            override fun sectionRenderers(): List<Pair<TestSection, (StringBuilder, String) -> Unit>> = listOf(
                TestSection.ALPHA to { sb, data ->
                    sb.appendLine("Content: $data")
                }
            )
        }

        val prompt = minimalBuilder.buildPrompt("hello", setOf(TestSection.ALPHA))
        assertContains(prompt, "Content: hello")
    }

    // ── Format versioning tests ──

    @Test
    fun `formatVersion 0 does not add version comment`() {
        val prompt = builder.buildPrompt(data, allSections)
        assertTrue("format_version" !in prompt)
    }

    @Test
    fun `formatVersion greater than 0 adds version comment`() {
        val versionedBuilder = object : AppPromptBuilder<TestData, TestSection>() {
            override val formatVersion = 2
            override fun appendHeader(sb: StringBuilder, data: TestData) {
                sb.appendLine("# Test")
            }
            override fun sectionRenderers(): List<Pair<TestSection, (StringBuilder, TestData) -> Unit>> = emptyList()
        }

        val prompt = versionedBuilder.buildPrompt(data, emptySet())
        assertContains(prompt, "<!-- format_version: 2 -->")
    }

    @Test
    fun `formatVersion appears in JSON output`() {
        val versionedBuilder = object : AppPromptBuilder<TestData, TestSection>() {
            override val formatVersion = 3
            override fun appendHeader(sb: StringBuilder, data: TestData) {
                sb.appendLine("# Test")
            }
            override fun sectionRenderers(): List<Pair<TestSection, (StringBuilder, TestData) -> Unit>> = emptyList()
        }

        val json = versionedBuilder.buildJsonPrompt(data, emptySet())
        assertContains(json, "\"formatVersion\": 3")
    }

    // ── Section item count tests ──

    @Test
    fun `countSectionItems returns 0 by default`() {
        val counts = builder.countSectionItems(data)
        assertEquals(0, counts[TestSection.ALPHA])
        assertEquals(0, counts[TestSection.BETA])
        assertEquals(0, counts[TestSection.GAMMA])
    }

    @Test
    fun `countSectionItems with override returns correct counts`() {
        val countingBuilder = object : AppPromptBuilder<TestData, TestSection>() {
            override fun sectionRenderers(): List<Pair<TestSection, (StringBuilder, TestData) -> Unit>> = listOf(
                TestSection.ALPHA to { _, _ -> },
                TestSection.BETA to { _, _ -> }
            )
            override fun sectionItemCount(section: TestSection, data: TestData): Int = when (section) {
                TestSection.ALPHA -> 1
                TestSection.BETA -> data.items.size
                else -> 0
            }
        }

        val counts = countingBuilder.countSectionItems(data)
        assertEquals(1, counts[TestSection.ALPHA])
        assertEquals(3, counts[TestSection.BETA])
    }

    // ── Configurable chunk labels test ──

    @Test
    fun `appName brands chunk labels`() {
        val brandedBuilder = object : AppPromptBuilder<TestData, TestSection>() {
            override val appName = "MyApp"
            override fun appendHeader(sb: StringBuilder, data: TestData) {
                sb.appendLine("# Header")
            }
            override fun sectionRenderers(): List<Pair<TestSection, (StringBuilder, TestData) -> Unit>> = listOf(
                TestSection.ALPHA to { sb, _ -> sb.append("a".repeat(8000)) },
                TestSection.BETA to { sb, _ -> sb.append("b".repeat(8000)) }
            )
        }

        val result = brandedBuilder.buildPromptResult(data, setOf(TestSection.ALPHA, TestSection.BETA), chunkSize = 10000)
        assertTrue(result.isMultiPart, "Should have multiple chunks")
        // Check that at least one chunk mentions "MyApp"
        val hasAppName = result.chunks.any { "MyApp" in it }
        assertTrue(hasAppName, "Chunks should contain app name in labels")
    }
}
