package com.example.ai.query

import com.example.BuildConfig
import com.example.ai.model.Direction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Gemini-powered query parser. Invoked only when LocalQueryParser
 * cannot confidently parse a transcript.
 *
 * Returns null on any failure (network, API error, malformed response,
 * empty API key). The caller routes nulls to Failed(QueryParseFailed).
 */
class GeminiQueryParser(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build(),
    private val apiKeyProvider: () -> String = { BuildConfig.GEMINI_API_KEY }
) {

    private val endpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val systemPrompt = """
        You are a query parser for an Indian accounting app.
        Parse the user's spoken query into a structured JSON object.

        Output schema (return ONLY this JSON, no markdown, no commentary):
        {
          "entity": string | null,
          "direction": "INCOME" | "EXPENSE" | null,
          "category": "food" | "rent" | "shopping" | null,
          "timeToken": "TODAY" | "YESTERDAY" | "THIS_WEEK" | "LAST_WEEK" | "THIS_MONTH" | "LAST_MONTH" | "LIFETIME" | "CUSTOM",
          "customStartDate": "YYYY-MM-DD" | null,
          "customEndDate": "YYYY-MM-DD" | null
        }

        Rules:
        - "kitna diya / spent / paid / kharch" → direction = EXPENSE
        - "kitna liya / received / mila / kamai / aaya" → direction = INCOME
        - "balance / hisab / baki / bacha" → direction = null (net balance)
        - Specific date range like "15 January se 20 March" → CUSTOM with both dates
        - No time mentioned → timeToken = LIFETIME
        - Entity names: extract the proper noun, lowercase, single token preferred
        - Category: "khana"→food, "kiraya"→rent, "kharidari"→shopping, else null
    """.trimIndent()

    suspend fun parse(transcript: String): GeminiQueryParseResult? = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") return@withContext null

        val requestBody = buildRequestBody(transcript)
        val request = Request.Builder()
            .url("${"$"}endpoint?key=${"$"}apiKey")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val responseBody = response.body?.string() ?: return@withContext null
                return@withContext parseResponse(responseBody)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun buildRequestBody(transcript: String): JSONObject {
        return JSONObject().apply {
            put("contents", org.json.JSONArray().put(
                JSONObject().put("parts", org.json.JSONArray().put(
                    JSONObject().put("text", "${"$"}systemPrompt\n\nUser query: \"${"$"}transcript\"")
                ))
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("responseMimeType", "application/json")
                put("thinkingConfig", JSONObject().put("thinkingLevel", "medium"))
            })
        }
    }

    private fun parseResponse(body: String): GeminiQueryParseResult? {
        return try {
            val root = JSONObject(body)
            val text = root.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            val parsed = JSONObject(text)

            GeminiQueryParseResult(
                entity = parsed.optString("entity").takeIf { it.isNotBlank() && it != "null" },
                direction = parsed.optString("direction")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?.let { runCatching { Direction.valueOf(it) }.getOrNull() },
                categoryHint = parsed.optString("category").takeIf { it.isNotBlank() && it != "null" },
                timeToken = parsed.optString("timeToken")
                    .takeIf { it.isNotBlank() }
                    ?.let { runCatching { TimeToken.valueOf(it) }.getOrNull() }
                    ?: TimeToken.LIFETIME,
                customStartDate = parsed.optString("customStartDate").takeIf { it.isNotBlank() && it != "null" },
                customEndDate = parsed.optString("customEndDate").takeIf { it.isNotBlank() && it != "null" }
            )
        } catch (e: Exception) {
            null
        }
    }
}

data class GeminiQueryParseResult(
    val entity: String?,
    val direction: Direction?,
    val categoryHint: String?,
    val timeToken: TimeToken,
    val customStartDate: String?,
    val customEndDate: String?
)
