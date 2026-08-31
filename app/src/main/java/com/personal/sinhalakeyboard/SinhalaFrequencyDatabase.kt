package com.personal.sinhalakeyboard

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

/**
 * Frequency-ranked Sinhala words from the UCSC NLP 2M corpus
 * (nlpcuom/Word-Frequency-List-for-Sinhala — ~2.1M words).
 *
 * Ships as gzip-compressed SQLite in assets; decompresses once on first use.
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
        private const val ASSET_GZ = "sinhala_freq.db.gz"
        private const val ASSET_DB = "sinhala_freq.db"
        private const val DB_NAME = "sinhala_freq.db"

        /** Opens (and prepares from assets on first run) the frequency dictionary. */
        fun ensureReady(context: Context): SinhalaFrequencyDatabase = SinhalaFrequencyDatabase(context)

        private fun openReadOnly(context: Context): SQLiteDatabase? {
            return try {
                val path = context.getDatabasePath(DB_NAME)
                if (!path.exists()) {
                    prepareDatabase(context, path)
                }
                SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open Sinhala frequency database", e)
                null
            }
        }

        private fun prepareDatabase(context: Context, dest: File) {
            dest.parentFile?.mkdirs()
            when {
                hasAsset(context, ASSET_GZ) -> decompressGzAsset(context, ASSET_GZ, dest)
                hasAsset(context, ASSET_DB) -> copyAsset(context, ASSET_DB, dest)
                else -> error("No Sinhala dictionary asset found")
            }
        }

        private fun hasAsset(context: Context, name: String): Boolean {
            return try {
                context.assets.open(name).close()
                true
            } catch (_: Exception) {
                false
            }
        }

        private fun copyAsset(context: Context, assetName: String, dest: File) {
            context.assets.open(assetName).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
        }

        private fun decompressGzAsset(context: Context, assetName: String, dest: File) {
            GZIPInputStream(context.assets.open(assetName)).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}
