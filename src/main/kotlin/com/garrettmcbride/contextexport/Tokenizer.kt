package com.garrettmcbride.contextexport

/**
 * Pluggable token counting interface.
 *
 * Override [AppPromptBuilder.tokenizer] to swap in a real tokenizer
 * (e.g., tiktoken) instead of the default ~4 chars/token heuristic.
 *
 * Usage:
 * ```kotlin
 * class MyBuilder : AppPromptBuilder<MyData, MySection>() {
 *     override val tokenizer = Tokenizer { text ->
 *         myTiktokenInstance.encode(text).size
 *     }
 * }
 * ```
 */
fun interface Tokenizer {
    fun countTokens(text: String): Int

    companion object {
        /** Default tokenizer using [TokenEstimator]'s ~4 chars/token heuristic. */
        val DEFAULT = Tokenizer { TokenEstimator.estimate(it) }
    }
}
