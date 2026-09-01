package com.personal.sinhalakeyboard

import org.json.JSONArray
import org.json.JSONObject

/** Shared OpenRouter request/response helpers for all AI features. */
object OpenRouterHelper {

    /**
     * OpenRouter now defaults Gemini 3 to "thinking" mode, which can leave [message.content]
     * empty on simple keyboard calls. Keep effort minimal and exclude reasoning from the reply
     * so the answer stays in content like before.
     */
    fun applyModelOptions(body: JSONObject, model: String) {
        if (!isGemini3Family(model)) return
        body.put(
            "reasoning",
            JSONObject().apply {
                put("effort", "minimal")
                put("exclude", true)
            },
        )
    }

    private fun isGemini3Family(model: String): Boolean {
        val id = model.substringAfter('/')
        return id.startsWith("gemini-3") || id.startsWith("gemini-3.")
    }

    fun extractAssistantText(responseBody: String): String {
        val json = JSONObject(responseBody)
        val choice = json.getJSONArray("choices").getJSONObject(0)
        val message = choice.getJSONObject("message")

        message.optString("content").trim().takeIf { it.isNotEmpty() }?.let { return it }

        message.optString("reasoning").trim().takeIf { it.isNotEmpty() }?.let { return it }

        extractFromReasoningDetails(message.optJSONArray("reasoning_details"))
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        message.optString("refusal").trim().takeIf { it.isNotEmpty() }?.let { return it }

        // Some Gemini 3 error responses have null content and no reasoning text.
        val finishReason = choice.optString("finish_reason")
        if (finishReason == "error") {
            val nativeReason = choice.optString("native_finish_reason")
            if (nativeReason.isNotEmpty()) return ""
        }
        return ""
    }

    private fun extractFromReasoningDetails(details: JSONArray?): String? {
        if (details == null || details.length() == 0) return null
        val parts = buildList {
            for (i in 0 until details.length()) {
                val item = details.optJSONObject(i) ?: continue
                val text = item.optString("text").trim()
                if (text.isNotEmpty()) {
                    add(text)
                    continue
                }
                val content = item.optString("content").trim()
                if (content.isNotEmpty()) add(content)
            }
        }
        return parts.joinToString("\n").trim().ifEmpty { null }
    }

    /** Turn OpenRouter HTTP failures into short user-facing messages. */
    fun userMessageForFailure(raw: String): String {
        return when {
            raw.contains("401") || raw.contains("User not found", ignoreCase = true) ->
                "Invalid OpenRouter API key — check Settings"
            raw.contains("402") || raw.contains("Insufficient credits", ignoreCase = true) ->
                "OpenRouter credits exhausted — add funds at openrouter.ai"
            raw.contains("429") ->
                "OpenRouter rate limit — try again in a moment"
            raw.contains("empty content", ignoreCase = true) ->
                "AI returned no text — check OpenRouter credits and try again"
            else -> "OpenRouter request failed — check key, credits, and internet"
        }
    }
}
