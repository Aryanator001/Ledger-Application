package com.ledger.query.ai

import com.ledger.query.core.Direction
import com.ledger.query.core.GroupBy
import com.ledger.query.core.Metric
import com.ledger.query.core.QueryFrame
import com.ledger.query.core.QueryPlan
import com.ledger.query.exec.LedgerLog
import com.ledger.query.exec.PlanResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────
// Public interfaces
// ─────────────────────────────────────────────────────────────

data class SchemaHint(
    val knownEntities: List<String>,
    val knownCategories: List<String>,
    val today: LocalDate
)

interface AiPlanner {
    suspend fun plan(frame: QueryFrame, schema: SchemaHint): PlanResult?
}

// ─────────────────────────────────────────────────────────────
// GeminiPlannerImpl
// ─────────────────────────────────────────────────────────────

class GeminiPlannerImpl(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build(),
    private val apiKeyProvider: () -> String
) : AiPlanner {

    private val endpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    private val systemPrompt = """
You translate shopkeeper accounting questions into a JSON query plan.
You do NOT answer questions. You do NOT compute or invent amounts.
Output ONLY raw JSON. No markdown, no backticks, no explanation.

Schema:
{
  "direction": "OUT" | "IN" | "NET",
  "metric": "SUM" | "COUNT" | "AVG" | "MAX" | "MIN" | "LIST",
  "group_by": "NONE" | "ENTITY" | "CATEGORY" | "DAY" | "WEEK" | "MONTH",
  "entity": string | null,
  "category": string | null,
  "start": "YYYY-MM-DD",
  "end": "YYYY-MM-DD",
  "limit": number | null
}

Rules:
- direction OUT = money the shopkeeper paid out. IN = money received.
- "entity" MUST be copied exactly from the provided entity list, or null.
- "category" MUST be copied exactly from the provided category list, or null.
- If no time period is mentioned, use start "1970-01-01" and end = today.
- Languages: English, Hindi (roman + devanagari), Gujarati. Code-mixing is normal.
- If the question cannot be expressed in this schema, output exactly: {"error":"unsupported"}
""".trimIndent()

    override suspend fun plan(frame: QueryFrame, schema: SchemaHint): PlanResult? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val apiKey = apiKeyProvider()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") return@withContext null

            val entityList = schema.knownEntities.take(60).joinToString(", ")
            val categoryList = schema.knownCategories.joinToString(", ")

            val userPrompt = buildString {
                append("Question: ${frame.rawQuery}\n")
                append("Today: ${schema.today}\n")
                append("Entities: $entityList\n")
                append("Categories: $categoryList")
                if (frame.residual.isNotEmpty()) append("\nUnclassified words: ${frame.residual.joinToString(", ")}")
            }

            val requestJson = JSONObject().apply {
                put("contents", org.json.JSONArray().put(
                    JSONObject().put("parts", org.json.JSONArray().put(
                        JSONObject().put("text", "$systemPrompt\n\n$userPrompt")
                    ))
                ))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
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
                    val text = JSONObject(body)
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                        .trim()
                    parseAndValidate(text, schema)
                }
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Parse + validate the raw JSON string from Gemini.
     * This is the security boundary: entity/category names not in schema are set to null.
     * Any exception → null (never throws).
     * Internal visibility for unit-testing without a network mock.
     */
    internal fun parseAndValidate(json: String, schema: SchemaHint): PlanResult? {
        return try {
            // Strip markdown fences if present
            val cleaned = json.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```")
                .trim()

            val obj = JSONObject(cleaned)

            // {"error":"..."} → unsupported
            if (obj.has("error")) return null

            val direction = obj.optString("direction", "OUT")
                .let { runCatching { Direction.valueOf(it) }.getOrElse { Direction.OUT } }

            val metric = obj.optString("metric", "SUM")
                .let { runCatching { Metric.valueOf(it) }.getOrElse { Metric.SUM } }

            val groupBy = obj.optString("group_by", "NONE")
                .let { runCatching { GroupBy.valueOf(it) }.getOrElse { GroupBy.NONE } }

            // Entity: must be in known list — hallucinated names are dropped
            val rawEntity = obj.optString("entity").takeIf { it.isNotBlank() && it != "null" }
            val validatedEntity = rawEntity?.let { name ->
                schema.knownEntities.firstOrNull { it.equals(name, ignoreCase = true) }
                    ?: run {
                        LedgerLog.w("GeminiPlanner", "Hallucinated entity '$name' not in schema — dropping")
                        null
                    }
            }

            // Category: must be in known list
            val rawCategory = obj.optString("category").takeIf { it.isNotBlank() && it != "null" }
            val validatedCategory = rawCategory?.let { name ->
                schema.knownCategories.firstOrNull { it.equals(name, ignoreCase = true) }
                    ?: run {
                        LedgerLog.w("GeminiPlanner", "Hallucinated category '$name' not in schema — dropping")
                        null
                    }
            }

            val startStr = obj.optString("start", "1970-01-01")
            val endStr = obj.optString("end", schema.today.toString())

            val start = runCatching { LocalDate.parse(startStr) }.getOrElse { LocalDate.of(1970, 1, 1) }
            val end = runCatching { LocalDate.parse(endStr) }.getOrElse { schema.today }

            // Reject inverted date ranges
            if (end < start) {
                LedgerLog.w("GeminiPlanner", "Inverted date range $start..$end — rejecting")
                return null
            }

            val limit = obj.optInt("limit", -1).takeIf { it in 1..500 }

            val entityId = if (validatedEntity != null) {
                (schema.knownEntities.indexOfFirst { it.equals(validatedEntity, ignoreCase = true) }
                    .takeIf { it >= 0 } ?: 0).toLong() + 1L
            } else null

            val categoryId = if (validatedCategory != null) {
                (schema.knownCategories.indexOfFirst { it.equals(validatedCategory, ignoreCase = true) }
                    .takeIf { it >= 0 } ?: 0).toLong() + 100L
            } else null

            val plan = QueryPlan(
                direction = direction,
                metric = metric,
                groupBy = groupBy,
                entityId = entityId,
                categoryId = categoryId,
                start = start,
                end = end,
                limit = limit,
                label = buildLabel(direction, validatedEntity, validatedCategory, start, end)
            )

            PlanResult(
                plan = plan,
                entityName = validatedEntity,
                categoryDbId = validatedCategory?.lowercase()
            )
        } catch (e: Exception) {
            LedgerLog.e("GeminiPlanner", "parseAndValidate failed", e)
            null
        }
    }

    private fun buildLabel(
        direction: Direction,
        entity: String?,
        category: String?,
        start: LocalDate,
        end: LocalDate
    ): String {
        val parts = mutableListOf<String>()
        parts += when (direction) {
            Direction.OUT -> "Paid"
            Direction.IN -> "Received"
            Direction.NET -> "Balance"
            Direction.UNSPECIFIED -> "Activity"
        }
        entity?.let { parts += it }
        category?.let { parts += it }
        parts += "$start to $end"
        return parts.joinToString(" · ")
    }
}
