package com.garrettmcbride.contextexport

/**
 * How frequently a section's content changes between exports.
 *
 * Used by [AppPromptBuilder.sectionVolatility] to control section ordering
 * when [AppPromptBuilder.cacheOptimized] is enabled. Stable sections render
 * first so AI API prompt caching can reuse the prefix across calls.
 *
 * - [STABLE] — Rarely changes (world lore, character backstory, app settings).
 *   Rendered first to maximize cache hits.
 * - [MODERATE] — Changes occasionally (inventory, quest log, workout history).
 * - [VOLATILE] — Changes every export (session notes, recent activity, live stats).
 *   Rendered last so the cached prefix stays valid.
 */
enum class Volatility {
    STABLE,
    MODERATE,
    VOLATILE
}
