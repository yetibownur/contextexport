package com.garrettmcbride.contextexport

/**
 * Compresses prompt text to save tokens without losing readability.
 *
 * Useful as a post-processing step before chunking or displaying,
 * especially for large datasets where whitespace adds up.
 *
 * Usage:
 * ```kotlin
 * val compressed = PromptCompressor.compress(rawPrompt)
 * // Saves ~5-15% of tokens on typical prompts
 * ```
 */
object PromptCompressor {

    /**
     * Compresses the prompt by removing redundant whitespace.
     *
     * Operations:
     * - Collapses 3+ consecutive blank lines into 2 (keeps section separation)
     * - Trims trailing whitespace from each line
     * - Removes trailing blank lines at end of prompt
     *
     * Does NOT alter:
     * - Content within lines
     * - Single blank lines between sections
     * - Indentation (leading spaces are preserved)
     * - Markdown formatting
     *
     * @param prompt The prompt text to compress.
     * @return Compressed prompt text.
     */
    fun compress(prompt: String): String {
        if (prompt.isEmpty()) return prompt

        val lines = prompt.lines()
        val result = mutableListOf<String>()
        var consecutiveBlanks = 0

        for (line in lines) {
            val trimmed = line.trimEnd()

            if (trimmed.isEmpty()) {
                consecutiveBlanks++
                // Allow at most 2 consecutive blank lines
                if (consecutiveBlanks <= 2) {
                    result.add("")
                }
            } else {
                consecutiveBlanks = 0
                result.add(trimmed)
            }
        }

        // Remove trailing blank lines
        while (result.isNotEmpty() && result.last().isEmpty()) {
            result.removeAt(result.lastIndex)
        }

        return result.joinToString("\n")
    }

    /**
     * Returns how many characters (and estimated tokens) compression would save.
     *
     * @param prompt The prompt text to analyze.
     * @return Pair of (characters saved, estimated tokens saved).
     */
    fun savings(prompt: String): Pair<Int, Int> {
        val compressed = compress(prompt)
        val charsSaved = prompt.length - compressed.length
        val tokensSaved = TokenEstimator.estimate(charsSaved.toString()) // rough
        return charsSaved to (charsSaved / TokenEstimator.DEFAULT_CHARS_PER_TOKEN).toInt()
    }
}
