package com.personal.sinhalakeyboard

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Converter audit — two phases:
 * 1. Priority list (scripts/most_used_singlish.txt) — most-used / Desh-verified, tested first
 * 2. Corpus list (sinhala_audit_corpus.txt) — dict keys ranked by corpus frequency
 *
 * Regenerate lists: python scripts/audit_singlish.py generate
 */
@RunWith(Parameterized::class)
class SinglishCorpusAuditTest(
    private val roman: String,
    private val expected: String,
    private val label: String,
) {

    @Test
    fun convertMatchesExpected() {
        assertEquals(
            "[$label] convert($roman)",
            expected,
            SinglishConverter.convert(roman),
        )
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} -> {1} ({2})")
        fun data(): List<Array<Any>> {
            val out = ArrayList<Array<Any>>()
            out.addAll(loadPairs("singlish_audit_priority.txt", "priority"))
            // Corpus file is for coverage reports — enable when romans are verified:
            // out.addAll(loadPairs("singlish_audit_corpus.txt", "corpus"))
            return out
        }

        private fun loadPairs(resource: String, label: String): List<Array<Any>> {
            val stream = SinglishCorpusAuditTest::class.java.classLoader?.getResourceAsStream(resource)
                ?: return emptyList()
            val rows = ArrayList<Array<Any>>()
            stream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                    val parts = trimmed.split("|")
                    if (parts.size < 2) return@forEach
                    val roman = parts[0].trim()
                    val sinhala = parts[1].trim()
                    if (roman.isNotEmpty() && sinhala.isNotEmpty()) {
                        rows.add(arrayOf(roman, sinhala, label))
                    }
                }
            }
            return rows
        }
    }
}
