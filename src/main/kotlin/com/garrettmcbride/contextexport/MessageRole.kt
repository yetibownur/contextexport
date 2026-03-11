package com.garrettmcbride.contextexport

/**
 * Role tag for structured prompt messages.
 *
 * Used by [AppPromptBuilder.sectionRole] to assign sections to
 * system or user message roles. [AppPromptBuilder.buildStructuredPrompt]
 * groups sections by role and returns a list of [PromptMessage] objects
 * that map directly to AI API message arrays.
 *
 * - [SYSTEM] — Instructions, context, and reference data the AI should internalize.
 * - [USER] — The user's actual request or the data they want analyzed.
 */
enum class MessageRole {
    SYSTEM,
    USER
}
