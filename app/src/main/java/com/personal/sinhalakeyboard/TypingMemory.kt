package com.personal.sinhalakeyboard

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Learns words and next-word pairs from what the user types.
 *
 * Stored locally in app-private storage.
 *
 * Responsibilities:
 *
 * 1. Learn Roman -> Sinhala mappings.
 * 2. Learn English words.
 * 3. Learn Sinhala and English bigrams.
 *
 * Important:
 * Exact Roman -> Sinhala memory is treated as the strongest personal signal.
 */
class TypingMemory(context: Context) {

    private val appContext = context.applicationContext
    private val storeFile = File(appContext.filesDir, "typing_memory.tsv")

    private val sinhalaByRoman = ConcurrentHashMap<String, Entry>()
    private val englishWords = ConcurrentHashMap<String, Entry>()

    private val sinhalaBigrams =
        ConcurrentHashMap<String, ConcurrentHashMap<String, Entry>>()

    private val englishBigrams =
        ConcurrentHashMap<String, ConcurrentHashMap<String, Entry>>()

    init {
        load()
    }

    data class Entry(
        val value: String,
        var count: Int,
    )

    /**
     * Exact learned Roman mapping.
     *
     * Example:
     *
     * patiyo -> පටියෝ
     *
     * Returns null when the user has never committed this exact Roman key.
     */
    fun exactSinhalaMapping(
        roman: String,
    ): SuggestionCandidate? {

        val key = roman.trim().lowercase()

        if (key.isEmpty()) {
            return null
        }

        val entry =
            sinhalaByRoman[key]
                ?: return null

        val value =
            entry.value.trim()

        if (!containsSinhalaScript(value)) {
            return null
        }

        return SuggestionCandidate(
            display = value,
            commitText = value,
            isPersonal = true,
        )
    }

    /**
     * Exact learned Roman mapping with usage count.
     *
     * Useful for ranking.
     */
    fun exactSinhalaEntry(
        roman: String,
    ): Entry? {

        val key =
            roman.trim().lowercase()

        if (key.isEmpty()) {
            return null
        }

        val entry =
            sinhalaByRoman[key]
                ?: return null

        if (!containsSinhalaScript(entry.value)) {
            return null
        }

        return Entry(
            value = entry.value,
            count = entry.count,
        )
    }

    /**
     * Remember the Sinhala word selected for a Roman spelling.
     */
    fun rememberSinhala(
        roman: String,
        sinhala: String,
    ) {

        val key =
            roman.trim().lowercase()

        val value =
            sinhala.trim()

        if (key.length < 2) {
            return
        }

        if (value.isEmpty()) {
            return
        }

        if (
            !SinhalaSuggestionRules
                .isReasonableRomanKey(key)
        ) {
            return
        }

        if (
            !SinhalaSuggestionRules
                .isReasonableSinhalaSuggestion(
                    sinhala = value,
                    typedRomanLength = key.length,
                )
        ) {
            return
        }

        bump(
            map = sinhalaByRoman,
            key = key,
            value = value,
        )

        saveAsync()
    }

    /**
     * Remember an English word.
     */
    fun rememberEnglish(
        word: String,
    ) {

        val key =
            word.trim().lowercase()

        if (
            key.length < 2 ||
            !key.any { it.isLetter() }
        ) {
            return
        }

        bump(
            map = englishWords,
            key = key,
            value = key,
        )

        saveAsync()
    }

    /**
     * Remember a next-word relationship.
     */
    fun rememberBigram(
        previous: String,
        next: String,
        sinhala: Boolean,
    ) {

        val prevKey =
            normalizeBigramKey(
                word = previous,
                sinhala = sinhala,
            )

        val nextVal =
            next.trim()

        if (
            prevKey.isEmpty() ||
            nextVal.isEmpty()
        ) {
            return
        }

        val map =
            if (sinhala) {
                sinhalaBigrams
            } else {
                englishBigrams
            }

        val bucket =
            map.getOrPut(prevKey) {
                ConcurrentHashMap()
            }

        bump(
            map = bucket,
            key = nextVal,
            value = nextVal,
        )

        saveAsync()
    }

    /**
     * Remember the Roman spelling itself.
     *
     * Kept for compatibility with existing behavior.
     */
    fun rememberSinglishRoman(
        roman: String,
    ) {

        val key =
            roman.trim().lowercase()

        if (key.length < 2) {
            return
        }

        if (
            !SinhalaSuggestionRules
                .isReasonableRomanKey(key)
        ) {
            return
        }

        bump(
            map = sinhalaByRoman,
            key = key,
            value = key,
        )

        saveAsync()
    }

