package com.personal.sinhalakeyboard

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

/**
 * Local SQLite history: words the user commits, per keyboard mode.
 * Score = personal_count * [PERSONAL_WEIGHT] + corpus_frequency.
 */
class PersonalHistoryDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION,
) {

    private val appContext = context.applicationContext

    init {
        writableDatabase.close()
        migrateFromLegacyTsvOnce()
    }

    fun increment(word: String, langMode: String) {
        val cleaned = word.trim()
        if (cleaned.isEmpty() || langMode.isBlank()) return
        writableDatabase.use { db ->
            db.execSQL(
                """
                INSERT INTO personal_history (word, lang_mode, count)
                VALUES (?, ?, 1)
                ON CONFLICT(word, lang_mode) DO UPDATE SET count = count + 1
                """.trimIndent(),
                arrayOf(cleaned, langMode),
            )
        }
    }

    fun getCount(word: String, langMode: String): Int {
        val cleaned = word.trim()
        if (cleaned.isEmpty()) return 0
        return readableDatabase.use { db ->
            db.rawQuery(
                "SELECT count FROM personal_history WHERE word = ? AND lang_mode = ? LIMIT 1",
                arrayOf(cleaned, langMode),
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        }
    }

    fun getCounts(words: Collection<String>, langMode: String): Map<String, Int> {
        if (words.isEmpty()) return emptyMap()
        val unique = words.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (unique.isEmpty()) return emptyMap()

        val out = HashMap<String, Int>(unique.size)
        readableDatabase.use { db ->
            for (chunk in unique.chunked(400)) {
                val placeholders = chunk.joinToString(",") { "?" }
                val args = arrayOf(langMode, *chunk.toTypedArray())
                db.rawQuery(
                    """
                    SELECT word, count FROM personal_history
                    WHERE lang_mode = ? AND word IN ($placeholders)
                    """.trimIndent(),
                    args,
                ).use { cursor ->
                    val wordIdx = cursor.getColumnIndex("word")
                    val countIdx = cursor.getColumnIndex("count")
                    while (cursor.moveToNext()) {
                        out[cursor.getString(wordIdx)] = cursor.getInt(countIdx)
                    }
                }
            }
        }
        return out
    }

    /** Prefix match for English personal suggestions (case-insensitive). */
    fun queryEnglishPrefix(prefix: String, limit: Int = 12): List<HistoryEntry> {
        val key = prefix.trim().lowercase()
        if (key.isEmpty()) return emptyList()
        return readableDatabase.use { db ->
            db.rawQuery(
                """
                SELECT word, count FROM personal_history
                WHERE lang_mode = ? AND LOWER(word) LIKE ? ESCAPE '\'
                ORDER BY count DESC, LENGTH(word), word
                LIMIT ?
                """.trimIndent(),
                arrayOf(MODE_ENGLISH, escapeLike(key) + "%", limit.toString()),
            ).use { cursor -> readHistoryEntries(cursor) }
        }
    }

    /** Exact and prefix hits for Sinhala script words the user typed before. */
    fun querySinhalaWords(candidates: Collection<String>, limit: Int = 12): List<HistoryEntry> {
        if (candidates.isEmpty()) return emptyList()
        val words = candidates.filter { containsSinhalaScript(it) }.distinct()
        if (words.isEmpty()) return emptyList()

        val counts = getCounts(words, MODE_SINHALA)
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { HistoryEntry(it.key, it.value) }
    }

    fun score(personalCount: Int, corpusFrequency: Int): Int =
        personalCount * PERSONAL_WEIGHT + corpusFrequency

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE personal_history (
                word TEXT NOT NULL,
                lang_mode TEXT NOT NULL,
                count INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY (word, lang_mode)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX idx_personal_history_mode_prefix ON personal_history (lang_mode, word)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < DB_VERSION) {
            db.execSQL("DROP TABLE IF EXISTS personal_history")
            onCreate(db)
        }
    }

    private fun migrateFromLegacyTsvOnce() {
        val flag = File(appContext.filesDir, LEGACY_MIGRATION_FLAG)
        if (flag.exists()) return
        val legacy = File(appContext.filesDir, "typing_memory.tsv")
        if (!legacy.exists()) {
            flag.writeText("done")
            return
        }
        try {
            legacy.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split('\t')
                    when (parts.getOrNull(0)) {
                        "S" -> if (parts.size >= 4) {
                            val sinhala = parts[2].trim()
                            val count = parts[3].toIntOrNull() ?: 1
                            if (containsSinhalaScript(sinhala)) {
                                seedCount(sinhala, MODE_SINHALA, count)
                            }
                        }
                        "E" -> if (parts.size >= 3) {
                            val word = parts[1].trim()
                            val count = parts[2].toIntOrNull() ?: 1
                            if (word.isNotEmpty()) seedCount(word, MODE_ENGLISH, count)
                        }
                    }
                }
            }
            flag.writeText("done")
        } catch (_: Exception) {
            // retry on next launch
        }
    }

    private fun seedCount(word: String, langMode: String, count: Int) {
        if (count <= 0) return
        writableDatabase.use { db ->
            db.execSQL(
                """
                INSERT INTO personal_history (word, lang_mode, count)
                VALUES (?, ?, ?)
                ON CONFLICT(word, lang_mode) DO UPDATE SET
                    count = MAX(personal_history.count, excluded.count)
                """.trimIndent(),
                arrayOf(word, langMode, count.toString()),
            )
        }
    }

    private fun readHistoryEntries(cursor: android.database.Cursor): List<HistoryEntry> {
        val out = ArrayList<HistoryEntry>()
        val wordIdx = cursor.getColumnIndex("word")
        val countIdx = cursor.getColumnIndex("count")
        while (cursor.moveToNext()) {
            out.add(HistoryEntry(cursor.getString(wordIdx), cursor.getInt(countIdx)))
        }
        return out
    }

    private fun escapeLike(prefix: String): String =
        prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun containsSinhalaScript(text: String): Boolean =
        text.any { it.code in 0x0D80..0x0DFF }

    data class HistoryEntry(val word: String, val count: Int)

    companion object {
        const val MODE_SINHALA = "sinhala"
        const val MODE_ENGLISH = "english"
        const val PERSONAL_WEIGHT = 10_000

        private const val DB_NAME = "personal_history.db"
        private const val DB_VERSION = 1
        private const val LEGACY_MIGRATION_FLAG = ".personal_history_migrated"
    }
}
