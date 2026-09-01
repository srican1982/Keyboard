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

/** Cloud AI suggestions via OpenRouter (optional, needs API key). */
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
        val system = if (sinhala) {
            "You predict the next word(s) in a Sinhala text message written in Sinhala script. " +
                "Given the message so far, return ONLY a JSON array of up to $limit likely next " +
                "words in Sinhala Unicode (සිංහල අකුරු only — not English, not Singlish roman). " +
                "Short 2-word phrases OK. Example: [\"යන්න\", \"කොහොමද\"]. No explanation, no markdown."
        } else {
            "You predict the next word(s) someone is typing in English. Use ${tone.aiDescription()}. " +
                "Given the message text so far, return ONLY a JSON array of up to $limit single words " +
                "(or short phrases max 2 words) they are most likely to type next. " +
                "Example: [\"am\", \"will\", \"can\"]. No explanation, no markdown."
        }
        callOpenRouter(
            apiKey = apiKey,
            systemPrompt = system,
            userContent = contextText.takeLast(200),
            maxTokens = 120,
            limit = limit,
        )
    }

    /** Complete partial Singlish typing → Sinhala script word suggestions. */
    suspend fun predictSinhalaWordCompletions(
        contextText: String,
        partialSinglish: String,
        apiKey: String,
        limit: Int = 5,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (partialSinglish.isBlank() || apiKey.isBlank()) {
            return@withContext Result.success(emptyList())
        }
        val system = "You help someone type Sinhala using Singlish (Roman-letter phonetic spelling). " +
            "Given the message context and the partial Singlish they are typing, return ONLY a JSON " +
            "array of up to $limit complete words in Sinhala script (Unicode U+0D80–U+0DFF). " +
            "Understand Sri Lankan Singlish spellings (mama, kohomada, yanna, etc.). " +
            "Prefer words matching the partial spelling. Example for \"mam\": [\"මම\"]. " +
            "For \"koho\": [\"කොහොමද\", \"කොහෙද\"]. Sinhala script ONLY — no English, no roman. " +
            "No explanation, no markdown."
        val user = buildString {
            if (contextText.isNotBlank()) {
                append("Message so far:\n")
                append(contextText.takeLast(250))
                append("\n\n")
            }
            append("Partial Singlish being typed: \"")
            append(partialSinglish)
            append('"')
        }
        callOpenRouter(
            apiKey = apiKey,
            systemPrompt = system,
            userContent = user,
            maxTokens = 120,
            limit = limit,
        )
    }

    /** Complete the partial word at the end of English text (context-aware). */
    suspend fun predictWordCompletions(
        contextText: String,
        partialWord: String,
        apiKey: String,
        tone: EnglishTone = EnglishTone.PROFESSIONAL,
        limit: Int = 5,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (partialWord.isBlank() || apiKey.isBlank()) {
            return@withContext Result.success(emptyList())
        }
        val system = "You complete English words someone is typing. Use ${tone.aiDescription()}. " +
            "Given the message so far and the partial word they are typing, return ONLY a JSON " +
            "array of up to $limit complete words they most likely mean. Prefer words that start " +
            "with the same letters as the partial word. Include context-aware corrections when " +
            "the partial word is a typo (e.g. \"teh\" → \"the\"). Example: [\"hello\", \"help\"]. " +
            "No explanation, no markdown."
        val user = buildString {
            append(contextText.takeLast(250))
            append("\n\nPartial word being typed: \"")
            append(partialWord)
            append('"')
        }
        callOpenRouter(
            apiKey = apiKey,
            systemPrompt = system,
            userContent = user,
            maxTokens = 100,
            limit = limit,
        )
    }

    /** Next words, phrases, and short sentence continuations for English. */
    suspend fun predictNextCompletions(
        contextText: String,
        apiKey: String,
        tone: EnglishTone = EnglishTone.PROFESSIONAL,
        limit: Int = 6,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (contextText.isBlank() || apiKey.isBlank()) {
            return@withContext Result.success(emptyList())
        }
        val system = "You predict what someone will type next in English. Use ${tone.aiDescription()}. " +
            "Given the message text so far, return ONLY a JSON array of up to $limit suggestions " +
            "mixing: single next words, natural short phrases (2–5 words), and brief sentence " +
            "continuations (up to about 12 words). Order by likelihood. Examples: " +
            "[\"am\", \"looking forward to\", \"Thank you for your message\"]. " +
            "No explanation, no markdown."
        callOpenRouter(
            apiKey = apiKey,
            systemPrompt = system,
            userContent = contextText.takeLast(300),
            maxTokens = 180,
            limit = limit,
        )
    }

    private fun callOpenRouter(
        apiKey: String,
        systemPrompt: String,
        userContent: String,
        maxTokens: Int,
        limit: Int,
    ): Result<List<String>> {
        return try {
            val body = JSONObject().apply {
                put("model", GrammarFixer.MODEL)
                put("max_tokens", maxTokens)
                OpenRouterHelper.applyModelOptions(this, GrammarFixer.MODEL)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userContent)
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
                    return Result.failure(IllegalStateException("OpenRouter ${response.code}"))
                }
                val content = OpenRouterHelper.extractAssistantText(responseBody)
                if (content.isBlank()) {
                    return Result.failure(IllegalStateException("OpenRouter returned empty content"))
                }
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
