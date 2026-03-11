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
     * Override to automatically skip sections whose [sectionItemCount] returns 0.
     *
     * When `true`, the build loop checks `sectionItemCount(section, data) == 0`
     * before calling a section's renderer, eliminating the need for manual
     * `if (data.xxx.isEmpty()) return` guards in every renderer.
     *
     * Requires that [sectionItemCount] is properly overridden with accurate counts
     * for all sections. Default is `false` (all enabled sections are always rendered).
     */
    protected open val autoSkipEmpty: Boolean = false

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
            if (section !in enabledSections) continue
            if (autoSkipEmpty && sectionItemCount(section, data) == 0) continue
            renderer(sb, data)
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
        val totalChars = fullText.length.coerceAtLeast(1)

        // Per-section breakdown
        val breakdown = mutableMapOf<String, SectionStats>()
        for ((section, renderer) in sectionRenderers()) {
            if (section !in enabledSections) continue
            if (autoSkipEmpty && sectionItemCount(section, data) == 0) continue
            val sectionSb = StringBuilder()
            renderer(sectionSb, data)
            val content = sectionSb.toString()
            if (content.isNotEmpty()) {
                val chars = content.length
                breakdown[section.name] = SectionStats(
                    chars = chars,
                    tokens = TokenEstimator.estimate(content),
                    percentage = (chars.toDouble() / totalChars) * 100.0
                )
            }
        }

        return PromptResult(
            fullText = fullText,
            chunks = chunks,
            sizeBytes = sizeBytes,
            estimatedTokens = TokenEstimator.estimate(fullText),
            enabledSectionCount = enabledSections.size,
            chunkSize = chunkSize,
            sectionBreakdown = breakdown
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
     * Override to assign priority to sections for budget-based truncation.
     *
     * Higher values = higher priority (kept first when trimming via
     * [buildPromptWithBudget]). Default returns the section's position
     * in [sectionRenderers] inverted, so sections listed first have the
     * highest priority.
     *
     * @param section The section to prioritize.
     * @return Priority value. Higher = more important.
     */
    protected open fun sectionPriority(section: TSection): Int {
        val renderers = sectionRenderers()
        val index = renderers.indexOfFirst { it.first == section }
        return if (index >= 0) renderers.size - index else 0
    }

    /**
     * Builds a prompt that fits within a token budget by selectively
     * dropping low-priority sections.
     *
     * Sections are rendered individually, then sorted by [sectionPriority].
     * Starting from the lowest-priority section, sections are dropped until
     * the estimated token count fits within [maxTokens].
     *
     * If the prompt still exceeds the budget after dropping all sections
     * (header + footer alone are too large), [BudgetResult.withinBudget]
     * will be `false`.
     *
     * @param data Your app's data bag.
     * @param enabledSections Which sections to consider.
     * @param maxTokens Maximum token budget for the final prompt.
     * @param chunkSize Chunk size passed through to [buildPromptResult].
     * @return A [BudgetResult] with the trimmed prompt and drop information.
     */
    fun buildPromptWithBudget(
        data: TData,
        enabledSections: Set<TSection>,
        maxTokens: Int,
        chunkSize: Int = PromptChunker.DEFAULT_CHUNK_SIZE
    ): BudgetResult<TSection> {
        // First try with all enabled sections
        val fullResult = buildPromptResult(data, enabledSections, chunkSize)
        if (fullResult.estimatedTokens <= maxTokens) {
            return BudgetResult(
                promptResult = fullResult,
                droppedSections = emptySet(),
                includedSections = enabledSections,
                budgetTokens = maxTokens,
                withinBudget = true
            )
        }

        // Measure each section individually
        data class SectionMeasure(val section: TSection, val tokens: Int, val priority: Int)

        val measures = mutableListOf<SectionMeasure>()
        for ((section, renderer) in sectionRenderers()) {
            if (section !in enabledSections) continue
            if (autoSkipEmpty && sectionItemCount(section, data) == 0) continue
            val sb = StringBuilder()
            renderer(sb, data)
            measures.add(SectionMeasure(section, TokenEstimator.estimate(sb.toString()), sectionPriority(section)))
        }

        // Sort by priority ascending (lowest priority = drop first)
        val sortedByPriority = measures.sortedBy { it.priority }

        val dropped = mutableSetOf<TSection>()
        var currentTokens = fullResult.estimatedTokens

        for (measure in sortedByPriority) {
            if (currentTokens <= maxTokens) break
            dropped.add(measure.section)
            currentTokens -= measure.tokens
        }

        // Rebuild with remaining sections
        val remaining = enabledSections - dropped
        val trimmedResult = buildPromptResult(data, remaining, chunkSize)

        return BudgetResult(
            promptResult = trimmedResult,
            droppedSections = dropped,
            includedSections = remaining,
            budgetTokens = maxTokens,
            withinBudget = trimmedResult.estimatedTokens <= maxTokens
        )
    }

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
            if (section !in enabledSections) continue
            if (autoSkipEmpty && sectionItemCount(section, data) == 0) continue
            val sectionSb = StringBuilder()
            renderer(sectionSb, data)
            val content = sectionSb.toString().trimEnd()
            if (content.isNotEmpty()) {
                sections[section.name] = content
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
