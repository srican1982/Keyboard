package com.personal.sinhalakeyboard

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

/**
 * Frequency-ranked Sinhala words from the UCSC NLP verified list
 * (nlpcuom/Word-Frequency-List-for-Sinhala — ~280k words).
 *
 * Important:
 * Prefix relevance is preserved so results from a stronger/longer prefix
 * are preferred over results from a weaker/shorter prefix.
 */
class SinhalaFrequencyDatabase(context: Context) {

    data class Entry(
        val word: String,
        val frequency: Int,
        val matchedPrefix: String = "",
        val matchedPrefixLength: Int = 0,
    )

    private val db: SQLiteDatabase?

    init {
        db = openReadOnly(context)
    }

    fun lookupFrequency(word: String): Int {
        if (word.isEmpty() || db == null) return 0

        db.rawQuery(
            "SELECT freq FROM words WHERE word = ? LIMIT 1",
            arrayOf(word),
        ).use { cursor ->
            return if (cursor.moveToFirst()) {
                cursor.getInt(0)
            } else {
                0
            }
        }
    }

    fun lookupFrequencies(
        words: Collection<String>,
    ): Map<String, Int> {

        if (words.isEmpty() || db == null) {
            return emptyMap()
        }

        val unique = words
            .filter { it.isNotEmpty() }
            .distinct()

        if (unique.isEmpty()) {
            return emptyMap()
        }

        val out = HashMap<String, Int>(unique.size)

        val chunkSize = 400

        for (chunk in unique.chunked(chunkSize)) {

            val placeholders =
                chunk.joinToString(",") { "?" }

            db.rawQuery(
                """
                SELECT word, freq
                FROM words
                WHERE word IN ($placeholders)
                """.trimIndent(),
                chunk.toTypedArray(),
            ).use { cursor ->

                val wordIdx =
                    cursor.getColumnIndex("word")

                val freqIdx =
                    cursor.getColumnIndex("freq")

                while (cursor.moveToNext()) {

                    val word =
                        cursor.getString(wordIdx)

                    val freq =
                        cursor.getInt(freqIdx)

                    out[word] = freq
                }
            }
        }

        return out
    }

    /**
     * Merge prefix hits from multiple Sinhala prefixes.
     *
     * Ranking priority:
     *
     * 1. Longer matched prefix
     * 2. Higher frequency
     * 3. Shorter final word length
     *
     * This is much better than globally sorting only by corpus frequency.
     */
    fun queryMergedByPrefixes(
        prefixes: Collection<String>,
        limitPerPrefix: Int = 20,
        totalLimit: Int = 64,
    ): List<Entry> {

        if (prefixes.isEmpty() || db == null) {
            return emptyList()
        }

        /*
         * Normalize and prefer stronger/longer prefixes first.
         */
        val normalizedPrefixes = prefixes
            .asSequence()
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()
            .sortedByDescending { it.length }
            .toList()

        if (normalizedPrefixes.isEmpty()) {
            return emptyList()
        }

        /*
         * Word -> best entry.
         *
         * If the same word appears for multiple prefixes,
         * keep the entry with the LONGEST matched prefix.
         *
         * If prefix lengths tie, keep the higher frequency.
         */
        val merged = LinkedHashMap<String, Entry>()

        for (prefix in normalizedPrefixes) {

            val results =
                queryByPrefix(
                    prefix = prefix,
                    limit = limitPerPrefix,
                )

            for (entry in results) {

                val candidate = Entry(
                    word = entry.word,
                    frequency = entry.frequency,
                    matchedPrefix = prefix,
                    matchedPrefixLength = prefix.length,
                )

                val previous =
                    merged[candidate.word]

                if (
                    previous == null ||
                    candidate.matchedPrefixLength > previous.matchedPrefixLength ||
                    (
                        candidate.matchedPrefixLength == previous.matchedPrefixLength &&
                        candidate.frequency > previous.frequency
                    )
                ) {
                    merged[candidate.word] = candidate
                }
            }

            /*
             * Avoid unbounded candidate growth.
             *
             * We still collect more than totalLimit so ranking has room,
             * but we don't let very broad prefixes flood memory.
             */
            if (merged.size >= totalLimit * 3) {
                break
            }
        }

        return merged
            .values
            .sortedWith(
                compareByDescending<Entry> {
                    it.matchedPrefixLength
                }
                    .thenByDescending {
                        it.frequency
                    }
                    .thenBy {
                        it.word.length
                    }
                    .thenBy {
                        it.word
                    },
            )
            .take(totalLimit)
    }

