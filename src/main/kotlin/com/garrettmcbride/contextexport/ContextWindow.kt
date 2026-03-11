package com.garrettmcbride.contextexport

/**
 * Context window sizes for popular AI models and utilities for
 * checking whether a prompt fits.
 *
 * Token counts here are the model's *total* context window (input + output).
 * Since prompts are input-only, the effective input budget is typically
 * 80–90% of these values (models reserve tokens for output). Use
 * [effectiveInput] to apply a reserve ratio.
 *
 * Token estimates in this SDK use a ~4 chars/token heuristic,
 * so all calculations here are approximate.
 *
 * Usage:
 * ```kotlin
 * val fits = ContextWindow.fitsInContext(result.estimatedTokens, ContextWindow.CLAUDE_3_5)
 * val usage = ContextWindow.usagePercent(result.estimatedTokens, ContextWindow.CLAUDE_3_5)
 * // "Uses 12.3% of Claude 3.5's context"
 * ```
 */
object ContextWindow {

    // ── Model presets ──

    /** GPT-4 Turbo — 128K total context. */
    const val GPT_4_TURBO: Int = 128_000

    /** GPT-4o — 128K total context. */
    const val GPT_4O: Int = 128_000

    /** GPT-4o mini — 128K total context. */
    const val GPT_4O_MINI: Int = 128_000

    /** Claude 3 Opus / Sonnet / Haiku — 200K total context. */
    const val CLAUDE_3: Int = 200_000

    /** Claude 3.5 Sonnet / Haiku — 200K total context. */
    const val CLAUDE_3_5: Int = 200_000

    /** Claude 4 Opus / Sonnet — 200K total context. */
    const val CLAUDE_4: Int = 200_000

    /** Gemini 1.5 Pro — 1M total context. */
    const val GEMINI_1_5_PRO: Int = 1_000_000

    /** Gemini 1.5 Flash — 1M total context. */
    const val GEMINI_1_5_FLASH: Int = 1_000_000

    /** Gemini 2.0 Flash — 1M total context. */
    const val GEMINI_2_FLASH: Int = 1_000_000

    /** Default output reserve ratio (keep 15% for model output). */
    const val DEFAULT_RESERVE_RATIO: Double = 0.15

    /**
     * Returns the effective input token budget after reserving space for output.
     *
     * @param contextSize Total context window in tokens.
     * @param reserveRatio Fraction to reserve for output (default: [DEFAULT_RESERVE_RATIO]).
     * @return Usable input tokens.
     */
    fun effectiveInput(
        contextSize: Int,
        reserveRatio: Double = DEFAULT_RESERVE_RATIO
    ): Int = ((1.0 - reserveRatio) * contextSize).toInt()

    /**
     * Whether [tokens] fits within the model's effective input budget.
     *
     * @param tokens Estimated token count of the prompt.
     * @param contextSize Model context window (e.g., [CLAUDE_3_5]).
     * @param reserveRatio Fraction reserved for output.
     */
    fun fitsInContext(
        tokens: Int,
        contextSize: Int,
        reserveRatio: Double = DEFAULT_RESERVE_RATIO
    ): Boolean = tokens <= effectiveInput(contextSize, reserveRatio)

    /**
     * What percentage of the effective input budget [tokens] uses.
     *
     * @return Usage as 0.0–100.0+. Values above 100 mean the prompt exceeds the budget.
     */
    fun usagePercent(
        tokens: Int,
        contextSize: Int,
        reserveRatio: Double = DEFAULT_RESERVE_RATIO
    ): Double {
        val budget = effectiveInput(contextSize, reserveRatio).coerceAtLeast(1)
        return (tokens.toDouble() / budget) * 100.0
    }

    /**
     * How many tokens remain in the effective input budget.
     *
     * @return Remaining tokens. Negative if the prompt exceeds the budget.
     */
    fun remainingTokens(
        tokens: Int,
        contextSize: Int,
        reserveRatio: Double = DEFAULT_RESERVE_RATIO
    ): Int = effectiveInput(contextSize, reserveRatio) - tokens
}
