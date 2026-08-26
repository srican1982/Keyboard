package com.personal.sinhalakeyboard

enum class KeyboardTheme(val id: String) {
    WHITE("white"),
    BLACK("black"),
    ;

    companion object {
        fun fromId(id: String?): KeyboardTheme =
            entries.find { it.id == id } ?: WHITE
    }
}
