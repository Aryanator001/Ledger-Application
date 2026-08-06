package com.ledger.query.ai

import com.ledger.query.core.Lang
import com.ledger.query.core.QueryFrame
import com.ledger.query.exec.QueryResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

interface AiAnalyst {
    suspend fun analyze(frame: QueryFrame, facts: List<QueryResult>): String?
}

class GeminiAnalystImpl(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val apiKeyProvider: () -> String
) : AiAnalyst {

    private val endpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"

    private val rupee = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
    }

    private val systemPrompt = """
You are an accounting assistant for an Indian small shopkeeper.
You are given VERIFIED figures already computed from the shop's database.

Rules:
- NEVER compute, estimate, or invent a number. Use only the figures given.
- If a figure needed to answer is missing, say what is missing.
- Reply in the SAME language and script the shopkeeper used.
- Be brief: 2-4 sentences. Shopkeepers are busy.
- Amounts in ₹ with Indian digit grouping (₹1,20,000 not ₹120,000).
- No markdown. Plain conversational text.
""".trimIndent()

    override suspend fun analyze(frame: QueryFrame, facts: List<QueryResult>): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val apiKey = apiKeyProvider()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") return@withContext null

            val factSheet = serializeFacts(facts)
            val langHint = when (frame.language) {
                Lang.HI -> "Hindi (roman script)"
                Lang.GU -> "Gujarati (roman script)"
                Lang.MIXED -> "Hinglish (Hindi-English mix)"
                else -> "English"
            }

            val userPrompt = """
Question: ${frame.rawQuery}
Language of question: $langHint

Verified figures from database:
$factSheet
""".trimIndent()

            val requestJson = JSONObject().apply {
                put("contents", org.json.JSONArray().put(
                    JSONObject().put("parts", org.json.JSONArray().put(
                        JSONObject().put("text", "$systemPrompt\n\n$userPrompt")
                    ))
                ))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("responseMimeType", "text/plain")
                })
            }

            try {
                val request = Request.Builder()
                    .url("$endpoint?key=$apiKey")
                    .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body?.string() ?: return@withContext null
                    JSONObject(body)
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                        .trim()
                        .takeIf { it.isNotBlank() }
                }
            } catch (e: Exception) {
                null
            }
        }

    private fun serializeFacts(facts: List<QueryResult>): String = buildString {
        facts.forEachIndexed { i, fact ->
            append("${i + 1}. ")
            when (fact) {
                is QueryResult.Scalar -> {
                    val amt = "₹${rupee.format(kotlin.math.abs(fact.amount))}"
                    append("${fact.label}: $amt (${fact.txnCount} transactions)")
                }
                is QueryResult.Grouped -> {
                    append("${fact.label}:")
                    fact.buckets.forEach { b ->
                        append("\n   ${b.key}: ₹${rupee.format(b.amount)} (${b.count} entries)")
                    }
                }
                is QueryResult.Rows -> {
                    append("${fact.label}: ${fact.transactions.size} rows")
                }
                else -> append(fact.toString())
            }
            append("\n")
        }
    }
}
