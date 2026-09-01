package com.personal.sinhalakeyboard

import org.json.JSONObject

/** Shared OpenRouter request/response helpers for all AI features. */
object OpenRouterHelper {

    /** Gemini 3 models default to heavy reasoning — keep keyboard calls fast and text-only. */
    fun applyMinimalReasoning(body: JSONObject) {
        body.put("reasoning", JSONObject().put("effort", "minimal"))
    }

    fun extractAssistantText(responseBody: String): String {
        val json = JSONObject(responseBody)
        val message = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val content = message.optString("content").trim()
        if (content.isNotEmpty()) return content
        return message.optString("refusal").trim()
    }
}
