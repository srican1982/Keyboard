package com.personal.sinhalakeyboard

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Learns words and next-word pairs from what the user types (like Gboard history).
 * Stored locally in app private storage — never sent anywhere.
 */
class TypingMemory(context: Context) {

    private val appContext = context.applicationContext
    private val storeFile = File(appContext.filesDir, "typing_memory.tsv")

    private val sinhalaByRoman = ConcurrentHashMap<String, Entry>()
    private val englishWords = ConcurrentHashMap<String, Entry>()
    private val sinhalaBigrams = ConcurrentHashMap<String, ConcurrentHashMap<String, Entry>>()
    private val englishBigrams = ConcurrentHashMap<String, ConcurrentHashMap<String, Entry>>()

    init {
        load()
    }

    data class Entry(val value: String, var count: Int)

    fun rememberSinhala(roman: String, sinhala: String) {
        val key = roman.trim().lowercase()
        val value = sinhala.trim()
        if (key.length < 2 || value.isEmpty()) return
        if (!SinhalaSuggestionRules.isReasonableRomanKey(key)) return
        if (!SinhalaSuggestionRules.isReasonableSinhalaSuggestion(value, key.length)) return
        bump(sinhalaByRoman, key, value)
        saveAsync()
    }

    fun rememberEnglish(word: String) {
        val key = word.trim().lowercase()
        if (key.length < 2 || !key.any { it.isLetter() }) return
        bump(englishWords, key, key)
        saveAsync()
    }

    fun rememberBigram(previous: String, next: String, sinhala: Boolean) {
        val prevKey = normalizeBigramKey(previous, sinhala)
        val nextVal = next.trim()
        if (prevKey.isEmpty() || nextVal.isEmpty()) return
        val map = if (sinhala) sinhalaBigrams else englishBigrams
        val bucket = map.getOrPut(prevKey) { ConcurrentHashMap() }
        bump(bucket, nextVal, nextVal)
        saveAsync()
    }

    fun sinhalaSuggestions(prefix: String, limit: Int = 4): List<SuggestionCandidate> {
        val key = prefix.trim().lowercase()
        if (key.isEmpty()) return emptyList()
        return sinhalaByRoman.entries
            .filter { it.key.startsWith(key) }
            .sortedWith(compareByDescending<Map.Entry<String, Entry>> { it.value.count }.thenBy { it.key })
            .take(limit)
            .map { (_, entry) ->
                SuggestionCandidate(
                    display = entry.value,
                    commitText = entry.value,
                    isPersonal = true,
                )
            }
    }

    fun englishSuggestions(prefix: String, limit: Int = 4): List<SuggestionCandidate> {
        val key = prefix.trim().lowercase()
        if (key.isEmpty()) return emptyList()
        return englishWords.entries
            .filter { it.key.startsWith(key) && it.key != key }
            .sortedWith(compareByDescending<Map.Entry<String, Entry>> { it.value.count }.thenBy { it.key })
            .take(limit)
            .map { (wordKey, _) ->
                val display = formatEnglish(wordKey, prefix)
                SuggestionCandidate(display, display, isPersonal = true)
            }
    }

    fun nextWordSuggestions(lastWord: String, sinhala: Boolean, limit: Int = 6): List<SuggestionCandidate> {
        val key = normalizeBigramKey(lastWord, sinhala)
        if (key.isEmpty()) return emptyList()
        val map = if (sinhala) sinhalaBigrams else englishBigrams
        return map[key].orEmpty().entries
            .sortedWith(compareByDescending<Map.Entry<String, Entry>> { it.value.count }.thenBy { it.key })
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

    private fun bump(map: ConcurrentHashMap<String, Entry>, key: String, value: String) {
        val existing = map[key]
        if (existing == null) {
            map[key] = Entry(value, 1)
        } else {
            existing.count += 1
            if (existing.value != value) {
                map[key] = Entry(value, existing.count)
            }
        }
        pruneIfNeeded(map, MAX_WORD_ENTRIES)
    }

    private fun normalizeBigramKey(word: String, sinhala: Boolean): String {
        val trimmed = word.trim().trimEnd { !it.isLetter() && it != '\'' }
        return if (sinhala) trimmed else trimmed.lowercase()
    }

    private fun formatEnglish(stored: String, typedPrefix: String): String {
        return stored.replaceFirstChar { ch ->
            if (typedPrefix.firstOrNull()?.isUpperCase() == true) ch.uppercaseChar() else ch
        }
    }

    private fun pruneIfNeeded(map: ConcurrentHashMap<String, Entry>, max: Int) {
        if (map.size <= max) return
        val drop = map.entries
            .sortedBy { it.value.count }
            .take(map.size - max)
        drop.forEach { map.remove(it.key) }
    }

    private var savePending = false
    private val saveRunnable = Runnable {
        savePending = false
        saveNow()
    }

    private fun saveAsync() {
        if (savePending) return
        savePending = true
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(saveRunnable, 800)
    }

    @Synchronized
    private fun saveNow() {
        try {
            storeFile.bufferedWriter().use { out ->
                sinhalaByRoman.forEach { (roman, entry) ->
                    out.write("S\t$roman\t${entry.value}\t${entry.count}\n")
                }
                englishWords.forEach { (word, entry) ->
                    out.write("E\t$word\t${entry.count}\n")
                }
                sinhalaBigrams.forEach { (prev, nextMap) ->
                    nextMap.forEach { (_, entry) ->
                        out.write("BS\t$prev\t${entry.value}\t${entry.count}\n")
                    }
                }
                englishBigrams.forEach { (prev, nextMap) ->
                    nextMap.forEach { (_, entry) ->
                        out.write("BE\t$prev\t${entry.value}\t${entry.count}\n")
                    }
                }
            }
        } catch (_: Exception) {
            // best-effort persistence
        }
    }

    private fun load() {
        if (!storeFile.exists()) return
        try {
            storeFile.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split('\t')
                    when (parts.getOrNull(0)) {
                        "S" -> if (parts.size >= 4) {
                            val roman = parts[1]
                            val sinhala = parts[2]
                            val count = parts[3].toIntOrNull() ?: 1
                            if (SinhalaSuggestionRules.isReasonableRomanKey(roman) &&
                                SinhalaSuggestionRules.isReasonableSinhalaSuggestion(sinhala, roman.length)
                            ) {
                                sinhalaByRoman[roman] = Entry(sinhala, count)
                            }
                        }
                        "E" -> if (parts.size >= 3) {
                            englishWords[parts[1]] = Entry(parts[1], parts[2].toIntOrNull() ?: 1)
                        }
                        "BS" -> if (parts.size >= 4) {
                            val bucket = sinhalaBigrams.getOrPut(parts[1]) { ConcurrentHashMap() }
                            bucket[parts[2]] = Entry(parts[2], parts[3].toIntOrNull() ?: 1)
                        }
                        "BE" -> if (parts.size >= 4) {
                            val bucket = englishBigrams.getOrPut(parts[1]) { ConcurrentHashMap() }
                            bucket[parts[2]] = Entry(parts[2], parts[3].toIntOrNull() ?: 1)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // start fresh if corrupt
        }
    }

    companion object {
        private const val MAX_WORD_ENTRIES = 2500
    }
}
