package com.garrettmcbride.contextexport

/**
 * Result of a diff-based prompt build via [AppPromptBuilder.buildPromptDiff].
 *
 * Contains only the sections that changed since the previous [PromptSnapshot],
 * plus metadata about what changed vs. stayed the same.
 *
 * @param TSection Your section enum type.
 * @property promptResult The prompt containing only changed sections.
 * @property changedSections Sections whose content differs from the snapshot.
 * @property unchangedSections Sections whose content is identical to the snapshot.
 * @property isFullExport `true` if no previous snapshot was provided (all sections included).
 */
data class DiffResult<TSection : Enum<TSection>>(
    val promptResult: PromptResult,
    val changedSections: Set<TSection>,
    val unchangedSections: Set<TSection>,
    val isFullExport: Boolean
)
