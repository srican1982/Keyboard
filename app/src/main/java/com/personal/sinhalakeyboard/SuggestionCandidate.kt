package com.personal.sinhalakeyboard

data class SuggestionCandidate(
    val display: String,
    val commitText: String,
    val isRoman: Boolean = false,
    val isNextWord: Boolean = false,
    val isCloud: Boolean = false,
    val isPersonal: Boolean = false,
)
