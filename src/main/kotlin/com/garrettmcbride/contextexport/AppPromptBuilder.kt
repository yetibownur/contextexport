package com.garrettmcbride.contextexport

/**
 * Generic base class for building AI export prompts.
 *
 * Subclass this with your app's data bag and section enum,
 * then override [appendHeader] and [sectionRenderers].
 *
 * Each app provides its own data model and section definitions.
 * The base class handles the build loop — iterate enabled sections,
 * call each renderer, and return the assembled prompt string.
 */
open class AppPromptBuilder<TData, TSection : Enum<TSection>> {

    open fun buildPrompt(
        data: TData,
        enabledSections: Set<TSection>
    ): String {
        val sb = StringBuilder()
        appendHeader(sb, data)

        for ((section, renderer) in sectionRenderers()) {
            if (section in enabledSections) renderer(sb, data)
        }

        return sb.toString().trimEnd()
    }

    protected open fun appendHeader(sb: StringBuilder, data: TData) {}

    protected open fun sectionRenderers(): List<Pair<TSection, (StringBuilder, TData) -> Unit>> = emptyList()
}
