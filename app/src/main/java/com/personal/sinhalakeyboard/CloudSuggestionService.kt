package com.personal.sinhalakeyboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Cloud next-word suggestions via OpenRouter (optional, needs API key). */
class CloudSuggestionService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun predictNextWords(
        contextText: String,
        sinhala: Boolean,
        apiKey: String,
        tone: EnglishTone = EnglishTone.PROFESSIONAL,
        limit: Int = 5,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (contextText.isBlank() || apiKey.isBlank()) {
            return@withContext Result.success(emptyList())
        }
        try {
            val lang = if (sinhala) "Sinhala" else "English"
            val toneHint = if (sinhala) "" else " Use ${tone.aiDescription()}."
            val body = JSONObject().apply {
                put("model", GrammarFixer.MODEL)
                put("max_tokens", 120)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put(
                            "content",
                            "You predict the next word(s) someone is typing in $lang.$toneHint " +
                                "Given the message text so far, return ONLY a JSON array of up to " +
                                "$limit single words (or short phrases max 2 words) they are most " +
                                "likely to type next. Example: [\"am\", \"will\", \"can\"]. " +
                                "No explanation, no markdown.",
                        )
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", contextText.takeLast(200))
                    })
                })
            }

            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://github.com/personal/sinhala-keyboard")
                .addHeader("X-Title", "Sinhala Keyboard")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("OpenRouter ${response.code}"),
                    )
                }
                val content = JSONObject(responseBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                Result.success(parseWordList(content, limit))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseWordList(raw: String, limit: Int): List<String> {
        val trimmed = raw.trim()
        val jsonStart = trimmed.indexOf('[')
        val jsonEnd = trimmed.lastIndexOf(']')
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            try {
                val arr = JSONArray(trimmed.substring(jsonStart, jsonEnd + 1))
                return buildList {
                    for (i in 0 until arr.length()) {
                        val w = arr.optString(i).trim()
                        if (w.isNotEmpty()) add(w)
                        if (size >= limit) break
                    }
                }
            } catch (_: Exception) {
                // fall through
            }
        }
        return trimmed.split(",", "\n")
            .map { it.trim().trim('"', '\'', '.') }
            .filter { it.isNotEmpty() && !it.startsWith("[") }
            .take(limit)
    }
}