    /**
     * Learned Sinhala suggestions for a Roman prefix.
     *
     * Ranking:
     *
     * 1. Exact Roman key
     * 2. Higher personal count
     * 3. Shorter Roman key
     * 4. Alphabetical key
     */
    fun sinhalaSuggestions(
        prefix: String,
        limit: Int = 4,
    ): List<SuggestionCandidate> {

        val key =
            prefix.trim().lowercase()

        if (key.isEmpty()) {
            return emptyList()
        }

        return sinhalaByRoman
            .entries
            .asSequence()
            .filter { (romanKey, entry) ->

                if (!romanKey.startsWith(key)) {
                    return@filter false
                }

                /*
                 * For Sinhala suggestion mode, only return actual
                 * Sinhala mappings.
                 *
                 * Roman-only memory is intentionally not mixed into
                 * Sinhala script suggestions.
                 */
                containsSinhalaScript(
                    entry.value
                )
            }
            .sortedWith(
                compareByDescending<Map.Entry<String, Entry>> {
                    if (it.key == key) {
                        1
                    } else {
                        0
                    }
                }
                    .thenByDescending {
                        it.value.count
                    }
                    .thenBy {
                        it.key.length
                    }
                    .thenBy {
                        it.key
                    },
            )
            .take(limit)
            .map { (_, entry) ->

                SuggestionCandidate(
                    display = entry.value,
                    commitText = entry.value,
                    isPersonal = true,
                )
            }
            .toList()
    }

    /**
     * English prefix suggestions.
     */
    fun englishSuggestions(
        prefix: String,
        limit: Int = 4,
    ): List<SuggestionCandidate> {

        val key =
            prefix.trim().lowercase()

        if (key.isEmpty()) {
            return emptyList()
        }

        return englishWords
            .entries
            .asSequence()
            .filter {
                it.key.startsWith(key) &&
                    it.key != key
            }
            .sortedWith(
                compareByDescending<Map.Entry<String, Entry>> {
                    it.value.count
                }
                    .thenBy {
                        it.key.length
                    }
                    .thenBy {
                        it.key
                    },
            )
            .take(limit)
            .map { (wordKey, _) ->

                val display =
                    formatEnglish(
                        stored = wordKey,
                        typedPrefix = prefix,
                    )

                SuggestionCandidate(
                    display = display,
                    commitText = display,
                    isPersonal = true,
                )
            }
            .toList()
    }

