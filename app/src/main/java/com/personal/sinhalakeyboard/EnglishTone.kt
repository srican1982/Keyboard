package com.personal.sinhalakeyboard

enum class EnglishTone(val id: String, val label: String) {
    PROFESSIONAL("professional", "Pro"),
    FRIENDLY("friendly", "Friendly"),
    ;

    fun aiDescription(): String = when (this) {
        PROFESSIONAL ->
            "formal, professional, polite business English"
        FRIENDLY ->
            "casual, warm, friendly conversational English"
    }

    companion object {
        fun fromId(id: String?): EnglishTone =
            entries.find { it.id == id } ?: PROFESSIONAL
    }
}
