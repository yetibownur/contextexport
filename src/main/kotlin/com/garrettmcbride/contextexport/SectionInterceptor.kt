package com.garrettmcbride.contextexport

/**
 * Post-processing hook applied to each section's rendered output.
 *
 * Interceptors run after a section's renderer produces content,
 * allowing cross-cutting transformations without modifying individual renderers.
 *
 * Use cases:
 * - Auto-compress verbose sections
 * - Redact spoilers for player-facing exports
 * - Add "last updated" timestamps
 * - Inject formatting or markers
 *
 * Usage:
 * ```kotlin
 * class MyBuilder : AppPromptBuilder<MyData, MySection>() {
 *     override fun sectionInterceptors() = listOf(
 *         SectionInterceptor { name, content ->
 *             if (name == "LORE") PromptCompressor.compress(content) else content
 *         }
 *     )
 * }
 * ```
 */
fun interface SectionInterceptor {
    /**
     * Transform a section's rendered content.
     *
     * @param sectionName The section enum's `.name` string.
     * @param content The rendered text from the section's renderer.
     * @return The transformed content. Return [content] unchanged to skip.
     */
    fun intercept(sectionName: String, content: String): String
}
