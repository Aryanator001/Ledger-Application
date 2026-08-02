package com.ledger.query.core

import java.time.LocalDate

class QueryRouter(
    private val entityResolver: EntityResolver,
    private val categoryResolver: CategoryResolver,
    private val clock: () -> LocalDate = { LocalDate.now() }
) {

    interface EntityResolver {
        fun resolve(surfaceForm: String): EntityRef
        /** Must be O(1) in-memory — no DB call. */
        fun couldBeEntity(token: String): Boolean
    }

    interface CategoryResolver {
        fun resolve(surfaceForm: String): CategoryRef
        fun couldBeCategory(token: String): Boolean
    }

    // -------------------------------------------------------------------------
    // Public API — stateless; safe to call concurrently
    // -------------------------------------------------------------------------

    fun route(rawQuery: String): RouteDecision {
        val frame = buildFrame(rawQuery)

        val (tier, confidence, reason, options) = decide(frame)
        return RouteDecision(tier, confidence, reason, frame, options)
    }

    fun toPlan(frame: QueryFrame): QueryPlan {
        val dir = if (frame.direction == Direction.UNSPECIFIED) Direction.OUT else frame.direction
        val label = buildList<String> {
            add(when (dir) {
                Direction.OUT -> "Paid"
                Direction.IN -> "Received"
                Direction.NET -> "Balance"
                Direction.UNSPECIFIED -> "Activity"
            })
            frame.entity?.canonicalName?.let { add(it) }
            frame.category?.canonicalName?.let { add(it) }
            add(frame.timeRange.label)
        }.joinToString(" · ")

        return QueryPlan(
            direction = dir,
            metric = frame.metric,
            groupBy = frame.groupBy,
            entityId = frame.entity?.id,
            categoryId = frame.category?.id,
            start = frame.timeRange.start,
            end = frame.timeRange.end,
            label = label
        )
    }

    // -------------------------------------------------------------------------
    // Decision — evaluate rules in strict order
    // -------------------------------------------------------------------------

    private data class Decision(
        val tier: Tier,
        val confidence: Float,
        val reason: String,
        val options: List<String> = emptyList()
    )

    private fun decide(frame: QueryFrame): Decision {
        // Rule 1: analytical markers veto everything — reasoning beats retrieval
        if (frame.analyticalMarkers.isNotEmpty()) {
            return Decision(
                Tier.AI_ANALYST, 0.9f,
                "analytical intent detected: ${frame.analyticalMarkers}"
            )
        }

        // Rule 2: ambiguous entity — never guess whose money this is
        if (frame.entity?.isAmbiguous == true) {
            return Decision(
                Tier.CLARIFY, 1.0f,
                "entity '${frame.entity.surfaceForm}' is ambiguous",
                frame.entity.ambiguousWith
            )
        }

        // Rule 3: unclaimed content words — we didn't understand everything
        if (frame.residual.isNotEmpty()) {
            return Decision(
                Tier.AI_PLANNER, 0.75f,
                "unclaimed content words: ${frame.residual}"
            )
        }

        // Rule 4: no direction and no metric signal — under-specified
        if (frame.direction == Direction.UNSPECIFIED && !frame.hasMetricSignal) {
            return Decision(
                Tier.AI_PLANNER, 0.5f,
                "direction unspecified and no metric signal"
            )
        }

        // Rule 5: route locally
        var conf = 0.95f
        if (frame.direction == Direction.UNSPECIFIED) conf -= 0.15f
        val entConf = frame.entity?.confidence ?: 1.0f
        if (entConf < 0.9f) conf -= 0.10f
        return Decision(Tier.LOCAL, conf, "all tokens accounted for")
    }

    // -------------------------------------------------------------------------
    // Frame builder — all mutable state is local to this function
    // -------------------------------------------------------------------------

    private fun buildFrame(rawQuery: String): QueryFrame {
        val today = clock()

        // 1. Normalize
        val normalized = rawQuery.lowercase()
            .replace(PUNCTUATION_RE, " ")
            .trim()
            .replace(WHITESPACE_RE, " ")

        // 2. Tokenize
        val tokens = normalized.split(" ").filter { it.isNotBlank() }
        val claimed = BooleanArray(tokens.size) { false }
        val consumed = mutableMapOf<String, String>()

        // ── Step a: Analytical markers — record but DO NOT claim ─────────────
        val analyticalMarkers = mutableListOf<String>()
        // scan unigrams
        for (i in tokens.indices) {
            if (tokens[i] in Lexicon.ANALYTICAL_MARKERS) analyticalMarkers += tokens[i]
        }
        // scan bigrams (e.g. "other than", "except for")
        for (i in 0..tokens.size - 2) {
            val bigram = "${tokens[i]} ${tokens[i + 1]}"
            if (bigram in Lexicon.ANALYTICAL_MARKERS) analyticalMarkers += bigram
        }

        // ── Step b: Time ──────────────────────────────────────────────────────
        val timeMatch = TimeExpressionParser.parse(tokens, today)
        var timeRange = TimeRange.lifetime(today)
        var timeWasExplicit = false
        if (timeMatch != null) {
            timeRange = timeMatch.range
            timeWasExplicit = true
            timeMatch.consumedIndices.forEach { idx ->
                if (idx < tokens.size) {
                    claimed[idx] = true
                    consumed[tokens[idx]] = "TIME"
                }
            }
        }

        // ── Step c: Direction — first unclaimed hit wins ──────────────────────
        var direction = Direction.UNSPECIFIED
        for (i in tokens.indices) {
            if (claimed[i]) continue
            val t = tokens[i]
            val dir = when {
                t in Lexicon.DIRECTION_OUT -> Direction.OUT
                t in Lexicon.DIRECTION_IN  -> Direction.IN
                t in Lexicon.DIRECTION_NET -> Direction.NET
                else -> null
            }
            if (dir != null) {
                direction = dir
                claimed[i] = true
                consumed[t] = "DIRECTION"
                break
            }
        }

        // ── Step d: Metric — scan all; COUNT beats SUM ───────────────────────
        var metric = Metric.SUM
        var hasMetricSignal = false
        val metricHits = mutableSetOf<Metric>()
        for (i in tokens.indices) {
            if (claimed[i]) continue
            val t = tokens[i]
            val m = when {
                t in Lexicon.METRIC_COUNT -> Metric.COUNT
                t in Lexicon.METRIC_AVG   -> Metric.AVG
                t in Lexicon.METRIC_MAX   -> Metric.MAX
                t in Lexicon.METRIC_MIN   -> Metric.MIN
                t in Lexicon.METRIC_LIST  -> Metric.LIST
                t in Lexicon.METRIC_SUM   -> Metric.SUM
                else -> null
            }
            if (m != null) {
                metricHits += m
                hasMetricSignal = true
                claimed[i] = true
                consumed[t] = "METRIC"
            }
        }
        // Priority: COUNT > LIST > AVG > MAX > MIN > SUM
        metric = when {
            Metric.COUNT in metricHits -> Metric.COUNT
            Metric.LIST  in metricHits -> Metric.LIST
            Metric.AVG   in metricHits -> Metric.AVG
            Metric.MAX   in metricHits -> Metric.MAX
            Metric.MIN   in metricHits -> Metric.MIN
            else -> Metric.SUM
        }

        // ── Step e: GroupBy ───────────────────────────────────────────────────
        var groupBy = GroupBy.NONE
        for (i in tokens.indices) {
            if (claimed[i]) continue
            val t = tokens[i]
            val gb = when {
                t in Lexicon.GROUP_BY_ENTITY   -> GroupBy.ENTITY
                t in Lexicon.GROUP_BY_CATEGORY -> GroupBy.CATEGORY
                t in Lexicon.GROUP_BY_MONTH    -> GroupBy.MONTH
                else -> null
            }
            if (gb != null) {
                groupBy = gb
                claimed[i] = true
                consumed[t] = "GROUP_BY"
                break
            }
        }

        // ── Step f: SELF_REF — claim, no data ────────────────────────────────
        for (i in tokens.indices) {
            if (claimed[i]) continue
            if (tokens[i] in Lexicon.SELF_REF) {
                claimed[i] = true
                consumed[tokens[i]] = "SELF_REF"
            }
        }

        // ── Step g: Entity — positional, not dictionary-first ─────────────────
        var entity: EntityRef? = null
        val entityIndices = mutableSetOf<Int>()

        // Hindi/Gujarati: postposition → entity is the token(s) BEFORE the marker
        outer@ for (i in tokens.indices) {
            if (claimed[i]) continue
            if (tokens[i] !in Lexicon.ENTITY_MARKER_POST) continue
            // Try multi-token name: indices i-2, i-1
            if (i >= 2 && !claimed[i - 2] && !claimed[i - 1]) {
                val joined = "${tokens[i - 2]} ${tokens[i - 1]}"
                if (entityResolver.couldBeEntity(tokens[i - 2]) &&
                    entityResolver.couldBeEntity(tokens[i - 1])
                ) {
                    val ref = entityResolver.resolve(joined)
                    if (ref.id != null || ref.isAmbiguous) {
                        entity = ref
                        entityIndices += i - 2; entityIndices += i - 1
                        break@outer
                    }
                }
            }
            // Single token before the marker
            if (i >= 1 && !claimed[i - 1] && !Lexicon.isNoise(tokens[i - 1])) {
                val surf = tokens[i - 1]
                if (entityResolver.couldBeEntity(surf)) {
                    val ref = entityResolver.resolve(surf)
                    if (ref.id != null || ref.isAmbiguous) {
                        entity = ref
                        entityIndices += i - 1
                        break@outer
                    }
                }
            }
        }

        // English: preposition → entity is the token(s) AFTER the marker
        if (entity == null) {
            outer@ for (i in tokens.indices) {
                if (claimed[i]) continue
                if (tokens[i] !in Lexicon.ENTITY_MARKER_PRE) continue
                // Try multi-token name: indices i+1, i+2
                if (i + 2 < tokens.size && !claimed[i + 1] && !claimed[i + 2]) {
                    val joined = "${tokens[i + 1]} ${tokens[i + 2]}"
                    if (entityResolver.couldBeEntity(tokens[i + 1]) &&
                        entityResolver.couldBeEntity(tokens[i + 2])
                    ) {
                        val ref = entityResolver.resolve(joined)
                        if (ref.id != null || ref.isAmbiguous) {
                            entity = ref
                            entityIndices += i + 1; entityIndices += i + 2
                            break@outer
                        }
                    }
                }
                // Single token after the marker
                if (i + 1 < tokens.size && !claimed[i + 1] && !Lexicon.isNoise(tokens[i + 1])) {
                    val surf = tokens[i + 1]
                    if (entityResolver.couldBeEntity(surf)) {
                        val ref = entityResolver.resolve(surf)
                        if (ref.id != null || ref.isAmbiguous) {
                            entity = ref
                            entityIndices += i + 1
                            break@outer
                        }
                    }
                }
            }
        }

        // Fallback: any unclaimed non-noise token where couldBeEntity() is true
        if (entity == null) {
            for (i in tokens.indices) {
                if (claimed[i] || Lexicon.isNoise(tokens[i])) continue
                // Skip tokens that are analytical markers (don't accidentally resolve them as entities)
                if (tokens[i] in Lexicon.ANALYTICAL_MARKERS) continue
                if (entityResolver.couldBeEntity(tokens[i])) {
                    val ref = entityResolver.resolve(tokens[i])
                    if (ref.id != null || ref.isAmbiguous) {
                        entity = ref
                        entityIndices += i
                        break
                    }
                }
            }
        }

        entityIndices.forEach { idx ->
            claimed[idx] = true
            consumed[tokens[idx]] = "ENTITY"
        }

        // ── Step h: Claim entity markers AFTER resolution ─────────────────────
        for (i in tokens.indices) {
            if (claimed[i]) continue
            if (tokens[i] in Lexicon.ENTITY_MARKER_PRE || tokens[i] in Lexicon.ENTITY_MARKER_POST) {
                claimed[i] = true
                consumed[tokens[i]] = "ENTITY_MARKER"
            }
        }

        // ── Step i: Category ──────────────────────────────────────────────────
        var category: CategoryRef? = null
        for (i in tokens.indices) {
            if (claimed[i]) continue
            if (categoryResolver.couldBeCategory(tokens[i])) {
                val ref = categoryResolver.resolve(tokens[i])
                if (ref.id != null) {
                    category = ref
                    claimed[i] = true
                    consumed[tokens[i]] = "CATEGORY"
                    break
                }
            }
        }

        // ── Step j: Residual — unclaimed, non-noise, non-analytical, non-integer ──
        val residual = tokens.indices
            .filter { i ->
                !claimed[i] &&
                !Lexicon.isNoise(tokens[i]) &&
                tokens[i] !in Lexicon.ANALYTICAL_MARKERS &&
                tokens[i].toIntOrNull() == null
            }
            .map { tokens[it] }

        // ── Language detection ────────────────────────────────────────────────
        val lang = detectLang(tokens)

        return QueryFrame(
            rawQuery = rawQuery,
            normalizedQuery = normalized,
            language = lang,
            direction = direction,
            metric = metric,
            groupBy = groupBy,
            entity = entity,
            category = category,
            timeRange = timeRange,
            timeWasExplicit = timeWasExplicit,
            hasMetricSignal = hasMetricSignal,
            residual = residual,
            consumed = consumed.toMap(),
            analyticalMarkers = analyticalMarkers.toList()
        )
    }

    // -------------------------------------------------------------------------
    // Language detection heuristic
    // -------------------------------------------------------------------------

    private val hindiRomanTokens = setOf(
        "ko", "se", "ne", "ka", "ki", "ke", "hai", "hain", "tha", "thi", "ho",
        "hua", "hui", "huyi", "hue", "aaj", "kal", "mahine", "hafte",
        "diya", "diye", "mila", "kitna", "kitne", "kitni", "paise",
        "meri", "mera", "mere", "maine", "mene", "main", "mai",
        "toh", "bhi", "hi", "yeh", "ye", "vo", "woh"
    )
    private val gujaratiRomanTokens = setOf(
        "aapya", "aapyu", "kamani", "aavak", "dareka", "batavo", "kem", "ketla"
    )

    private fun detectLang(tokens: List<String>): Lang {
        val hasDevanagari = tokens.any { t -> t.any { c -> c in 'ऀ'..'ॿ' } }
        val hasGujaratiScript = tokens.any { t -> t.any { c -> c in '઀'..'૿' } }
        val hasHindi = hasDevanagari || tokens.any { it in hindiRomanTokens }
        val hasGu = hasGujaratiScript || tokens.any { it in gujaratiRomanTokens }
        val hasEn = tokens.any { t ->
            t.all { c -> c.code < 128 } && t !in hindiRomanTokens && t !in gujaratiRomanTokens
        }
        return when {
            (hasHindi && hasEn) || (hasGu && hasEn) || (hasHindi && hasGu) -> Lang.MIXED
            hasDevanagari || hasHindi -> Lang.HI
            hasGujaratiScript || hasGu -> Lang.GU
            else -> Lang.EN
        }
    }

    companion object {
        private val PUNCTUATION_RE = Regex("[?!.,;:\"'₹]")
        private val WHITESPACE_RE  = Regex("\\s+")
    }
}
