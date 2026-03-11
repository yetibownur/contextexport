package com.garrettmcbride.contextexport

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class InterceptorTest {

    enum class Section { ALPHA, BETA }

    data class Data(val text: String = "test")

    @Test
    fun `no interceptors leaves content unchanged`() {
        val builder = object : AppPromptBuilder<Data, Section>() {
            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                Section.ALPHA to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Alpha")
                    sb.appendLine("Original content.")
                    sb.appendLine()
                }
            )
        }

        val prompt = builder.buildPrompt(Data(), setOf(Section.ALPHA))
        assertContains(prompt, "Original content.")
    }

    @Test
    fun `interceptor transforms section content`() {
        val builder = object : AppPromptBuilder<Data, Section>() {
            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                Section.ALPHA to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Alpha")
                    sb.appendLine("Hello World")
                    sb.appendLine()
                }
            )

            override fun sectionInterceptors() = listOf(
                SectionInterceptor { _, content ->
                    content.replace("Hello", "Goodbye")
                }
            )
        }

        val prompt = builder.buildPrompt(Data(), setOf(Section.ALPHA))
        assertContains(prompt, "Goodbye World")
        assertTrue("Hello World" !in prompt)
    }

    @Test
    fun `interceptor receives correct section name`() {
        var capturedName = ""
        val builder = object : AppPromptBuilder<Data, Section>() {
            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                Section.BETA to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Beta")
                    sb.appendLine()
                }
            )

            override fun sectionInterceptors() = listOf(
                SectionInterceptor { name, content ->
                    capturedName = name
                    content
                }
            )
        }

        builder.buildPrompt(Data(), setOf(Section.BETA))
        assertTrue(capturedName == "BETA")
    }

    @Test
    fun `interceptor only modifies specific section`() {
        val builder = object : AppPromptBuilder<Data, Section>() {
            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                Section.ALPHA to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Alpha")
                    sb.appendLine("Alpha text")
                    sb.appendLine()
                },
                Section.BETA to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Beta")
                    sb.appendLine("Beta text")
                    sb.appendLine()
                }
            )

            override fun sectionInterceptors() = listOf(
                SectionInterceptor { name, content ->
                    if (name == "ALPHA") content.uppercase() else content
                }
            )
        }

        val prompt = builder.buildPrompt(Data(), Section.entries.toSet())
        assertContains(prompt, "ALPHA TEXT")  // uppercased
        assertContains(prompt, "Beta text")   // unchanged
    }

    @Test
    fun `multiple interceptors chain in order`() {
        val builder = object : AppPromptBuilder<Data, Section>() {
            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                Section.ALPHA to { sb: StringBuilder, _: Data ->
                    sb.appendLine("hello")
                }
            )

            override fun sectionInterceptors() = listOf(
                SectionInterceptor { _, content -> content.replace("hello", "world") },
                SectionInterceptor { _, content -> content.replace("world", "DONE") }
            )
        }

        val prompt = builder.buildPrompt(Data(), setOf(Section.ALPHA))
        assertContains(prompt, "DONE")
    }

    @Test
    fun `interceptors affect buildPromptResult breakdown`() {
        val builder = object : AppPromptBuilder<Data, Section>() {
            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                Section.ALPHA to { sb: StringBuilder, _: Data ->
                    sb.appendLine("short")
                }
            )

            override fun sectionInterceptors() = listOf(
                SectionInterceptor { _, _ -> "a".repeat(1000) }
            )
        }

        val result = builder.buildPromptResult(Data(), setOf(Section.ALPHA))
        val stats = result.sectionBreakdown["ALPHA"]
        assertTrue(stats != null)
        assertTrue(stats.chars == 1000)
    }

    @Test
    fun `interceptors affect JSON output`() {
        val builder = object : AppPromptBuilder<Data, Section>() {
            override fun sectionRenderers(): List<Pair<Section, (StringBuilder, Data) -> Unit>> = listOf(
                Section.ALPHA to { sb: StringBuilder, _: Data ->
                    sb.appendLine("## Alpha")
                    sb.appendLine("original")
                    sb.appendLine()
                }
            )

            override fun sectionInterceptors() = listOf(
                SectionInterceptor { _, content -> content.replace("original", "intercepted") }
            )
        }

        val json = builder.buildJsonPrompt(Data(), setOf(Section.ALPHA))
        assertContains(json, "intercepted")
        assertTrue("original" !in json)
    }
}
