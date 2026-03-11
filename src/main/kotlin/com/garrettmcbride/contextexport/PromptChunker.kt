package com.garrettmcbride.contextexport

/**
 * Splits large prompts into sendable chunks for AI conversations.
 *
 * Chunks are split at markdown `##` section boundaries so that each part
 * contains complete sections. If a single section exceeds the chunk size,
 * it is force-split at newline boundaries as a fallback.
 *
 * Multi-part results include continuation labels so the AI model knows
 * to expect more data.
 *
 * Usage:
 * ```kotlin
 * // Default labels
 * val chunks = PromptChunker.chunk(longPrompt)
 *
 * // Custom app-branded labels
 * val chunks = PromptChunker.chunk(longPrompt, appName = "PocketPump fitness")
 * // → "[Part 1 of 3] — Continuation of PocketPump fitness data export."
 * ```
 */
object PromptChunker {

    /** Default chunk size in characters (~4K tokens). */
    const val DEFAULT_CHUNK_SIZE = 15_000

    /**
     * Splits [prompt] into chunks of at most [chunkSize] characters.
     *
     * @param prompt The full prompt text to split.
     * @param chunkSize Maximum characters per chunk (default: [DEFAULT_CHUNK_SIZE]).
     * @param appName Optional app name for branded continuation labels.
     *   Default "data" produces "Continuation of data export."
     *   Passing "PocketPump fitness" produces "Continuation of PocketPump fitness data export."
     * @return A list of prompt chunks. Single-element if the prompt fits within [chunkSize].
     */
    fun chunk(
        prompt: String,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        appName: String = ""
    ): List<String> {
        if (prompt.length <= chunkSize) return listOf(prompt)

        // Split on markdown section headings (## lines)
        val sectionPattern = Regex("(?=^## )", RegexOption.MULTILINE)
        val sections = sectionPattern.split(prompt)

        val chunks = mutableListOf<String>()
        val currentChunk = StringBuilder()

        for (section in sections) {
            if (section.isBlank()) continue

            // If adding this section would exceed the limit, start a new chunk
            if (currentChunk.isNotEmpty() && currentChunk.length + section.length > chunkSize) {
                chunks.add(currentChunk.toString().trimEnd())
                currentChunk.clear()
            }
            currentChunk.append(section)
        }

        // Add the last chunk
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trimEnd())
        }

        // If we only got 1 chunk (single massive section), force-split by character
        if (chunks.size == 1 && chunks[0].length > chunkSize) {
            val bigChunk = chunks.removeAt(0)
            var i = 0
            while (i < bigChunk.length) {
                val end = (i + chunkSize).coerceAtMost(bigChunk.length)
                // Try to split at a newline boundary
                val splitAt = if (end < bigChunk.length) {
                    val lastNewline = bigChunk.lastIndexOf('\n', end)
                    if (lastNewline > i) lastNewline + 1 else end
                } else end
                chunks.add(bigChunk.substring(i, splitAt).trimEnd())
                i = splitAt
            }
        }

        // Build label prefix from app name
        val exportLabel = if (appName.isNotBlank()) "$appName data export" else "data export"

        // Add continuation labels if multiple chunks
        return if (chunks.size <= 1) chunks
        else chunks.mapIndexed { index, chunk ->
            val label = "[Part ${index + 1} of ${chunks.size}]"
            when {
                index == 0 ->
                    "$chunk\n\n$label \u2014 Send the next part to continue."
                index < chunks.size - 1 ->
                    "$label \u2014 Continuation of $exportLabel.\n\n$chunk\n\n$label \u2014 Send the next part to continue."
                else ->
                    "$label \u2014 Final part of $exportLabel.\n\n$chunk\n\n[End of export \u2014 All ${chunks.size} parts received. You can now analyze the complete dataset.]"
            }
        }
    }
}
