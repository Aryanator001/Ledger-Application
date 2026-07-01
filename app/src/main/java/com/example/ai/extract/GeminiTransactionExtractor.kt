package com.example.ai.extract

import android.util.Log
import com.example.ai.model.CandidateTransaction
import com.example.ai.model.ConfidenceScore
import com.example.ai.model.Direction
import com.example.ai.model.TransactionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Extracts structured transaction data from a transcript using Gemini Flash.
 * Reuses the app's existing Gemini REST pattern (OkHttp + org.json + BuildConfig key).
 * Returns a JSON-only response parsed into a CandidateTransaction.
 */
class GeminiTransactionExtractor : TransactionExtractor {

    private val client = OkHttpClient()

    private val systemInstruction = """
        You are a precise transaction extraction engine for an Indian accounting app.
        Extract structured data from the user's spoken transaction.
        The user speaks in English, Hindi, or Hinglish (Latin script).

        Return ONLY a valid JSON object. No prose. No markdown. No code fences.
        The JSON must have exactly these keys:
        {
          "amount": number or null,
          "direction": "INCOME" | "EXPENSE" | "TRANSFER" or null,
          "entity": string or null,
          "category": "general" | "food" | "rent" | "shopping" or null,
          "paymentMethod": "cash" | "upi" | "bank" or null,
          "notes": string or null
        }

        Rules:
        - direction: money received/earned = INCOME; money paid/spent = EXPENSE; moving between own accounts = TRANSFER.
        - "diya"/"paid"/"gave"/"kharch"/"bought" => EXPENSE. "mila"/"received"/"got"/"aaya" => INCOME.
        - entity: the person, shop, or vendor name. Null if none mentioned.
        - category: pick the closest of general/food/rent/shopping. chai/lunch/tea/food/grocery => food. rent => rent. clothes/items => shopping. Otherwise general.
        - paymentMethod: cash/nakad => cash; phonepe/gpay/upi/online => upi; bank/account/neft => bank. Default cash if unclear.
        - notes: a short description of what the transaction was for, if mentioned.
        - amount: numeric value only, no currency symbols.
        - If you cannot determine a field, use null (except category/paymentMethod which default to general/cash).
    """.trimIndent()

    override suspend fun extract(
        normalizedTranscript: String,
        originalTranscript: String
    ): CandidateTransaction? = withContext(Dispatchers.IO) {
        try {
            val apiKey = com.example.BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                throw Exception("API Key is missing. Please add/configure your GEMINI_API_KEY in the AI Studio Secrets panel.")
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", normalizedTranscript) })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstruction) })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                })
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(requestBody).build()
            val response = client.newCall(request).execute()
            val responseText = response.body?.string() ?: ""

            Log.d("GeminiExtract", "Raw response: $responseText")
            
            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(responseText).getJSONObject("error").optString("message", "HTTP code: ${response.code}")
                } catch (jsonEx: Exception) {
                    "HTTP ${response.code}: ${response.message}"
                }
                throw Exception("API Error: $errorMsg")
            }

            if (responseText.isEmpty()) throw Exception("Server returned empty response")

            val jsonResponse = JSONObject(responseText)
            if (jsonResponse.has("error")) {
                val errMsg = jsonResponse.getJSONObject("error").optString("message", "API Error")
                throw Exception("API Error: $errMsg")
            }

            val candidates = jsonResponse.optJSONArray("candidates") 
                ?: throw Exception("Invalid response: candidates list missing")
            if (candidates.length() == 0) throw Exception("Response has 0 candidates")

            val parts = candidates.getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts") ?: throw Exception("Invalid response: structural part content missing")
            if (parts.length() == 0) throw Exception("Response has 0 content parts")

            var rawText = parts.getJSONObject(0).optString("text", "").trim()
            if (rawText.isEmpty()) throw Exception("Response content text block is empty")

            // Defensive: strip markdown fences if the model added them.
            rawText = rawText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

            val parsed = JSONObject(rawText)

            val amount = if (parsed.isNull("amount")) null else parsed.optDouble("amount").let { if (it.isNaN()) null else it }
            val direction = Direction.fromString(if (parsed.isNull("direction")) null else parsed.optString("direction"))
            val entity = if (parsed.isNull("entity")) null else parsed.optString("entity").trim().ifBlank { null }
            val category = if (parsed.isNull("category")) null else parsed.optString("category").trim().ifBlank { null }
            val paymentMethod = if (parsed.isNull("paymentMethod")) null else parsed.optString("paymentMethod").trim().ifBlank { null }
            val notes = if (parsed.isNull("notes")) null else parsed.optString("notes").trim().ifBlank { null }

            val confidence = computeConfidence(amount, direction, entity, category, paymentMethod)

            CandidateTransaction(
                amount = amount,
                direction = direction,
                entity = entity,
                categoryHint = category,
                paymentMethodHint = paymentMethod,
                notes = notes,
                rawTranscript = originalTranscript,
                confidence = confidence,
                source = TransactionSource.VOICE
            )
        } catch (e: Exception) {
            Log.e("GeminiExtract", "Extraction failed", e)
            throw e
        }
    }

    private fun computeConfidence(
        amount: Double?,
        direction: Direction?,
        entity: String?,
        category: String?,
        paymentMethod: String?
    ): ConfidenceScore {
        val amountScore = if (amount != null && amount > 0) 1f else 0f
        val directionScore = if (direction != null) 1f else 0f
        val entityScore = if (!entity.isNullOrBlank()) 1f else 0f
        val categoryScore = if (!category.isNullOrBlank()) 0.8f else 0.3f
        val paymentScore = if (!paymentMethod.isNullOrBlank()) 0.8f else 0.3f

        // Overall weighted toward the critical fields (amount, direction, entity).
        val overall = (amountScore * 0.4f) + (directionScore * 0.3f) +
                (entityScore * 0.2f) + (categoryScore * 0.05f) + (paymentScore * 0.05f)

        return ConfidenceScore(
            overall = overall,
            amount = amountScore,
            direction = directionScore,
            entity = entityScore,
            category = categoryScore,
            paymentMethod = paymentScore,
            transcriptQuality = 0.7f
        )
    }
}
