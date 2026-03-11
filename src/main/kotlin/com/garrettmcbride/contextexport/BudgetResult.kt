package com.garrettmcbride.contextexport

/**
 * Result of building a prompt with a token budget via
 * [AppPromptBuilder.buildPromptWithBudget].
 *
 * Contains the final prompt (after dropping sections to fit the budget)
 * and information about which sections were kept vs. dropped.
 *
 * @param TSection The section enum type.
 * @property promptResult The final prompt result (after dropping sections).
 * @property droppedSections Sections removed to fit the budget.
 * @property includedSections Sections that made it into the prompt.
 * @property budgetTokens The token budget that was targeted.
 * @property withinBudget Whether the final prompt fits within the budget.
 */
data class BudgetResult<TSection : Enum<TSection>>(
    val promptResult: PromptResult,
    val droppedSections: Set<TSection>,
    val includedSections: Set<TSection>,
    val budgetTokens: Int,
    val withinBudget: Boolean
)
