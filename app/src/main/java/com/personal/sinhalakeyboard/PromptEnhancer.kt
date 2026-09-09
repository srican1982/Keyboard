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

/** Turns short informal notes into a complete, detailed AI prompt. */
class PromptEnhancer {

    companion object {
        const val MODEL = GrammarFixer.MODEL
        private const val API_URL = "https://openrouter.ai/api/v1/chat/completions"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun enhancePrompt(
        text: String,
        apiKey: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.success(text)
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("API key missing"))
        }

        try {
            val body = JSONObject().apply {
                put("model", MODEL)
                put("max_tokens", 1200)
                OpenRouterHelper.applyModelOptions(this, MODEL)
                put(
                    "messages",
                    JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put("role", "system")
                                put(
                                    "content",
                                    """
                                    You turn short, messy, or informal notes into a complete, high-quality prompt.

                                    The user typed a rough idea, not a finished prompt. Infer what they want,
                                    then rewrite it as a detailed ready-to-use prompt.

                                    Rules:
                                    - Keep the same intent. Do not invent a different subject.
                                    - Expand shorthand, slang, and incomplete phrases.
                                    - If it is about a photo, picture, image, poster, or visual scene,
                                      write a detailed image-generation prompt: subject, era, setting,
                                      lighting, camera/style, mood, colors, composition, clothing, and
                                      important details.
                                    - If it is a writing, chat, or task request, write a clear instruction
                                      prompt with goal, audience, tone, constraints, and output format.
                                    - Use fluent English.
                                    - Return ONLY the enhanced prompt. No quotes, labels, or explanation.

                                    Example input:
                                    80's type photo

                                    Example output:
                                    A cinematic 1980s photograph of a person in a neon-lit city street at
                                    night, retro fashion, film grain, saturated magenta and teal lighting,
                                    slightly faded color, analog 35mm look, candid pose, shallow depth of
                                    field, nostalgic mood.
                                    """.trimIndent(),
                                )
                            },
                        )
                        put(
                            JSONObject().apply {
                                put("role", "user")
                                put("content", text)
                            },
                        )
                    },
                )
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
                        IllegalStateException("OpenRouter error ${response.code}: $responseBody"),
                    )
                }

                val enhanced = OpenRouterHelper.extractAssistantText(responseBody)
                if (enhanced.isBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException("OpenRouter returned empty content"),
                    )
                }
                Result.success(enhanced.trim())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
