package com.personal.sinhalakeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterHelperTest {

    @Test
    fun extractAssistantText_prefersContent() {
        val body =
            """{"choices":[{"message":{"role":"assistant","content":"Hello world","reasoning":"think"}}]}"""
        assertEquals("Hello world", OpenRouterHelper.extractAssistantText(body))
    }

    @Test
    fun extractAssistantText_fallsBackToReasoning() {
        val body =
            """{"choices":[{"message":{"role":"assistant","content":"","reasoning":"[\"ma\"]"}}]}"""
        assertEquals("""["ma"]""", OpenRouterHelper.extractAssistantText(body))
    }

    @Test
    fun userMessageForFailure_detectsCredits() {
        val msg = OpenRouterHelper.userMessageForFailure("OpenRouter error 402: Insufficient credits")
        assertTrue(msg.contains("credits"))
    }
}
