package com.garrettmcbride.contextexport

/**
 * Utility for estimating token counts from text.
 *
 * Uses the widely-accepted heuristic of ~4 characters per token for English text.
 * This is not a precise tokenizer — it's a fast approximation suitable for
 * showing users "~2.5K tokens" in a UI or deciding whether to chunk a prompt.
 *
 * For reference:
 * - OpenAI's GPT models average ~4 chars/token for English
 * - Anthropic's Claude averages ~3.5–4 chars/token for English
 * - Code and structured data (JSON, markdown tables) tend toward ~3 chars/token
 *
 * Usage:
 * ```kotlin
 * val tokens = TokenEstimator.estimate("Hello, world!")  // ~3
 * val tokens = TokenEstimator.estimate(longPrompt)       // ~4200
 * ```
 */
object TokenEstimator {

    /** Default characters-per-token ratio. */
    const val DEFAULT_CHARS_PER_TOKEN = 4.0

    /**
     * Estimates the token count for [text].
     *
     * @param text The input text.
     * @param charsPerToken Characters-per-token ratio (default: [DEFAULT_CHARS_PER_TOKEN]).
     * @return Estimated token count, minimum 0.
     */
    fun estimate(text: String, charsPerToken: Double = DEFAULT_CHARS_PER_TOKEN): Int {
        if (text.isEmpty()) return 0
        return (text.length / charsPerToken).toInt().coerceAtLeast(1)
    }
}
