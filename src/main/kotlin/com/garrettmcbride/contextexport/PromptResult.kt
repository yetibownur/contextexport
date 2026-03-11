package com.garrettmcbride.contextexport

/**
 * The result of building a prompt via [AppPromptBuilder.buildPromptResult].
 *
 * Contains the full prompt text, pre-chunked parts for multi-message AI conversations,
 * and metadata about the prompt's size.
 *
 * @property fullText The complete prompt as a single string.
 * @property chunks The prompt split into sendable parts at `##` section boundaries.
 *   If the prompt fits within [chunkSize], this is a single-element list equal to [fullText].
 *   Multi-element lists include continuation labels (e.g., "[Part 1 of 3]").
 * @property sizeBytes Byte size of [fullText] (UTF-8).
 * @property estimatedTokens Rough token estimate (~4 characters per token).
 * @property enabledSectionCount How many sections were enabled when this prompt was built.
 * @property chunkSize The character limit used for chunking.
 */
data class PromptResult(
    val fullText: String,
    val chunks: List<String>,
    val sizeBytes: Int,
    val estimatedTokens: Int,
    val enabledSectionCount: Int,
    val chunkSize: Int
) {
    /** Number of chunks the prompt was split into. */
    val chunkCount: Int get() = chunks.size

    /** Whether the prompt required splitting into multiple parts. */
    val isMultiPart: Boolean get() = chunks.size > 1

    /** Approximate size in kilobytes. */
    val sizeKb: Double get() = sizeBytes / 1024.0
}
