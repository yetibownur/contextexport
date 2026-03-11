package com.garrettmcbride.contextexport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenEstimatorTest {

    @Test
    fun `empty string returns 0 tokens`() {
        assertEquals(0, TokenEstimator.estimate(""))
    }

    @Test
    fun `short text returns at least 1 token`() {
        assertEquals(1, TokenEstimator.estimate("Hi"))
    }

    @Test
    fun `4 characters returns 1 token at default ratio`() {
        assertEquals(1, TokenEstimator.estimate("abcd"))
    }

    @Test
    fun `400 characters returns ~100 tokens`() {
        val text = "a".repeat(400)
        assertEquals(100, TokenEstimator.estimate(text))
    }

    @Test
    fun `custom ratio changes estimate`() {
        val text = "a".repeat(300)
        // At 3 chars/token: 300/3 = 100
        assertEquals(100, TokenEstimator.estimate(text, charsPerToken = 3.0))
        // At 4 chars/token: 300/4 = 75
        assertEquals(75, TokenEstimator.estimate(text, charsPerToken = 4.0))
    }

    @Test
    fun `realistic prompt gives reasonable estimate`() {
        val prompt = """
            # My App Export

            ## Profile
            - Name: Alice
            - Age: 30

            ## History
            - Ran 5 miles on Monday
            - Lifted 200 lbs on Tuesday
            - Swam 1000m on Wednesday
        """.trimIndent()

        val tokens = TokenEstimator.estimate(prompt)
        // ~200 chars / 4 = ~50 tokens
        assertTrue(tokens in 30..80, "Expected reasonable token estimate, got $tokens")
    }

    @Test
    fun `default chars per token is 4`() {
        assertEquals(4.0, TokenEstimator.DEFAULT_CHARS_PER_TOKEN)
    }
}
