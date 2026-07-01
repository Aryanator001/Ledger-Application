package com.example.ai.query

import com.example.BuildConfig
import com.example.ai.model.Direction
import com.example.ai.model.ParsedQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Generates a natural-language one-line headline for the query answer card.
 * Returns null on any failure — caller falls back to QueryResponseFormatter.
 */
class GeminiQuerySynthesizer(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val apiKeyProvider: () -> String = { BuildConfig.GEMINI_API_KEY }
) {

    private val endpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val rupeeFormat: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
    }

    suspend fun synthesize(
        transcript: String,
        query: ParsedQuery,
        totalAmount: Double,
        transactionCount: Int,
        timeLabel: String
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") return@withContext null

        val amountText = "₹${"$"}{rupeeFormat.format(kotlin.math.abs(totalAmount))}"
        val directionLabel = when (query.direction) {
            Direction.EXPENSE -> "expense"
            Direction.INCOME -> "income"
            Direction.TRANSFER -> "transfer"
            null -> "net balance"
        }

        val prompt = """
            You are a financial summary writer for an Indian accounting app.
            The user asked: "${"$"}transcript"
            
            The system computed:
            - Amount: ${"$"}amountText
            - Direction: ${"$"}directionLabel
            - Count: ${"$"}transactionCount transactions
            - Time range: ${"$"}{timeLabel.ifBlank { "all time" }}
            - Entity: ${"$"}{query.entity ?: "none"}
            - Category: ${"$"}{query.categoryHint ?: "none"}
            - Is negative (user owes): ${"$"}{totalAmount < 0}
            
            Generate ONE LINE summary (under 80 characters) in the same language style
            as the question. Use ₹ for rupees. If entity is set and direction is null,
            phrase it as a balance ("X owes you" / "you owe X").
            
            Return ONLY the summary string. No quotes, no markdown, no extra text.
        """.trimIndent()

        val requestBody = JSONObject().apply {
            put("contents", org.json.JSONArray().put(
                JSONObject().put("parts", org.json.JSONArray().put(
                    JSONObject().put("text", prompt)
                ))
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.4)
                put("responseMimeType", "text/plain")
            })
        }

        try {
            val request = Request.Builder()
                .url("$endpoint?key=$apiKey")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val text = JSONObject(body)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("'")
                return@withContext text.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            null
        }
    }
}
