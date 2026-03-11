package com.garrettmcbride.contextexport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextWindowTest {

    @Test
    fun `effectiveInput reserves 15 percent by default`() {
        // 200_000 * 0.85 = 170_000
        assertEquals(170_000, ContextWindow.effectiveInput(ContextWindow.CLAUDE_3_5))
    }

    @Test
    fun `effectiveInput with custom reserve ratio`() {
        // 100_000 * 0.80 = 80_000
        assertEquals(80_000, ContextWindow.effectiveInput(100_000, 0.20))
    }

    @Test
    fun `effectiveInput with zero reserve returns full context`() {
        assertEquals(128_000, ContextWindow.effectiveInput(ContextWindow.GPT_4O, 0.0))
    }

    @Test
    fun `fitsInContext returns true when under budget`() {
        assertTrue(ContextWindow.fitsInContext(1_000, ContextWindow.CLAUDE_3_5))
    }

    @Test
    fun `fitsInContext returns false when over budget`() {
        // Effective input for Claude 3.5 = 170_000
        assertFalse(ContextWindow.fitsInContext(180_000, ContextWindow.CLAUDE_3_5))
    }

    @Test
    fun `fitsInContext returns true at exact boundary`() {
        val effective = ContextWindow.effectiveInput(ContextWindow.GPT_4O)
        assertTrue(ContextWindow.fitsInContext(effective, ContextWindow.GPT_4O))
    }

    @Test
    fun `usagePercent returns correct percentage`() {
        val effective = ContextWindow.effectiveInput(100_000, 0.0)
        // 50_000 / 100_000 = 50%
        assertEquals(50.0, ContextWindow.usagePercent(50_000, 100_000, 0.0), 0.01)
    }

    @Test
    fun `usagePercent can exceed 100`() {
        val pct = ContextWindow.usagePercent(200_000, 100_000, 0.0)
        assertTrue(pct > 100.0)
    }

    @Test
    fun `remainingTokens is positive when under budget`() {
        val remaining = ContextWindow.remainingTokens(1_000, ContextWindow.CLAUDE_3_5)
        assertTrue(remaining > 0)
        assertEquals(170_000 - 1_000, remaining)
    }

    @Test
    fun `remainingTokens is negative when over budget`() {
        val remaining = ContextWindow.remainingTokens(180_000, ContextWindow.CLAUDE_3_5)
        assertTrue(remaining < 0)
    }

    @Test
    fun `model presets have expected values`() {
        assertEquals(128_000, ContextWindow.GPT_4_TURBO)
        assertEquals(128_000, ContextWindow.GPT_4O)
        assertEquals(128_000, ContextWindow.GPT_4O_MINI)
        assertEquals(200_000, ContextWindow.CLAUDE_3)
        assertEquals(200_000, ContextWindow.CLAUDE_3_5)
        assertEquals(200_000, ContextWindow.CLAUDE_4)
        assertEquals(1_000_000, ContextWindow.GEMINI_1_5_PRO)
        assertEquals(1_000_000, ContextWindow.GEMINI_1_5_FLASH)
        assertEquals(1_000_000, ContextWindow.GEMINI_2_FLASH)
    }
}
