package com.garrettmcbride.contextexport

/**
 * Generic base class for building structured AI prompts from app data.
 *
 * Subclass this with your app's data model and section enum, then override
 * [appendHeader], [sectionRenderers], and optionally [appendFooter].
 *
 * The build loop is simple:
 * 1. Call [appendHeader] (title, description, AI instructions)
 * 2. Iterate [sectionRenderers] — call each renderer whose section is enabled
 * 3. Call [appendFooter] (closing instructions, end marker)
 *
 * ## Example
 * ```kotlin
 * enum class MySection(val label: String) {
 *     PROFILE("Profile"),
 *     HISTORY("History")
 * }
 *
 * data class MyData(val name: String, val entries: List<String>)
 *
 * class MyBuilder : AppPromptBuilder<MyData, MySection>() {
 *     override val appName = "MyApp"
 *     override val formatVersion = 1
 *
 *     override fun appendHeader(sb: StringBuilder, data: MyData) {
 *         sb.appendLine("# ${data.name} — Export")
 *         sb.appendLine()
 *     }
 *
 *     override fun sectionRenderers() = listOf(
 *         MySection.PROFILE to ::appendProfile,
 *         MySection.HISTORY to ::appendHistory
 *     )
 *
 *     private fun appendProfile(sb: StringBuilder, data: MyData) { /* ... */ }
 *     private fun appendHistory(sb: StringBuilder, data: MyData) { /* ... */ }
 * }
 *
 * val result = MyBuilder().buildPromptResult(myData, MySection.entries.toSet())
 * // result.fullText, result.chunks, result.estimatedTokens, etc.
 * ```
 *
 * @param TData Your app's export data bag (data class, POJO, anything).
 * @param TSection Your section enum. Each entry maps to a renderer and a UI toggle.
 */
open class AppPromptBuilder<TData, TSection : Enum<TSection>> {

    /**
     * Override to set your app's name for branded chunk labels.
     *
     * When set, multi-part chunk labels will read:
     * "[Part 2 of 3] — Continuation of MyApp data export."
     *
     * Default is empty (produces "Continuation of data export.").
     */
    protected open val appName: String = ""

    /**
     * Override to set the export format version.
     *
     * When > 0, a `<!-- format_version: N -->` comment is prepended to the prompt.
     * This helps AI models understand which schema version they're parsing.
     *
     * Default is 0 (no version line).
     */
    protected open val formatVersion: Int = 0

    /**
     * Builds the prompt and returns the raw text string.
     *
     * For richer output (chunks, token estimate, metadata), use [buildPromptResult] instead.
     *
     * @param data Your app's data bag.
     * @param enabledSections Which sections to include.
     * @return The assembled prompt string.
     */
    open fun buildPrompt(
        data: TData,
        enabledSections: Set<TSection>
    ): String {
        val sb = StringBuilder()

        if (formatVersion > 0) {
            sb.appendLine("<!-- format_version: $formatVersion -->")
        }

        appendHeader(sb, data)

        for ((section, renderer) in sectionRenderers()) {
            if (section in enabledSections) renderer(sb, data)
        }

        appendFooter(sb, data)

        return sb.toString().trimEnd()
    }

    /**
     * Builds the prompt and returns a [PromptResult] with the full text,
     * pre-chunked parts, and size metadata.
     *
     * This is the recommended entry point for UI code that needs to display
     * prompt previews, show size info, or support multi-part copying.
     *
     * @param data Your app's data bag.
     * @param enabledSections Which sections to include.
     * @param chunkSize Maximum characters per chunk (default: [PromptChunker.DEFAULT_CHUNK_SIZE]).
     * @return A [PromptResult] containing the prompt text, chunks, and metadata.
     */
    fun buildPromptResult(
        data: TData,
        enabledSections: Set<TSection>,
        chunkSize: Int = PromptChunker.DEFAULT_CHUNK_SIZE
    ): PromptResult {
        val fullText = buildPrompt(data, enabledSections)
        val chunks = PromptChunker.chunk(fullText, chunkSize, appName)
        val sizeBytes = fullText.toByteArray(Charsets.UTF_8).size

        return PromptResult(
            fullText = fullText,
            chunks = chunks,
            sizeBytes = sizeBytes,
            estimatedTokens = TokenEstimator.estimate(fullText),
            enabledSectionCount = enabledSections.size,
            chunkSize = chunkSize
        )
    }

    /**
     * Counts items per section by calling [sectionItemCount] for each section.
     *
     * Override [sectionItemCount] to return the count for each section,
     * then call this from your ViewModel instead of manually building a counts map.
     *
     * @param data Your app's data bag.
     * @return Map of section to item count.
     */
    fun countSectionItems(data: TData): Map<TSection, Int> {
        val counts = mutableMapOf<TSection, Int>()
        for ((section, _) in sectionRenderers()) {
            counts[section] = sectionItemCount(section, data)
        }
        return counts
    }

    /**
     * Override to return the number of items in a section.
     *
     * Used by [countSectionItems] to build section count maps for UI display
     * (e.g., showing "Hirelings (5)" on filter chips).
     *
     * Default returns 0 for all sections.
     *
     * @param section The section to count items for.
     * @param data Your app's data bag.
     * @return Number of items in this section.
     */
    protected open fun sectionItemCount(section: TSection, data: TData): Int = 0

    /**
     * Override to write the prompt header — title, description, and instructions
     * that tell the AI model how to interpret the data that follows.
     *
     * Called once at the start of [buildPrompt], before any section renderers.
     *
     * @param sb The StringBuilder to append to.
     * @param data Your app's data bag.
     */
    protected open fun appendHeader(sb: StringBuilder, data: TData) {}

    /**
     * Override to return a list of section-to-renderer pairs.
     *
     * Each pair maps a [TSection] enum value to a function that appends that
     * section's content to the StringBuilder. Sections are rendered in the order
     * they appear in this list — only those present in `enabledSections` are called.
     *
     * @return Ordered list of (section, renderer) pairs.
     */
    protected open fun sectionRenderers(): List<Pair<TSection, (StringBuilder, TData) -> Unit>> = emptyList()

    /**
     * Override to write a closing block after all sections.
     *
     * Use this for end-of-export markers, final instructions to the AI,
     * or summary metadata. Called once at the end of [buildPrompt].
     *
     * @param sb The StringBuilder to append to.
     * @param data Your app's data bag.
     */
    protected open fun appendFooter(sb: StringBuilder, data: TData) {}

    /**
     * Builds a structured JSON string with header, individual sections, footer, and metadata.
     *
     * Each enabled section is rendered independently so API consumers can send
     * individual sections as separate messages or pick specific sections programmatically.
     *
     * The JSON is hand-built (no library dependency) to keep the SDK zero-dep.
     *
     * @param data Your app's data bag.
     * @param enabledSections Which sections to include.
     * @param totalSectionCount Total available sections (for metadata). Defaults to enabled count.
     * @return A JSON string.
     */
    fun buildJsonPrompt(
        data: TData,
        enabledSections: Set<TSection>,
        totalSectionCount: Int = enabledSections.size
    ): String {
        // Render header
        val headerSb = StringBuilder()
        appendHeader(headerSb, data)
        val header = headerSb.toString().trimEnd()

        // Render footer
        val footerSb = StringBuilder()
        appendFooter(footerSb, data)
        val footer = footerSb.toString().trimEnd()

        // Render each enabled section independently
        val sections = mutableMapOf<String, String>()
        for ((section, renderer) in sectionRenderers()) {
            if (section in enabledSections) {
                val sectionSb = StringBuilder()
                renderer(sectionSb, data)
                val content = sectionSb.toString().trimEnd()
                if (content.isNotEmpty()) {
                    sections[section.name] = content
                }
            }
        }

        // Calculate metadata from full text
        val fullText = buildPrompt(data, enabledSections)
        val sizeBytes = fullText.toByteArray(Charsets.UTF_8).size
        val estimatedTokens = TokenEstimator.estimate(fullText)

        // Build JSON by hand (zero dependencies)
        val sb = StringBuilder()
        sb.appendLine("{")

        if (formatVersion > 0) {
            sb.appendLine("  \"formatVersion\": $formatVersion,")
        }

        sb.appendLine("  \"header\": ${jsonEscape(header)},")
        sb.appendLine("  \"sections\": {")

        val entries = sections.entries.toList()
        for ((i, entry) in entries.withIndex()) {
            val comma = if (i < entries.size - 1) "," else ""
            sb.appendLine("    ${jsonEscape(entry.key)}: ${jsonEscape(entry.value)}$comma")
        }

        sb.appendLine("  },")
        sb.appendLine("  \"footer\": ${jsonEscape(footer)},")
        sb.appendLine("  \"metadata\": {")
        sb.appendLine("    \"enabledSections\": ${enabledSections.size},")
        sb.appendLine("    \"totalSections\": $totalSectionCount,")
        sb.appendLine("    \"estimatedTokens\": $estimatedTokens,")
        sb.appendLine("    \"sizeBytes\": $sizeBytes")
        sb.appendLine("  }")
        sb.append("}")

        return sb.toString()
    }

    private fun jsonEscape(value: String): String {
        if (value.isEmpty()) return "\"\""
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }
}
