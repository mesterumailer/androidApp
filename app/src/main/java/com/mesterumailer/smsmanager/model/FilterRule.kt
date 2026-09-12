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
    val priority: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("categoryId", categoryId)
        put("displayName", displayName)
        put("senderContains", JSONArray(senderContains))
        put("requiredKeywords", JSONArray(requiredKeywords))
        put("anyKeywords", JSONArray(anyKeywords))
        put("excludedKeywords", JSONArray(excludedKeywords))
        put("priority", priority)
    }

    companion object {
        fun fromJson(json: JSONObject): FilterRule = FilterRule(
            categoryId = json.optString("categoryId"),
            displayName = json.optString("displayName"),
            senderContains = json.optJSONArray("senderContains").toStringList(),
            requiredKeywords = json.optJSONArray("requiredKeywords").toStringList(),
            anyKeywords = json.optJSONArray("anyKeywords").toStringList(),
            excludedKeywords = json.optJSONArray("excludedKeywords").toStringList(),
            priority = json.optInt("priority")
        )

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return buildList(length()) {
                for (i in 0 until length()) {
                    optString(i).trim().takeIf { it.isNotEmpty() }?.let(::add)
                }
            }
        }
    }
}