    /**
     * Next-word suggestions from learned bigrams.
     */
    fun nextWordSuggestions(
        lastWord: String,
        sinhala: Boolean,
        limit: Int = 6,
    ): List<SuggestionCandidate> {

        val key =
            normalizeBigramKey(
                word = lastWord,
                sinhala = sinhala,
            )

        if (key.isEmpty()) {
            return emptyList()
        }

        val map =
            if (sinhala) {
                sinhalaBigrams
            } else {
                englishBigrams
            }

        return map[key]
            .orEmpty()
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Entry>> {
                    it.value.count
                }
                    .thenBy {
                        it.key
                    },
            )
            .take(limit)
            .map { (_, entry) ->

                SuggestionCandidate(
                    display = entry.value,
                    commitText = entry.value,
                    isNextWord = true,
                    isPersonal = true,
                )
            }
    }

    /**
     * Increase a learned mapping count.
     *
     * If the same Roman key is later committed to a different Sinhala value,
     * keep the accumulated count while replacing the preferred value.
     */
    private fun bump(
        map: ConcurrentHashMap<String, Entry>,
        key: String,
        value: String,
    ) {

        val existing =
            map[key]

        if (existing == null) {

            map[key] =
                Entry(
                    value = value,
                    count = 1,
                )

        } else {

            val nextCount =
                existing.count + 1

            if (existing.value == value) {

                existing.count =
                    nextCount

            } else {

                /*
                 * The user's latest explicit choice becomes the preferred
                 * value for this exact Roman key.
                 */
                map[key] =
                    Entry(
                        value = value,
                        count = nextCount,
                    )
            }
        }

        pruneIfNeeded(
            map = map,
            max = MAX_WORD_ENTRIES,
        )
    }

    private fun normalizeBigramKey(
        word: String,
        sinhala: Boolean,
    ): String {

        val trimmed =
            word
                .trim()
                .trimEnd {
                    !it.isLetter() &&
                        it != '\''
                }

        return if (sinhala) {
            trimmed
        } else {
            trimmed.lowercase()
        }
    }

    private fun formatEnglish(
        stored: String,
        typedPrefix: String,
    ): String {

        return stored.replaceFirstChar { char ->

            if (
                typedPrefix
                    .firstOrNull()
                    ?.isUpperCase() == true
            ) {
                char.uppercaseChar()
            } else {
                char
            }
        }
    }

    private fun pruneIfNeeded(
        map: ConcurrentHashMap<String, Entry>,
        max: Int,
    ) {

        if (map.size <= max) {
            return
        }

        val drop =
            map.entries
                .sortedBy {
                    it.value.count
                }
                .take(
                    map.size - max
                )

        drop.forEach { entry ->
            map.remove(entry.key)
        }
    }

    private var savePending =
        false

    private val saveRunnable =
        Runnable {

            savePending =
                false

            saveNow()
        }

    private fun saveAsync() {

        if (savePending) {
            return
        }

        savePending =
            true

        android.os.Handler(
            android.os.Looper.getMainLooper()
        ).postDelayed(
            saveRunnable,
            800,
        )
    }

    @Synchronized
    private fun saveNow() {

        try {

            storeFile
                .bufferedWriter()
                .use { out ->

                    sinhalaByRoman
                        .forEach { (roman, entry) ->

                            out.write(
                                "S\t$roman\t${entry.value}\t${entry.count}\n"
                            )
                        }

                    englishWords
                        .forEach { (word, entry) ->

                            out.write(
                                "E\t$word\t${entry.count}\n"
                            )
                        }

                    sinhalaBigrams
                        .forEach { (prev, nextMap) ->

                            nextMap.forEach { (_, entry) ->

                                out.write(
                                    "BS\t$prev\t${entry.value}\t${entry.count}\n"
                                )
                            }
                        }

                    englishBigrams
                        .forEach { (prev, nextMap) ->

                            nextMap.forEach { (_, entry) ->

                                out.write(
                                    "BE\t$prev\t${entry.value}\t${entry.count}\n"
                                )
                            }
                        }
                }

        } catch (_: Exception) {

            /*
             * Best-effort persistence.
             */
        }
    }

    private fun load() {

        if (!storeFile.exists()) {
            return
        }

        try {

            storeFile
                .bufferedReader()
                .useLines { lines ->

                    lines.forEach { line ->

                        val parts =
                            line.split('\t')

                        when (
                            parts.getOrNull(0)
                        ) {

                            "S" -> {

                                if (parts.size < 4) {
                                    return@forEach
                                }

                                val roman =
                                    parts[1]
                                        .trim()
                                        .lowercase()

                                val storedValue =
                                    parts[2]
                                        .trim()

                                val count =
                                    parts[3]
                                        .toIntOrNull()
                                        ?.coerceAtLeast(1)
                                        ?: 1

                                if (
                                    !SinhalaSuggestionRules
                                        .isReasonableRomanKey(roman)
                                ) {
                                    return@forEach
                                }

                                /*
                                 * Preserve old Roman-only memory records for
                                 * compatibility, although they will no longer
                                 * be returned as Sinhala-script suggestions.
                                 */
                                val romanOnly =
                                    storedValue == roman

                                val validSinhala =
                                    containsSinhalaScript(
                                        storedValue
                                    ) &&
                                        SinhalaSuggestionRules
                                            .isReasonableSinhalaSuggestion(
                                                sinhala = storedValue,
                                                typedRomanLength = roman.length,
                                            )

                                if (
                                    romanOnly ||
                                    validSinhala
                                ) {
                                    sinhalaByRoman[roman] =
                                        Entry(
                                            value = storedValue,
                                            count = count,
                                        )
                                }
                            }

                            "E" -> {

                                if (parts.size < 3) {
                                    return@forEach
                                }

                                val word =
                                    parts[1]
                                        .trim()
                                        .lowercase()

                                if (word.isEmpty()) {
                                    return@forEach
                                }

                                val count =
                                    parts[2]
                                        .toIntOrNull()
                                        ?.coerceAtLeast(1)
                                        ?: 1

                                englishWords[word] =
                                    Entry(
                                        value = word,
                                        count = count,
                                    )
                            }

                            "BS" -> {

                                if (parts.size < 4) {
                                    return@forEach
                                }

                                val previous =
                                    parts[1]

                                val next =
                                    parts[2]

                                val count =
                                    parts[3]
                                        .toIntOrNull()
                                        ?.coerceAtLeast(1)
                                        ?: 1

                                val bucket =
                                    sinhalaBigrams
                                        .getOrPut(previous) {
                                            ConcurrentHashMap()
                                        }

                                bucket[next] =
                                    Entry(
                                        value = next,
                                        count = count,
                                    )
                            }

                            "BE" -> {

                                if (parts.size < 4) {
                                    return@forEach
                                }

                                val previous =
                                    parts[1]
                                        .lowercase()

                                val next =
                                    parts[2]
                                        .lowercase()

                                val count =
                                    parts[3]
                                        .toIntOrNull()
                                        ?.coerceAtLeast(1)
                                        ?: 1

                                val bucket =
                                    englishBigrams
                                        .getOrPut(previous) {
                                            ConcurrentHashMap()
                                        }

                                bucket[next] =
                                    Entry(
                                        value = next,
                                        count = count,
                                    )
                            }
                        }
                    }
                }

        } catch (_: Exception) {

            /*
             * Start with empty in-memory state if the stored file is corrupt.
             */
        }
    }

    private fun containsSinhalaScript(
        text: String,
    ): Boolean {

        return text.any { char ->
            char.code in 0x0D80..0x0DFF
        }
    }

    companion object {

        private const val MAX_WORD_ENTRIES =
            2500
    }
}
