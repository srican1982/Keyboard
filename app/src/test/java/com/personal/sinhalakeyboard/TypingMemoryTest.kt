package com.personal.sinhalakeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TypingMemoryTest {

    @Test
    fun saveCustomWord_appearsInSuggestions() {
        val memory = TypingMemory(RuntimeEnvironment.getApplication())
        memory.saveCustomWord("mama", "මම")
        val items = memory.sinhalaSuggestions("mama")
        assertTrue(items.any { it.commitText == "මම" })
    }

    @Test
    fun saveCustomWord_romanOnly() {
        val memory = TypingMemory(RuntimeEnvironment.getApplication())
        memory.saveCustomWord("koo", "koo")
        val items = memory.sinhalaSuggestions("koo")
        assertEquals(1, items.size)
        assertEquals("koo", items.first().commitText)
        assertTrue(items.first().isSinglishRoman)
    }
}