    /**
     * Query words that begin with the supplied Sinhala prefix.
     *
     * Within one exact prefix, frequency order is appropriate.
     */
    fun queryByPrefix(
        prefix: String,
        limit: Int = 12,
    ): List<Entry> {

        if (prefix.isEmpty() || db == null) {
            return emptyList()
        }

        val escaped =
            prefix
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")

        db.rawQuery(
            """
            SELECT word, freq
            FROM words
            WHERE word LIKE ? ESCAPE '\'
            ORDER BY freq DESC, LENGTH(word), word
            LIMIT ?
            """.trimIndent(),
            arrayOf(
                "$escaped%",
                limit.toString(),
            ),
        ).use { cursor ->

            val out =
                ArrayList<Entry>(limit)

            while (cursor.moveToNext()) {

                out.add(
                    Entry(
                        word = cursor.getString(0),
                        frequency = cursor.getInt(1),
                        matchedPrefix = prefix,
                        matchedPrefixLength = prefix.length,
                    )
                )
            }

            return out
        }
    }

    fun isReady(): Boolean {
        return db != null
    }

    fun wordCount(): Int? {

        val database =
            db ?: return null

        database
            .rawQuery(
                "SELECT COUNT(*) FROM words",
                null,
            )
            .use { cursor ->

                return if (cursor.moveToFirst()) {
                    cursor.getInt(0)
                } else {
                    null
                }
            }
    }

    fun close() {
        db?.close()
    }

    companion object {

        private const val TAG =
            "SinhalaFreqDb"

        private const val ASSET_GZ =
            "sinhala_freq.db.gz"

        private const val ASSET_DB =
            "sinhala_freq.db"

        private const val DB_NAME =
            "sinhala_freq.db"

        /**
         * Opens and prepares the frequency dictionary.
         */
        fun ensureReady(
            context: Context,
        ): SinhalaFrequencyDatabase {

            return SinhalaFrequencyDatabase(context)
        }

        private fun openReadOnly(
            context: Context,
        ): SQLiteDatabase? {

            return try {

                val path =
                    context.getDatabasePath(DB_NAME)

                if (!path.exists()) {
                    prepareDatabase(
                        context = context,
                        dest = path,
                    )
                }

                SQLiteDatabase.openDatabase(
                    path.path,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Failed to open Sinhala frequency database",
                    e,
                )

                null
            }
        }

        private fun prepareDatabase(
            context: Context,
            dest: File,
        ) {

            dest.parentFile?.mkdirs()

            when {

                hasAsset(
                    context = context,
                    name = ASSET_GZ,
                ) -> {

                    decompressGzAsset(
                        context = context,
                        assetName = ASSET_GZ,
                        dest = dest,
                    )
                }

                hasAsset(
                    context = context,
                    name = ASSET_DB,
                ) -> {

                    copyAsset(
                        context = context,
                        assetName = ASSET_DB,
                        dest = dest,
                    )
                }

                else -> {
                    error(
                        "No Sinhala dictionary asset found"
                    )
                }
            }
        }

        private fun hasAsset(
            context: Context,
            name: String,
        ): Boolean {

            return try {

                context.assets
                    .open(name)
                    .close()

                true

            } catch (_: Exception) {

                false
            }
        }

        private fun copyAsset(
            context: Context,
            assetName: String,
            dest: File,
        ) {

            context.assets
                .open(assetName)
                .use { input ->

                    FileOutputStream(dest)
                        .use { output ->

                            input.copyTo(output)
                        }
                }
        }

        private fun decompressGzAsset(
            context: Context,
            assetName: String,
            dest: File,
        ) {

            GZIPInputStream(
                context.assets.open(assetName)
            ).use { input ->

                FileOutputStream(dest)
                    .use { output ->

                        input.copyTo(output)
                    }
            }
        }
    }
}
