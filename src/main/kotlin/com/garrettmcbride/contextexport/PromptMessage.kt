package com.garrettmcbride.contextexport

/**
 * A single message in a structured prompt, tagged with a [MessageRole].
 *
 * Returned by [AppPromptBuilder.buildStructuredPrompt]. Maps directly
 * to AI API message formats:
 *
 * ```json
 * {"role": "system", "content": "..."}
 * {"role": "user", "content": "..."}
 * ```
 *
 * @property role Whether this is a [MessageRole.SYSTEM] or [MessageRole.USER] message.
 * @property content The rendered text content for this message.
 */
data class PromptMessage(
    val role: MessageRole,
    val content: String
)
