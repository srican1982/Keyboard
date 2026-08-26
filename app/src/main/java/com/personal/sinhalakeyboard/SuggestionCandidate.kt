package com.personal.sinhalakeyboard

data class SuggestionCandidate(
    val display: String,
    val commitText: String,
    val isRoman: Boolean = false,
    val isNextWord: Boolean = false,
    val isCloud: Boolean = false,
    val isPersonal: Boolean = false,
    /** Keep the Singlish letters as typed (not converted to Sinhala script). */
    val isSinglishRoman: Boolean = false,
    /** Toolbar-style action chip (+ Add word, etc.). */
    val isAction: Boolean = false,
    /** Opens add-word mode: type a word, save it for future suggestions. */
    val isAddWord: Boolean = false,
)
