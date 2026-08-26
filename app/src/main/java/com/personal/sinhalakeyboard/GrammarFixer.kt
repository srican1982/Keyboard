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

class GrammarFixer {

    companion object {
        const val MODEL = "google/gemini-3-flash-preview"
        private const val API_URL = "https://openrouter.ai/api/v1/chat/completions"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun fixGrammar(
        text: String,
        apiKey: String,
        tone: EnglishTone = EnglishTone.PROFESSIONAL,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.success(text)
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("API key missing"))

        try {
            val body = JSONObject().apply {
                put("model", MODEL)
                put("max_tokens", 4096)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put(
                            "content",
                            "You are an expert English writing assistant. The user sends a complete message " +
                                "that may contain multiple sentences, grammar errors, typos, or unclear phrasing. " +
                                "Read the ENTIRE message as one unified piece — understand what they mean to say, " +
                                "then rewrite it with correct grammar, spelling, and punctuation. Improve clarity " +
                                "and flow where helpful, but keep the same meaning and intent. " +
                                "Use a ${tone.aiDescription()} tone throughout. " +
                                "Do NOT fix sentences one-by-one in isolation. " +
                                "Return ONLY the improved text — no explanations, quotes, or labels."
                        )
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", text)
                    })
                })
            }

            val request = Request.Builder()
                .url(API_URL)
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
                        IllegalStateException("OpenRouter error ${response.code}: $responseBody")
                    )
                }

                val json = JSONObject(responseBody)
                val corrected = json
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()

                Result.success(corrected)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
