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
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PartialExtractionException(
    val amount: Double?,
    val direction: Direction?,
    cause: String?
) : Exception(cause)

/**
 * Extracts structured transaction data from a transcript using Gemini Flash.
 * Uses a 3-second call timeout and falls back with partial data on JSON failure.
 */
class GeminiTransactionExtractor : TransactionExtractor {

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(3, TimeUnit.SECONDS)
        .build()

    private val systemInstruction = """
        You are a precise transaction extraction engine for an Indian accounting app.
        Extract structured data from the user's spoken transaction in English, Hindi (Devanagari or romanized), or Hinglish.

        Compound Hindi numbers — always resolve to a single numeric value:
        "do sau"/"दो सौ" = 200, "teen sau" = 300, "panch sau"/"पाँच सौ" = 500,
        "do hazaar"/"दो हज़ार" = 2000, "panch hazaar" = 5000, "do lakh" = 200000.

        Return ONLY a valid JSON object. No prose. No markdown. No code fences. Keep every string field under 80 characters.
        The JSON must have exactly these keys:
        {
          "amount": number or null,
          "direction": "INCOME" | "EXPENSE" | "CREDIT" | "DEBIT" or null,
          "entity": string or null,
          "category": "general" | "food" | "rent" | "shopping" or null,
          "paymentMethod": "cash" | "upi" | "bank" or null,
          "notes": string or null
        }

        Direction rules:
        - EXPENSE: money you paid or spent ("diya", "paid", "gave", "kharch", "khaya", "piya", "kharida", "le gya", "bought").
        - INCOME: money you received or earned ("mila", "received", "got", "aaya", "milega").
        - CREDIT: someone will give YOU money later ("dega", "denge", "baad mein dega", "udhaar diya", "next week dega") — record as receivable.
        - DEBIT: you will give someone money later ("lunga", "le lunga", "baad mein dunga", "kalunga") — record as payable.
        - TRANSFER: moving money between your own accounts only.

        Entity rules:
        - If a person or shop is mentioned: use their name (e.g. "Chotu Painter", "DMart", "Ramesh").
        - If no person or shop: use the item or service consumed (e.g. "Pizza", "Chai", "Petrol", "Milk", "Khichdi").
        - NEVER return null for entity. Always extract something meaningful from the sentence.

        Category rules:
        - chai/lunch/pizza/tea/khana/food/grocery/khichdi/dosa/biryani/snack/sabzi/roti/dal → food
        - rent/kiraya/makaan → rent
        - clothes/kapde/shopping/items/jeans/shirt → shopping
        - Everything else → general

        Payment method:
        - cash/nakad/naqad → cash; phonepe/gpay/paytm/upi/online → upi; bank/neft/imps/account → bank.
        - Default to cash if unclear.

        Examples (input → JSON):
        - "do sau rupaye khane pe laga" → {"amount":200,"direction":"EXPENSE","entity":"Khana","category":"food","paymentMethod":"cash","notes":null}
        - "Ramesh ko panch hazaar diya" → {"amount":5000,"direction":"EXPENSE","entity":"Ramesh","category":"general","paymentMethod":"cash","notes":null}
        - "salary mili 45000" → {"amount":45000,"direction":"INCOME","entity":"Salary","category":"general","paymentMethod":"bank","notes":null}

        Notes:
        - Include any deferred timing ("next week", "kal dega"), purpose, or extra context. Max 80 chars. Null if nothing to add.
        - amount: numeric value only, no currency symbols or units.
    """.trimIndent()

    override suspend fun extract(
        normalizedTranscript: String,
        originalTranscript: String
    ): CandidateTransaction? = withContext(Dispatchers.IO) {
        try {
            val apiKey = com.example.BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                throw Exception("API Key is missing. Please configure GEMINI_API_KEY.")
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=$apiKey"

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
                    put("maxOutputTokens", 300)
                    put("responseMimeType", "application/json")
                })
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(requestBody).build()
            val response = httpClient.newCall(request).execute()
            val responseText = response.body?.string() ?: ""

            Log.d("GeminiExtract", "Raw response: $responseText")

            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(responseText).getJSONObject("error").optString("message", "HTTP ${response.code}")
                } catch (e: Exception) {
                    "HTTP ${response.code}: ${response.message}"
                }
                throw Exception("API Error: $errorMsg")
            }

            if (responseText.isEmpty()) throw Exception("Server returned empty response")

            val jsonResponse = JSONObject(responseText)
            if (jsonResponse.has("error")) {
                throw Exception("API Error: ${jsonResponse.getJSONObject("error").optString("message", "Unknown")}")
            }

            val candidates = jsonResponse.optJSONArray("candidates")
                ?: throw Exception("Invalid response: candidates list missing")
            if (candidates.length() == 0) throw Exception("Response has 0 candidates")

            val parts = candidates.getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts") ?: throw Exception("Invalid response: content parts missing")
            if (parts.length() == 0) throw Exception("Response has 0 content parts")

            var rawText = parts.getJSONObject(0).optString("text", "").trim()
            if (rawText.isEmpty()) throw Exception("Response content text block is empty")

            rawText = rawText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

            val parsed = try {
                JSONObject(rawText)
            } catch (e: JSONException) {
                // Partial extraction from the malformed response before re-throwing
                val partialAmount = Regex(""""amount"\s*:\s*(\d+(?:\.\d+)?)""")
                    .find(rawText)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                val partialDirection = Regex(""""direction"\s*:\s*"(INCOME|EXPENSE|CREDIT|DEBIT)"""")
                    .find(rawText)?.groupValues?.getOrNull(1)
                    ?.let { Direction.fromString(it) }
                Log.w("GeminiExtract", "JSON parse failed; partial amount=$partialAmount direction=$partialDirection")
                throw PartialExtractionException(partialAmount, partialDirection, e.message)
            }

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
        } catch (e: PartialExtractionException) {
            throw e
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
