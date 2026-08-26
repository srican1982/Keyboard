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

/** Translates Singlish (romanized Sinhala) messages to natural English via OpenRouter. */
class SinglishTranslator {

    companion object {
        const val MODEL = GrammarFixer.MODEL
        private const val API_URL = "https://openrouter.ai/api/v1/chat/completions"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun translateToEnglish(text: String, apiKey: String): Result<String> = withContext(Dispatchers.IO) {
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
                            "You translate Sri Lankan Singlish — informal Roman-letter Sinhala " +
                                "typing (mixed English words allowed) — into clear, natural English. " +
                                "Understand the full message context and intended meaning, not word-by-word. " +
                                "Fix grammar and spelling in the English output. " +
                                "Return ONLY the English translation — no explanations or quotes."
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
                val translated = json
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                Result.success(translated)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
