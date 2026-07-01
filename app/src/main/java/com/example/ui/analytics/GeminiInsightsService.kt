package com.example.ui.analytics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiInsightsService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val modelUrl =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-pro:generateContent"

    suspend fun generateInsight(
        totalIncomePaise: Long,
        totalExpensePaise: Long,
        netPaise: Long,
        topCategories: List<CategorySummary>,
        periodLabel: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = com.example.BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw Exception("API key missing")
        }

        val catText = topCategories.take(5).joinToString(", ") {
            "${it.name}: ₹${it.totalPaise / 100}"
        }
        val netWord = if (netPaise >= 0) "profit" else "loss"
        val prompt = """
            Financial summary for $periodLabel:
            Income: ₹${totalIncomePaise / 100}
            Expense: ₹${totalExpensePaise / 100}
            Net $netWord: ₹${Math.abs(netPaise) / 100}
            Top spending categories: $catText

            Write 2–3 concise sentences of plain-English financial insight for an Indian user.
            Be specific about the numbers. No markdown, no bullet points.
        """.trimIndent()

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 250)
            })
        }

        val body = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$modelUrl?key=$apiKey")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        val responseText = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val msg = try {
                JSONObject(responseText).getJSONObject("error").optString("message", "${response.code}")
            } catch (_: Exception) { "${response.code}" }
            throw Exception("Gemini error: $msg")
        }

        val json = JSONObject(responseText)
        val parts = json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
        parts.getJSONObject(0).getString("text").trim()
    }
}

data class CategorySummary(val name: String, val totalPaise: Long)
