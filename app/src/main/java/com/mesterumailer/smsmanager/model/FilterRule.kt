package com.mesterumailer.smsmanager.model

import org.json.JSONArray
import org.json.JSONObject

data class FilterRule(
    val categoryId: String,
    val displayName: String,
    val senderContains: List<String> = emptyList(),
    val requiredKeywords: List<String> = emptyList(),
    val anyKeywords: List<String> = emptyList(),
    val excludedKeywords: List<String> = emptyList(),
    val minimumAnyMatches: Int = 1,
    val priority: Int = 0,
    /** Explicit per-phrase score overrides, keyed by the original phrase. */
    val keywordWeights: Map<String, Int> = emptyMap(),
    /** Explicit per-sender score overrides, keyed by the original sender alias. */
    val senderWeights: Map<String, Int> = emptyMap()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("categoryId", categoryId)
        put("displayName", displayName)
        put("senderContains", JSONArray(senderContains))
        put("requiredKeywords", JSONArray(requiredKeywords))
        put("anyKeywords", JSONArray(anyKeywords))
        put("excludedKeywords", JSONArray(excludedKeywords))
        put("minimumAnyMatches", minimumAnyMatches)
        put("priority", priority)
        put("keywordWeights", keywordWeights.toJsonObject())
        put("senderWeights", senderWeights.toJsonObject())
    }

    companion object {
        fun fromJson(json: JSONObject): FilterRule = FilterRule(
            categoryId = json.optString("categoryId"),
            displayName = json.optString("displayName"),
            senderContains = json.optJSONArray("senderContains").toStringList(),
            requiredKeywords = json.optJSONArray("requiredKeywords").toStringList(),
            anyKeywords = json.optJSONArray("anyKeywords").toStringList(),
            excludedKeywords = json.optJSONArray("excludedKeywords").toStringList(),
            minimumAnyMatches = json.optInt("minimumAnyMatches", 1).coerceAtLeast(0),
            priority = json.optInt("priority"),
            keywordWeights = json.optJSONObject("keywordWeights").toIntMap(),
            senderWeights = json.optJSONObject("senderWeights").toIntMap()
        )

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return buildList(length()) {
                for (i in 0 until length()) {
                    optString(i).trim().takeIf { it.isNotEmpty() }?.let(::add)
                }
            }
        }

        private fun JSONObject?.toIntMap(): Map<String, Int> {
            if (this == null) return emptyMap()
            val result = linkedMapOf<String, Int>()
            val keys = keys()
            while (keys.hasNext()) {
                val key = keys.next().trim()
                val value = opt(key) as? Number ?: continue
                if (key.isNotBlank() && key.length <= 1000) {
                    result[key] = value.toInt().coerceIn(-20, 20)
                }
            }
            return result
        }

        private fun Map<String, Int>.toJsonObject(): JSONObject =
            JSONObject().apply { forEach { (key, value) -> put(key, value.coerceIn(-20, 20)) } }
    }
}
