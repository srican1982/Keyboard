package com.personal.sinhalakeyboard

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.LinkedHashMap
import java.util.zip.GZIPInputStream

/**
 * Frequency-ranked Sinhala word database.
 *
 * Performance goals:
 *
 * - avoid repeating SQLite work for the same prefix
 * - avoid repeating exact frequency lookups
 * - keep hot results in RAM
 * - preserve current public API so callers do not need to change
 */
class SinhalaFrequencyDatabase(context: Context) {

    data class Entry(
        val word: String,
        val frequency: Int,
        val matchedPrefix: String = "",
        val matchedPrefixLength: Int = 0,
    )

    private val db: SQLiteDatabase?

    /**
     * Exact word-frequency cache.
     *
     * key   = Sinhala word
     * value = corpus frequency
     *
     * Includes zero-frequency misses too, so we don't keep querying
     * SQLite for words that do not exist.
     */
    private val frequencyCache =
        object : LinkedHashMap<String, Int>(
            FREQUENCY_CACHE_SIZE,
            0.75f,
            true,
        ) {

            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, Int>?,
            ): Boolean {
                return size > FREQUENCY_CACHE_SIZE
            }
        }

    /**
     * Prefix result cache.
     *
     * key = prefix + "|" + limit
     */
    private val prefixCache =
        object : LinkedHashMap<String, List<Entry>>(
            PREFIX_CACHE_SIZE,
            0.75f,
            true,
        ) {

            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, List<Entry>>?,
            ): Boolean {
                return size > PREFIX_CACHE_SIZE
            }
        }

    init {
        db = openReadOnly(context)
    }

    /**
     * Exact frequency lookup.
     *
     * This now checks RAM before SQLite.
     */
    @Synchronized
    fun lookupFrequency(
        word: String,
    ): Int {

        if (word.isEmpty() || db == null) {
            return 0
        }

        frequencyCache[word]?.let {
            return it
        }

        val result =
            db.rawQuery(
                """
                SELECT freq
                FROM words
                WHERE word = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(word),
            ).use { cursor ->

                if (cursor.moveToFirst()) {
                    cursor.getInt(0)
                } else {
                    0
                }
            }

        frequencyCache[word] =
            result

        return result
    }

    /**
     * Batch exact-frequency lookup.
     *
     * Important:
     *
     * 1. Return cached values immediately.
     * 2. Query SQLite only for missing words.
     * 3. Cache both hits and misses.
     */
    @Synchronized
    fun lookupFrequencies(
        words: Collection<String>,
    ): Map<String, Int> {

        if (words.isEmpty() || db == null) {
            return emptyMap()
        }

        val unique =
            words
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .toList()

        if (unique.isEmpty()) {
            return emptyMap()
        }

        val out =
            HashMap<String, Int>(
                unique.size
            )

        val missing =
            ArrayList<String>()

        /*
         * First use RAM.
         */
        for (word in unique) {

            val cached =
                frequencyCache[word]

            if (cached != null) {

                out[word] =
                    cached

            } else {

                missing.add(word)
            }
        }

        /*
         * Everything was already cached.
         */
        if (missing.isEmpty()) {
            return out
        }

        /*
         * SQLite IN() limit safety.
         */
        val chunkSize =
            400

        for (chunk in missing.chunked(chunkSize)) {

            val placeholders =
                chunk.joinToString(",") {
                    "?"
                }

            /*
             * Mark every requested word as zero first.
             *
             * If SQLite finds a row, we'll overwrite it.
             *
             * This means misses are cached too.
             */
            for (word in chunk) {

                out[word] =
                    0
            }

            db.rawQuery(
                """
                SELECT word, freq
                FROM words
                WHERE word IN ($placeholders)
                """.trimIndent(),
                chunk.toTypedArray(),
            ).use { cursor ->

                while (
                    cursor.moveToNext()
                ) {

                    val word =
                        cursor.getString(0)

                    val freq =
                        cursor.getInt(1)

                    out[word] =
                        freq
                }
            }

            /*
             * Store hits AND misses.
             */
            for (word in chunk) {

                frequencyCache[word] =
                    out[word] ?: 0
            }
        }

        return out
    }

    /**
     * Merge multiple prefix queries.
     *
     * Longer prefixes remain stronger than shorter relaxed prefixes.
     */
    fun queryMergedByPrefixes(
        prefixes: Collection<String>,
        limitPerPrefix: Int = 20,
        totalLimit: Int = 64,
    ): List<Entry> {

        if (
            prefixes.isEmpty() ||
            db == null ||
            totalLimit <= 0
        ) {
            return emptyList()
        }

        val normalizedPrefixes =
            prefixes
                .asSequence()
                .map {
                    it.trim()
                }
                .filter {
                    it.length >= 2
                }
                .distinct()
                .sortedByDescending {
                    it.length
                }
                .toList()

        if (normalizedPrefixes.isEmpty()) {
            return emptyList()
        }

        val merged =
            LinkedHashMap<String, Entry>()

        for (prefix in normalizedPrefixes) {

            val results =
                queryByPrefix(
                    prefix = prefix,
                    limit = limitPerPrefix,
                )

            for (entry in results) {

                val candidate =
                    Entry(
                        word = entry.word,
                        frequency = entry.frequency,
                        matchedPrefix = prefix,
                        matchedPrefixLength = prefix.length,
                    )

                val previous =
                    merged[candidate.word]

                if (
                    previous == null ||
                    candidate.matchedPrefixLength >
                    previous.matchedPrefixLength ||
                    (
                        candidate.matchedPrefixLength ==
                            previous.matchedPrefixLength &&
                        candidate.frequency >
                            previous.frequency
                        )
                ) {

                    merged[candidate.word] =
                        candidate
                }
            }

            /*
             * Safety guard.
             *
             * Don't keep exploring lots of weak prefixes once we already
             * have a healthy result pool.
             */
            if (
                merged.size >=
                totalLimit * 2
            ) {
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
     * Prefix query with RAM cache.
     */
    @Synchronized
    fun queryByPrefix(
        prefix: String,
        limit: Int = 12,
    ): List<Entry> {

        if (
            prefix.isEmpty() ||
            db == null ||
            limit <= 0
        ) {
            return emptyList()
        }

        val normalized =
            prefix.trim()

        if (normalized.isEmpty()) {
            return emptyList()
        }

        val cacheKey =
            "$normalized|$limit"

        prefixCache[cacheKey]
            ?.let {
                return it
            }

        val escaped =
            normalized
                .replace(
                    "\\",
                    "\\\\",
                )
                .replace(
                    "%",
                    "\\%",
                )
                .replace(
                    "_",
                    "\\_",
                )

        val result =
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
                    ArrayList<Entry>(
                        limit
                    )

                while (
                    cursor.moveToNext()
                ) {

                    val word =
                        cursor.getString(0)

                    val freq =
                        cursor.getInt(1)

                    out.add(
                        Entry(
                            word = word,
                            frequency = freq,
                            matchedPrefix = normalized,
                            matchedPrefixLength =
                                normalized.length,
                        )
                    )

                    /*
                     * Since we already have the frequency here,
                     * also warm the exact-frequency cache.
                     */
                    frequencyCache[word] =
                        freq
                }

                out
            }

        prefixCache[cacheKey] =
            result

        return result
    }

    fun isReady(): Boolean {
        return db != null
    }

    fun wordCount(): Int? {

        val database =
            db ?: return null

        return database
            .rawQuery(
                """
                SELECT COUNT(*)
                FROM words
                """.trimIndent(),
                null,
            )
            .use { cursor ->

                if (
                    cursor.moveToFirst()
                ) {
                    cursor.getInt(0)
                } else {
                    null
                }
            }
    }

    @Synchronized
    fun clearMemoryCaches() {

        frequencyCache.clear()
        prefixCache.clear()
    }

    fun close() {

        clearMemoryCaches()

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
         * A few thousand exact word lookups is tiny in memory,
         * but enough to make normal typing reuse very effective.
         */
        private const val FREQUENCY_CACHE_SIZE =
            4096

        /**
         * Prefixes repeat constantly while typing.
         *
         * Example:
         *
         * ma
         * mag
         * mage
         *
         * and the same words are often typed again later.
         */
        private const val PREFIX_CACHE_SIZE =
            512

        fun ensureReady(
            context: Context,
        ): SinhalaFrequencyDatabase {

            return SinhalaFrequencyDatabase(
                context
            )
        }

        private fun openReadOnly(
            context: Context,
        ): SQLiteDatabase? {

            return try {

                val path =
                    context.getDatabasePath(
                        DB_NAME
                    )

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

            dest.parentFile
                ?.mkdirs()

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

                    FileOutputStream(
                        dest
                    ).use { output ->

                        input.copyTo(
                            output
                        )
                    }
                }
        }

        private fun decompressGzAsset(
            context: Context,
            assetName: String,
            dest: File,
        ) {

            GZIPInputStream(
                context.assets.open(
                    assetName
                )
            ).use { input ->

                FileOutputStream(
                    dest
                ).use { output ->

                    input.copyTo(
                        output
                    )
                }
            }
        }
    }
}
