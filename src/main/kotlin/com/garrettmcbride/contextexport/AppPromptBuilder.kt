package com.garrettmcbride.contextexport

import java.security.MessageDigest

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

    // ── Overridable properties ──────────────────────────────────────

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
     * Override to enable cache-optimized section ordering.
     *
     * When `true`, sections are sorted by [sectionVolatility] before rendering:
     * [Volatility.STABLE] first, then [Volatility.MODERATE], then [Volatility.VOLATILE].
     * This maximizes AI API prompt caching by keeping the stable prefix unchanged
     * across exports.
     *
     * Default is `false` (sections render in [sectionRenderers] order).
     */
    protected open val cacheOptimized: Boolean = false

    /**
     * Override to plug in a custom token counter.
     *
     * By default uses [TokenEstimator]'s ~4 chars/token heuristic.
     * Swap in a real tokenizer (e.g., tiktoken) for exact counts:
     *
     * ```kotlin
     * override val tokenizer = Tokenizer { text ->
     *     myTiktokenInstance.encode(text).size
     * }
     * ```
     */
    protected open val tokenizer: Tokenizer = Tokenizer.DEFAULT

    // ── Core build methods ──────────────────────────────────────────

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

        for ((section, renderer) in orderedRenderers(enabledSections, data)) {
            val sectionSb = StringBuilder()
            renderer(sectionSb, data)
            val content = applyInterceptors(section, sectionSb.toString())
            sb.append(content)
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
        for ((section, renderer) in orderedRenderers(enabledSections, data)) {
            val sectionSb = StringBuilder()
            renderer(sectionSb, data)
            val content = applyInterceptors(section, sectionSb.toString())
            if (content.isNotEmpty()) {
                val chars = content.length
                breakdown[section.name] = SectionStats(
                    chars = chars,
                    tokens = tokenizer.countTokens(content),
                    percentage = (chars.toDouble() / totalChars) * 100.0
                )
            }
        }

        return PromptResult(
            fullText = fullText,
            chunks = chunks,
            sizeBytes = sizeBytes,
            estimatedTokens = tokenizer.countTokens(fullText),
            enabledSectionCount = enabledSections.size,
            chunkSize = chunkSize,
            sectionBreakdown = breakdown
        )
    }

    // ── Section item counts ─────────────────────────────────────────

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

    // ── Budget-based truncation ─────────────────────────────────────

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
        for ((section, renderer) in orderedRenderers(enabledSections, data)) {
            val sb = StringBuilder()
            renderer(sb, data)
            val content = applyInterceptors(section, sb.toString())
            measures.add(SectionMeasure(section, tokenizer.countTokens(content), sectionPriority(section)))
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

    // ── Diff/delta exports ──────────────────────────────────────────

    /**
     * Creates a [PromptSnapshot] of the current section content hashes.
     *
     * Store this after each export and pass it to [buildPromptDiff] on the
     * next export to detect which sections changed.
     *
     * @param data Your app's data bag.
     * @param enabledSections Which sections to hash.
     * @return A snapshot containing SHA-256 hashes for each section.
     */
    fun snapshot(data: TData, enabledSections: Set<TSection>): PromptSnapshot {
        val hashes = mutableMapOf<String, String>()
        for ((section, renderer) in orderedRenderers(enabledSections, data)) {
            val sb = StringBuilder()
            renderer(sb, data)
            val content = applyInterceptors(section, sb.toString())
            hashes[section.name] = sha256(content)
        }
        return PromptSnapshot(sectionHashes = hashes)
    }

    /**
     * Builds a prompt containing only sections that changed since [previous].
     *
     * If [previous] is `null`, all sections are included (full export).
     * Otherwise, each section's content is hashed and compared to the snapshot —
     * unchanged sections are excluded from the output.
     *
     * @param data Your app's data bag.
     * @param enabledSections Which sections to consider.
     * @param previous The snapshot from the last export, or `null` for full export.
     * @param chunkSize Chunk size passed through to [buildPromptResult].
     * @return A [DiffResult] with the changed-only prompt and change metadata.
     */
    fun buildPromptDiff(
        data: TData,
        enabledSections: Set<TSection>,
        previous: PromptSnapshot?,
        chunkSize: Int = PromptChunker.DEFAULT_CHUNK_SIZE
    ): DiffResult<TSection> {
        if (previous == null) {
            return DiffResult(
                promptResult = buildPromptResult(data, enabledSections, chunkSize),
                changedSections = enabledSections,
                unchangedSections = emptySet(),
                isFullExport = true
            )
        }

        val changed = mutableSetOf<TSection>()
        val unchanged = mutableSetOf<TSection>()

        for ((section, renderer) in orderedRenderers(enabledSections, data)) {
            val sb = StringBuilder()
            renderer(sb, data)
            val content = applyInterceptors(section, sb.toString())
            val hash = sha256(content)
            val previousHash = previous.sectionHashes[section.name]

            if (previousHash == null || hash != previousHash) {
                changed.add(section)
            } else {
                unchanged.add(section)
            }
        }

        val diffResult = buildPromptResult(data, changed, chunkSize)

        return DiffResult(
            promptResult = diffResult,
            changedSections = changed,
            unchangedSections = unchanged,
            isFullExport = false
        )
    }

    // ── Export presets ───────────────────────────────────────────────

    /**
     * Returns the preset with the given [name], or `null` if not found.
     */
    fun getPreset(name: String): ExportPreset<TSection>? {
        return exportPresets().find { it.name == name }
    }

    /**
     * Builds a prompt using a named preset.
     *
     * If the preset has a [ExportPreset.targetModel], uses [buildPromptWithBudget]
     * to auto-fit. Otherwise, uses [buildPromptResult].
     *
     * @param data Your app's data bag.
     * @param presetName Name of the preset (from [exportPresets]).
     * @param chunkSize Chunk size passed through.
     * @return A [PromptResult], or `null` if the preset name is not found.
     */
    fun buildFromPreset(
        data: TData,
        presetName: String,
        chunkSize: Int = PromptChunker.DEFAULT_CHUNK_SIZE
    ): PromptResult? {
        val preset = getPreset(presetName) ?: return null
        return if (preset.targetModel != null) {
            val budget = ContextWindow.effectiveInput(preset.targetModel)
            buildPromptWithBudget(data, preset.sections, budget, chunkSize).promptResult
        } else {
            buildPromptResult(data, preset.sections, chunkSize)
        }
    }

    // ── Section groups ──────────────────────────────────────────────

    /**
     * Returns the set of sections belonging to the given group names.
     *
     * Use this to convert group selections to section sets for [buildPrompt]:
     * ```kotlin
     * val sections = builder.sectionsFromGroups("World", "Characters")
     * val result = builder.buildPromptResult(data, sections)
     * ```
     *
     * @param groupNames Group names defined in [sectionGroups].
     * @return Union of all sections in the named groups.
     */
    fun sectionsFromGroups(vararg groupNames: String): Set<TSection> {
        val groups = sectionGroups()
        val result = mutableSetOf<TSection>()
        for (name in groupNames) {
            groups[name]?.let { result.addAll(it) }
        }
        return result
    }

    /**
     * Returns all available group names defined in [sectionGroups].
     */
    fun availableGroups(): Set<String> = sectionGroups().keys

    // ── Structured message output ───────────────────────────────────

    /**
     * Builds a list of [PromptMessage] objects grouped by [MessageRole].
     *
     * Header is always [MessageRole.SYSTEM]. Footer is always [MessageRole.USER].
     * Sections are tagged by [sectionRole]. Adjacent sections with the same role
     * are merged into a single message.
     *
     * Maps directly to AI API message arrays:
     * ```json
     * [
     *   {"role": "system", "content": "header + system sections"},
     *   {"role": "user", "content": "user sections + footer"}
     * ]
     * ```
     *
     * @param data Your app's data bag.
     * @param enabledSections Which sections to include.
     * @return Ordered list of prompt messages.
     */
    fun buildStructuredPrompt(
        data: TData,
        enabledSections: Set<TSection>
    ): List<PromptMessage> {
        val messages = mutableListOf<PromptMessage>()

        // Header is always SYSTEM
        val headerSb = StringBuilder()
        if (formatVersion > 0) {
            headerSb.appendLine("<!-- format_version: $formatVersion -->")
        }
        appendHeader(headerSb, data)
        val headerText = headerSb.toString().trimEnd()

        // Collect section content grouped by role
        data class RoleBlock(val role: MessageRole, val content: StringBuilder = StringBuilder())

        val blocks = mutableListOf<RoleBlock>()
        if (headerText.isNotEmpty()) {
            blocks.add(RoleBlock(MessageRole.SYSTEM, StringBuilder(headerText)))
        }

        for ((section, renderer) in orderedRenderers(enabledSections, data)) {
            val sectionSb = StringBuilder()
            renderer(sectionSb, data)
            val content = applyInterceptors(section, sectionSb.toString())
            if (content.isEmpty()) continue

            val role = sectionRole(section)
            val lastBlock = blocks.lastOrNull()
            if (lastBlock != null && lastBlock.role == role) {
                lastBlock.content.append(content)
            } else {
                blocks.add(RoleBlock(role, StringBuilder(content)))
            }
        }

        // Footer is always USER
        val footerSb = StringBuilder()
        appendFooter(footerSb, data)
        val footerText = footerSb.toString().trimEnd()
        if (footerText.isNotEmpty()) {
            val lastBlock = blocks.lastOrNull()
            if (lastBlock != null && lastBlock.role == MessageRole.USER) {
                lastBlock.content.append("\n").append(footerText)
            } else {
                blocks.add(RoleBlock(MessageRole.USER, StringBuilder(footerText)))
            }
        }

        for (block in blocks) {
            val text = block.content.toString().trimEnd()
            if (text.isNotEmpty()) {
                messages.add(PromptMessage(role = block.role, content = text))
            }
        }

        return messages
    }

    // ── JSON output ─────────────────────────────────────────────────

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
        for ((section, renderer) in orderedRenderers(enabledSections, data)) {
            val sectionSb = StringBuilder()
            renderer(sectionSb, data)
            val content = applyInterceptors(section, sectionSb.toString()).trimEnd()
            if (content.isNotEmpty()) {
                sections[section.name] = content
            }
        }

        // Calculate metadata from full text
        val fullText = buildPrompt(data, enabledSections)
        val sizeBytes = fullText.toByteArray(Charsets.UTF_8).size
        val estimatedTokens = tokenizer.countTokens(fullText)

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

    // ── Overridable hooks ───────────────────────────────────────────

    /**
     * Override to return the number of items in a section.
     *
     * Used by [countSectionItems] to build section count maps for UI display
     * (e.g., showing "Hirelings (5)" on filter chips).
     *
     * Default returns 0 for all sections.
     */
    protected open fun sectionItemCount(section: TSection, data: TData): Int = 0

    /**
     * Override to write the prompt header — title, description, and instructions
     * that tell the AI model how to interpret the data that follows.
     *
     * Called once at the start of [buildPrompt], before any section renderers.
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
     */
    protected open fun appendFooter(sb: StringBuilder, data: TData) {}

    /**
     * Override to assign priority to sections for budget-based truncation.
     *
     * Higher values = higher priority (kept first when trimming via
     * [buildPromptWithBudget]). Default returns the section's position
     * in [sectionRenderers] inverted, so sections listed first have the
     * highest priority.
     */
    protected open fun sectionPriority(section: TSection): Int {
        val renderers = sectionRenderers()
        val index = renderers.indexOfFirst { it.first == section }
        return if (index >= 0) renderers.size - index else 0
    }

    /**
     * Override to assign volatility to sections for cache-optimized ordering.
     *
     * Only used when [cacheOptimized] is `true`. Sections with [Volatility.STABLE]
     * render first, [Volatility.VOLATILE] render last.
     *
     * Default is [Volatility.MODERATE] for all sections.
     */
    protected open fun sectionVolatility(section: TSection): Volatility = Volatility.MODERATE

    /**
     * Override to define named presets for quick export profiles.
     *
     * ```kotlin
     * override fun exportPresets() = listOf(
     *     ExportPreset("Full Export", MySection.entries.toSet()),
     *     ExportPreset("Quick Summary", setOf(MySection.PROFILE, MySection.STATS))
     * )
     * ```
     */
    protected open fun exportPresets(): List<ExportPreset<TSection>> = emptyList()

    /**
     * Override to define section groups for batch enable/disable.
     *
     * ```kotlin
     * override fun sectionGroups() = mapOf(
     *     "World" to setOf(MySection.LORE, MySection.LOCATIONS, MySection.FACTIONS),
     *     "Characters" to setOf(MySection.PCS, MySection.NPCS)
     * )
     * ```
     */
    protected open fun sectionGroups(): Map<String, Set<TSection>> = emptyMap()

    /**
     * Override to assign a [MessageRole] to each section.
     *
     * Used by [buildStructuredPrompt] to group sections into system vs. user messages.
     * Default is [MessageRole.USER] for all sections.
     */
    protected open fun sectionRole(section: TSection): MessageRole = MessageRole.USER

    /**
     * Override to add post-processing interceptors applied to each section's output.
     *
     * Interceptors run in order after a renderer produces content.
     * Default is empty (no interceptors).
     */
    protected open fun sectionInterceptors(): List<SectionInterceptor> = emptyList()

    // ── Internal helpers ────────────────────────────────────────────

    /**
     * Returns the filtered, optionally re-ordered list of (section, renderer) pairs.
     * Applies enabledSections filter, autoSkipEmpty, and cache-optimized sorting.
     */
    private fun orderedRenderers(
        enabledSections: Set<TSection>,
        data: TData
    ): List<Pair<TSection, (StringBuilder, TData) -> Unit>> {
        var renderers = sectionRenderers().filter { (section, _) ->
            section in enabledSections &&
                !(autoSkipEmpty && sectionItemCount(section, data) == 0)
        }

        if (cacheOptimized) {
            renderers = renderers.sortedBy { (section, _) -> sectionVolatility(section).ordinal }
        }

        return renderers
    }

    /**
     * Applies all [sectionInterceptors] to the given content.
     */
    private fun applyInterceptors(section: TSection, content: String): String {
        var result = content
        for (interceptor in sectionInterceptors()) {
            result = interceptor.intercept(section.name, result)
        }
        return result
    }

    /**
     * SHA-256 hash of a string, returned as hex.
     */
    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
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
