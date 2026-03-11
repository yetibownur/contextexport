package com.garrettmcbride.contextexport

/**
 * Size statistics for a single rendered section.
 *
 * Returned as part of [PromptResult.sectionBreakdown], keyed by the
 * section enum's `.name` string.
 *
 * @property chars Character count of the rendered section text.
 * @property tokens Estimated token count (via [TokenEstimator]).
 * @property percentage This section's share of the total prompt size (0.0–100.0).
 */
data class SectionStats(
    val chars: Int,
    val tokens: Int,
    val percentage: Double
)
