package com.garrettmcbride.contextexport

/**
 * A snapshot of section content hashes for diff-based exports.
 *
 * Created by [AppPromptBuilder.snapshot] and passed to
 * [AppPromptBuilder.buildPromptDiff] to detect which sections
 * changed since the last export.
 *
 * Store this in your ViewModel after each export:
 * ```kotlin
 * var lastSnapshot: PromptSnapshot? = null
 *
 * fun export() {
 *     val diff = builder.buildPromptDiff(data, allSections, lastSnapshot)
 *     lastSnapshot = builder.snapshot(data, allSections)
 *     // diff.changedSections tells you what's new
 * }
 * ```
 *
 * @property sectionHashes Map of section name to content hash.
 */
data class PromptSnapshot(
    val sectionHashes: Map<String, String>
)
