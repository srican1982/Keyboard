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

/**
 * Cloud AI suggestions via OpenRouter.
 *
 * Responsibilities:
 *
 * 1. Sinhala next-word prediction.
 * 2. Singlish -> Sinhala word completion.
 * 3. English word completion.
 * 4. English next-word / phrase prediction.
 * 5. English smart sentence completion after the user pauses.
 *
 * IMPORTANT:
 *
 * This class does NOT decide WHEN cloud AI should run.
 *
 * KeyboardService controls debounce / pause timing so that continuous
 * typing does not create unnecessary API requests.
 */
class CloudSuggestionService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    /**
     * Keep keyboard suggestions independent from GrammarFixer.MODEL.
     *
     * This lets us change the grammar model later without accidentally
     * changing the keyboard prediction model.
     */
    private companion object {

        const val SINHALA_SUGGESTION_MODEL =
            "google/gemini-3-flash-preview"

        const val ENGLISH_SUGGESTION_MODEL =
            "google/gemini-3-flash-preview"
    }

    /**
     * ================================================================
     * SINHALA NEXT WORD
     * ================================================================
     *
     * Used after a Sinhala word has been committed.
     */
    suspend fun predictNextWords(
        contextText: String,
        sinhala: Boolean,
        apiKey: String,
        tone: EnglishTone = EnglishTone.PROFESSIONAL,
        limit: Int = 5,
    ): Result<List<String>> = withContext(Dispatchers.IO) {

        if (
            contextText.isBlank() ||
            apiKey.isBlank()
        ) {
            return@withContext Result.success(
                emptyList()
            )
        }

        val system =
            if (sinhala) {

                """
                You are a predictive keyboard engine for natural modern Sinhala used by Sri Lankan speakers.

                Predict what the user is most likely to type NEXT based on the message so far.

                Return ONLY a JSON array containing up to $limit likely next words or very short phrases.

                Requirements:
                - Sinhala Unicode script only.
                - Natural Sri Lankan Sinhala.
                - Rank the most likely suggestion first.
                - Prefer useful everyday wording that fits the preceding context.
                - Short 2-word phrases are allowed when natural.
                - Do not return English.
                - Do not return Roman Singlish.
                - Do not explain anything.
                - Do not use markdown.

                Example:
                ["යන්න", "කොහොමද", "කරන්න"]
                """.trimIndent()

            } else {

                """
                You are an English predictive keyboard engine.

                Use ${tone.aiDescription()}.

                The message so far may contain typos, informal spelling,
                or phonetic English. Infer what the user meant, then
                predict the next word(s) as if the text were already
                corrected.

                Return ONLY a JSON array containing up to $limit likely
                next words or very short phrases.

                Rank the most likely first.

                No explanation.
                No markdown.
                """.trimIndent()
            }

        callOpenRouter(
            apiKey = apiKey,
            systemPrompt = system,
            userContent = contextText.takeLast(300),
            maxTokens = 70,
            limit = limit,
            model = if (sinhala) SINHALA_SUGGESTION_MODEL else ENGLISH_SUGGESTION_MODEL,
        )
    }

    /**
     * ================================================================
     * SINGLISH -> SINHALA WORD COMPLETION
     * ================================================================
     *
     * This is NOT intended to be a literal transliterator.
     *
     * Gemini should infer what Sinhala word the user probably means
     * even when the Roman spelling is informal or ambiguous.
     *
     * Examples:
     *
     * mam
     * -> මම
     *
     * patiyo
     * -> පැටියෝ
     *
     * sankayaawa
     * -> සංක්‍යාව
     *
     * Context should influence ranking.
     */
    suspend fun predictSinhalaWordCompletions(
        contextText: String,
        partialSinglish: String,
        apiKey: String,
        limit: Int = 5,
    ): Result<List<String>> = withContext(Dispatchers.IO) {

        val partial =
            partialSinglish.trim()

        if (
            partial.isBlank() ||
            apiKey.isBlank()
        ) {
            return@withContext Result.success(
                emptyList()
            )
        }

        val system =
            """
            You are a Sinhala predictive keyboard engine for Sri Lankan users typing Sinhala phonetically with Roman letters (Singlish).

            The Roman spelling may be:
            - informal
            - incomplete
            - ambiguous
            - phonetically approximate
            - spelled differently by different Sri Lankan users

            Infer the most likely NATURAL Sinhala word the user intends.

            Do NOT perform only literal character-by-character transliteration.

            Consider common Singlish ambiguities including:
            - dental vs retroflex consonants
            - ද / ඩ type ambiguity
            - ත / ට type ambiguity
            - න / ණ type ambiguity
            - ල / ළ type ambiguity
            - a / ae vowel ambiguity
            - short vs long vowels
            - anusvara ං
            - nasal consonants
            - consonant clusters
            - common spoken Sri Lankan Singlish spellings

            Use BOTH:
            1. the preceding message context
            2. the partial Singlish currently being typed

            Return ONLY a JSON array containing up to $limit COMPLETE Sinhala words.

            Requirements:
            - Sinhala Unicode script only.
            - Rank the most likely intended word first.
            - Prefer real, natural Sinhala words.
            - Do not return Roman text.
            - Do not return English.
            - Do not explain anything.
            - Do not use markdown.

            Examples:

            Partial:
            mam

            Output:
            ["මම"]

            Partial:
            koho

            Possible output:
            ["කොහොමද", "කොහෙද"]

            Partial:
            patiyo

            Possible intended word:
            ["පැටියෝ"]

            Partial:
            sankayaawa

            Possible intended word:
            ["සංක්‍යාව"]
            """.trimIndent()

        val user =
            buildString {

                if (
                    contextText.isNotBlank()
                ) {

                    append(
                        "Message context:\n"
                    )

                    append(
                        contextText.takeLast(
                            300
                        )
                    )

                    append(
                        "\n\n"
                    )
                }

                append(
                    "Partial Singlish currently being typed:\n"
                )

                append(
                    partial
                )
            }

        callOpenRouter(
            apiKey = apiKey,
            systemPrompt = system,
            userContent = user,
            maxTokens = 65,
            limit = limit,
            model = SINHALA_SUGGESTION_MODEL,
        )
    }

    /**
     * ================================================================
     * ENGLISH WORD COMPLETION
     * ================================================================
     *
     * Used while typing an individual word.
     *
     * Example:
     *
     * hel
     * -> hello
     * -> help
     */
    suspend fun predictWordCompletions(
        contextText: String,
        partialWord: String,
        apiKey: String,
        tone: EnglishTone = EnglishTone.PROFESSIONAL,
        limit: Int = 5,
    ): Result<List<String>> = withContext(Dispatchers.IO) {

        val partial =
            partialWord.trim()

        if (
            partial.isBlank() ||
            apiKey.isBlank()
        ) {
            return@withContext Result.success(
                emptyList()
            )
        }

        val system =
            """
            You are an English predictive keyboard word-completion engine.

            Use ${tone.aiDescription()}.

            The user is typing an unfinished or misspelled English word.
            Infer the intended word from:
            - the typed letters (even if wrong, phonetic, or incomplete)
            - the preceding sentence context
            - common typos, missing letters, swapped letters, and informal spelling

            Do NOT require the suggestion to start with the same letters.
            If the typed text is a typo, return the correctly spelled word.

            Return ONLY a JSON array containing up to $limit complete English words.
            Rank the most likely intended word first.

            Examples:
            "teh" -> ["the"]
            "becos" -> ["because"]
            "recieve" -> ["receive"]
            "wana" -> ["want"]
            "hel" -> ["hello", "help"]

            No explanation.
            No markdown.
            """.trimIndent()

        val user =
            buildString {

                if (
                    contextText.isNotBlank()
                ) {

                    append(
                        "Text before cursor:\n"
                    )

                    append(
                        contextText.takeLast(
                            300
                        )
                    )

                    append(
                        "\n\n"
                    )
                }

                append(
                    "Partial word:\n"
                )

                append(
                    partial
                )
            }

        callOpenRouter(
            apiKey = apiKey,
            systemPrompt = system,
            userContent = user,
            maxTokens = 55,
            limit = limit,
            model = ENGLISH_SUGGESTION_MODEL,
        )
    }

    /**
     * ================================================================
     * ENGLISH NEXT WORD / SHORT PHRASE
     * ================================================================
     *
     * Used after a word is completed.
     */
    suspend fun predictNextCompletions(
        contextText: String,
        apiKey: String,
        tone: EnglishTone = EnglishTone.PROFESSIONAL,
        limit: Int = 6,
    ): Result<List<String>> = withContext(Dispatchers.IO) {

        if (
            contextText.isBlank() ||
            apiKey.isBlank()
        ) {
            return@withContext Result.success(
                emptyList()
            )
        }

        val system =
            """
            You are an English predictive keyboard engine.

            Use ${tone.aiDescription()}.

            The text so far may contain typos or informal spelling.
            Infer the intended meaning first, then predict what the
            user is most likely to type next.

            Return ONLY a JSON array containing up to $limit suggestions.

            Suggestions may contain:
            - a single next word
            - a natural short phrase
            - a brief continuation

            Prefer concise useful completions.
            Rank the most likely suggestion first.

            Do not repeat the entire text already typed.
            No explanation.
            No markdown.
            """.trimIndent()

        callOpenRouter(
            apiKey = apiKey,
            systemPrompt = system,
            userContent = contextText.takeLast(350),
            maxTokens = 90,
            limit = limit,
            model = ENGLISH_SUGGESTION_MODEL,
        )
    }

    /**
     * ================================================================
     * ENGLISH SMART SENTENCE COMPLETION
     * ================================================================
     *
     * This will be called ONLY after KeyboardService determines that
     * the user has paused while typing.
     *
     * Example:
     *
     * Existing text:
     *
     *     I want to t
     *
     * Gemini should return:
     *
     *     tell you something important
     *     talk to you about this
     *     thank you for your help
     *
     * It should NOT return:
     *
     *     I want to tell you something important
     *
     * This matters because KeyboardService will replace only the current
     * unfinished word ("t") when a suggestion is selected.
     */
    suspend fun predictEnglishSentenceCompletions(
        contextText: String,
        partialWord: String,
        apiKey: String,
        tone: EnglishTone = EnglishTone.PROFESSIONAL,
        limit: Int = 3,
    ): Result<List<String>> = withContext(Dispatchers.IO) {

        val context =
            contextText.trimEnd()

        val partial =
            partialWord.trim()

        if (
            context.isBlank() ||
            partial.isBlank() ||
            apiKey.isBlank()
        ) {
            return@withContext Result.success(
                emptyList()
            )
        }

        /*
         * Avoid spending money on very short / meaningless context.
         */
        val words =
            context
                .split(
                    Regex("\\s+")
                )
                .filter {
                    it.isNotBlank()
                }

        if (
            context.length < 5 ||
            words.size < 1
        ) {
            return@withContext Result.success(
                emptyList()
            )
        }

        val system =
            """
            You are a smart English keyboard sentence-completion engine.

            The user has PAUSED while typing.

            Use ${tone.aiDescription()}.

            The typed text may contain typos, phonetic spelling, or an
            unfinished misspelled word. Infer what they meant, then
            continue naturally from that intended meaning.

            IMPORTANT INSERTION RULE:

            Return the FULL completion of the CURRENT unfinished/misspelled
            word plus the words that should naturally follow it.

            If the current word is a typo, start the suggestion with the
            correctly spelled intended word.

            Do NOT repeat words that came before the current unfinished word.

            Examples:

            Existing text:
            I want to t

            Current unfinished word:
            t

            GOOD:
            ["tell you something important", "talk to you about this"]

            Existing text:
            Can you ple

            Current unfinished word:
            ple

            GOOD:
            ["please send me the file", "please let me know"]

            Existing text:
            I wana tel

            Current unfinished word:
            tel

            GOOD:
            ["tell you something", "tell them later"]

            Existing text:
            becos i

            Current unfinished word:
            i

            GOOD:
            ["I need your help", "I will be late"]

            BAD:
            ["I want to tell you something important"]

            Return ONLY a JSON array containing up to $limit likely continuations.
            Each suggestion should normally be about 2 to 8 words.
            Rank the most likely continuation first.

            No explanation.
            No markdown.
            """.trimIndent()

        val user =
            buildString {

                append(
                    "Existing text:\n"
                )

                append(
                    context.takeLast(
                        400
                    )
                )

                append(
                    "\n\nCurrent unfinished word:\n"
                )

                append(
                    partial
                )
            }

        callOpenRouter(
            apiKey = apiKey,
            systemPrompt = system,
            userContent = user,
            maxTokens = 70,
            limit = limit,
            model = ENGLISH_SUGGESTION_MODEL,
        )
    }

    /**
     * ================================================================
     * OPENROUTER
     * ================================================================
     */
    private fun callOpenRouter(
        apiKey: String,
        systemPrompt: String,
        userContent: String,
        maxTokens: Int,
        limit: Int,
        model: String,
    ): Result<List<String>> {

        return try {

            val body =
                JSONObject().apply {

                    put(
                        "model",
                        model,
                    )

                    put(
                        "max_tokens",
                        maxTokens,
                    )

                    /*
                     * Low temperature makes keyboard predictions more
                     * deterministic and less creative/random.
                     */
                    put(
                        "temperature",
                        0.2,
                    )

                    OpenRouterHelper.applyModelOptions(
                        this,
                        model,
                    )

                    put(
                        "messages",
                        JSONArray().apply {

                            put(
                                JSONObject().apply {

                                    put(
                                        "role",
                                        "system",
                                    )

                                    put(
                                        "content",
                                        systemPrompt,
                                    )
                                }
                            )

                            put(
                                JSONObject().apply {

                                    put(
                                        "role",
                                        "user",
                                    )

                                    put(
                                        "content",
                                        userContent,
                                    )
                                }
                            )
                        },
                    )
                }

            val request =
                Request.Builder()
                    .url(
                        "https://openrouter.ai/api/v1/chat/completions"
                    )
                    .addHeader(
                        "Authorization",
                        "Bearer $apiKey",
                    )
                    .addHeader(
                        "Content-Type",
                        "application/json",
                    )
                    .addHeader(
                        "HTTP-Referer",
                        "https://github.com/personal/sinhala-keyboard",
                    )
                    .addHeader(
                        "X-Title",
                        "Sinhala Keyboard",
                    )
                    .post(
                        body
                            .toString()
                            .toRequestBody(
                                "application/json"
                                    .toMediaType()
                            )
                    )
                    .build()

            client
                .newCall(request)
                .execute()
                .use { response ->

                    val responseBody =
                        response
                            .body
                            ?.string()
                            .orEmpty()

                    if (
                        !response.isSuccessful
                    ) {

                        return Result.failure(
                            IllegalStateException(
                                "OpenRouter ${response.code}"
                            )
                        )
                    }

                    val content =
                        OpenRouterHelper
                            .extractAssistantText(
                                responseBody
                            )

                    if (
                        content.isBlank()
                    ) {

                        return Result.failure(
                            IllegalStateException(
                                "OpenRouter returned empty content"
                            )
                        )
                    }

                    Result.success(
                        parseWordList(
                            content,
                            limit,
                        )
                    )
                }

        } catch (
            e: Exception
        ) {

            Result.failure(e)
        }
    }

    /**
     * Gemini should normally return a JSON array.
     *
     * The fallback parser is intentionally kept in case a provider/model
     * occasionally returns comma/newline separated text instead.
     */
    private fun parseWordList(
        raw: String,
        limit: Int,
    ): List<String> {

        val trimmed =
            raw.trim()

        val jsonStart =
            trimmed.indexOf('[')

        val jsonEnd =
            trimmed.lastIndexOf(']')

        if (
            jsonStart >= 0 &&
            jsonEnd > jsonStart
        ) {

            try {

                val array =
                    JSONArray(
                        trimmed.substring(
                            jsonStart,
                            jsonEnd + 1,
                        )
                    )

                return buildList {

                    for (
                        i in
                        0 until array.length()
                    ) {

                        val suggestion =
                            array
                                .optString(i)
                                .trim()

                        if (
                            suggestion.isNotEmpty()
                        ) {
                            add(suggestion)
                        }

                        if (
                            size >= limit
                        ) {
                            break
                        }
                    }
                }

            } catch (
                _: Exception
            ) {
                // Fall through to defensive parser.
            }
        }

        return trimmed
            .split(
                ",",
                "\n",
            )
            .map {
                it
                    .trim()
                    .trim(
                        '"',
                        '\'',
                        '.',
                        '[',
                        ']',
                    )
            }
            .filter {
                it.isNotEmpty()
            }
            .distinct()
            .take(limit)
    }
}
