package com.personal.sinhalakeyboard

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.FileOutputStream

/**
 * Frequency-ranked Sinhala words from the UCSC NLP verified list
 * (nlpcuom/Word-Frequency-List-for-Sinhala — ~280k words).
 *
 * Queries by Sinhala script prefix — pair with SinglishConverter output while typing.
 */
class SinhalaFrequencyDatabase(context: Context) {

    data class Entry(val word: String, val frequency: Int)

    private val db: SQLiteDatabase?

    init {
        db = openReadOnly(context)
    }

    fun queryByPrefix(prefix: String, limit: Int = 12): List<Entry> {
        if (prefix.isEmpty() || db == null) return emptyList()
        val escaped = prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        db.rawQuery(
            """
            SELECT word, freq FROM words
            WHERE word LIKE ? ESCAPE '\'
            ORDER BY freq DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf("$escaped%", limit.toString()),
        ).use { cursor ->
            val out = ArrayList<Entry>(limit)
            while (cursor.moveToNext()) {
                out.add(Entry(cursor.getString(0), cursor.getInt(1)))
            }
            return out
        }
    }

    fun isReady(): Boolean = db != null

    fun wordCount(): Int? {
        val database = db ?: return null
        database.rawQuery("SELECT COUNT(*) FROM words", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else null
        }
    }

    fun close() {
        db?.close()
    }

    companion object {
        private const val TAG = "SinhalaFreqDb"
        private const val ASSET_NAME = "sinhala_freq.db"
        private const val DB_NAME = "sinhala_freq.db"

        /** Opens (and copies from assets on first run) the frequency dictionary. */
        fun ensureReady(context: Context): SinhalaFrequencyDatabase = SinhalaFrequencyDatabase(context)

        private fun openReadOnly(context: Context): SQLiteDatabase? {
            return try {
                val path = context.getDatabasePath(DB_NAME)
                if (!path.exists()) {
                    path.parentFile?.mkdirs()
                    context.assets.open(ASSET_NAME).use { input ->
                        FileOutputStream(path).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open Sinhala frequency database", e)
                null
            }
        }
    }
}
