package com.garrettmcbride.contextexport

/**
 * A named configuration of enabled sections for quick export profiles.
 *
 * Instead of toggling sections individually, users pick a preset from a dropdown.
 * Define presets in your [AppPromptBuilder] subclass via [AppPromptBuilder.exportPresets].
 *
 * Usage:
 * ```kotlin
 * class MyBuilder : AppPromptBuilder<MyData, MySection>() {
 *     override fun exportPresets() = listOf(
 *         ExportPreset("Full Export", MySection.entries.toSet()),
 *         ExportPreset("Quick Summary", setOf(MySection.PROFILE, MySection.STATS)),
 *         ExportPreset("Combat Ready", setOf(MySection.CHARACTERS, MySection.COMBAT),
 *             targetModel = ContextWindow.CLAUDE_3_5)
 *     )
 * }
 * ```
 *
 * @param TSection Your section enum type.
 * @property name Display name for the preset (shown in UI dropdowns).
 * @property sections Which sections to enable when this preset is selected.
 * @property targetModel Optional [ContextWindow] constant. When set,
 *   [AppPromptBuilder.buildFromPreset] uses [AppPromptBuilder.buildPromptWithBudget]
 *   to auto-fit the prompt to this model's context window.
 */
data class ExportPreset<TSection : Enum<TSection>>(
    val name: String,
    val sections: Set<TSection>,
    val targetModel: Int? = null
)
