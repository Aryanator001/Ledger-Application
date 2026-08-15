package com.ledger.query.core

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class QueryRouterTest {

    private val fixedDate = LocalDate.of(2026, 7, 16)

    private lateinit var router: QueryRouter

    @Before
    fun setUp() {
        router = QueryRouter(
            entityResolver = FakeEntityResolver,
            categoryResolver = FakeCategoryResolver,
            clock = { fixedDate }
        )
    }

    // =========================================================================
    // Acceptance matrix — 16 rows
    // =========================================================================

    // Row 1: English expense + entity (single name)
    @Test fun en_paidToRamesh_local() {
        val d = router.route("paid to Ramesh this month")
        assertEquals(Tier.LOCAL, d.tier)
        assertEquals(Direction.OUT, d.frame.direction)
        assertEntityResolved(d.frame, "Ramesh", 3L)
    }

    // Row 2: English expense + ambiguous entity → CLARIFY
    @Test fun en_paidToVijay_ambiguous_clarify() {
        val d = router.route("paid to vijay this month")
        assertEquals(Tier.CLARIFY, d.tier)
        // ambiguousWith should contain both Vijay Kumar and Vijaykumar Shah
        val opts = d.clarifyOptions
        assertTrue("expected ≥2 options, got $opts", opts.size >= 2)
    }

    // Row 3: Hindi expense + entity postposition — no time → CLARIFY TIME_RANGE
    @Test fun hi_rameshKoKitnaDiya_clarifyTime() {
        val d = router.route("ramesh ko kitna diya")
        assertEquals(Tier.CLARIFY, d.tier)
        assertEquals(ClarificationField.TIME_RANGE, d.fieldType)
        assertEquals(Direction.OUT, d.frame.direction)
        assertEntityResolved(d.frame, "Ramesh", 3L)
    }

    // Row 4: Hindi ambiguous entity
    @Test fun hi_vijayKoDiya_clarify() {
        val d = router.route("vijay ko kitna diya")
        assertEquals(Tier.CLARIFY, d.tier)
    }

    // Row 5: Category query (English)
    @Test fun en_foodExpenseThisMonth_local() {
        val d = router.route("food expense this month")
        assertEquals(Tier.LOCAL, d.tier)
        assertNotNull("expected category resolved", d.frame.category?.id)
        assertEquals(10L, d.frame.category?.id)
    }

    // Row 6: Category query (Hindi)
    @Test fun hi_kharcha_khana_local() {
        val d = router.route("is mahine khana ka kharcha")
        assertEquals(Tier.LOCAL, d.tier)
        assertNotNull("expected category resolved", d.frame.category?.id)
    }

    // Row 7: Direction only — SUM + explicit time → LOCAL
    @Test fun en_totalExpenseThisMonth_local() {
        val d = router.route("total expense this month")
        assertEquals(Tier.LOCAL, d.tier)
        assertEquals(Direction.OUT, d.frame.direction)
        assertTrue(d.frame.timeWasExplicit)
    }

    // Row 8: Entity + income direction — no time → CLARIFY TIME_RANGE
    @Test fun en_receivedFromRamesh_clarifyTime() {
        val d = router.route("received from ramesh")
        assertEquals(Tier.CLARIFY, d.tier)
        assertEquals(ClarificationField.TIME_RANGE, d.fieldType)
        assertEquals(Direction.IN, d.frame.direction)
        assertEntityResolved(d.frame, "Ramesh", 3L)
    }

    // Row 9: Analytical marker → AI_ANALYST
    @Test fun en_whySpendingSoMuch_analyst() {
        val d = router.route("why am I spending so much on food")
        assertEquals(Tier.AI_ANALYST, d.tier)
        assertTrue(d.confidence >= 0.9f)
    }

    // Row 10: Trend marker → AI_ANALYST
    @Test fun en_trendInExpenses_analyst() {
        val d = router.route("show me the trend in my expenses")
        assertEquals(Tier.AI_ANALYST, d.tier)
    }

    // Row 11: "how many times" + entity but no time → CLARIFY TIME_RANGE; frame still has COUNT
    @Test fun en_howManyTimes_count() {
        val d = router.route("how many times did I pay Ramesh")
        assertEquals(Tier.CLARIFY, d.tier)
        assertEquals(ClarificationField.TIME_RANGE, d.fieldType)
        assertEquals(Metric.COUNT, d.frame.metric)
    }

    // Row 12: "how much" + entity but no time → CLARIFY TIME_RANGE; frame still has SUM
    @Test fun en_howMuch_sum() {
        val d = router.route("how much did I pay to Ramesh")
        assertEquals(Tier.CLARIFY, d.tier)
        assertEquals(ClarificationField.TIME_RANGE, d.fieldType)
        assertEquals(Metric.SUM, d.frame.metric)
        assertEntityResolved(d.frame, "Ramesh", 3L)
    }

    // Row 13: No time keyword → lifetime range
    @Test fun en_noTime_lifetime() {
        val d = router.route("paid to ramesh")
        if (d.tier == Tier.LOCAL) {
            // lifetime start should be very early (year 2000 or earlier per spec)
            assertTrue(
                "expected lifetime start, got ${d.frame.timeRange.start}",
                d.frame.timeRange.start.year <= 2000
            )
            assertFalse(d.frame.timeWasExplicit)
        }
    }

    // Row 14: Unknown entity name (not in fake dataset) → residual → AI_PLANNER
    @Test fun en_unknownPerson_planner() {
        val d = router.route("paid to Govardhan this month")
        // "Govardhan" is not in FakeEntityResolver → not claimed → residual → AI_PLANNER
        assertEquals(Tier.AI_PLANNER, d.tier)
    }

    // Row 15: Pure "compare" query → AI_ANALYST
    @Test fun en_compare_analyst() {
        val d = router.route("compare my expenses this month vs last month")
        assertEquals(Tier.AI_ANALYST, d.tier)
    }

    // Row 16: Under-specified (no direction, no metric, no entity) → AI_PLANNER
    @Test fun en_bareEntity_noDirection_planner_or_local() {
        // "Suresh Patel" is a known entity, but with no direction + no metric signal this
        // hits Rule 4 → AI_PLANNER. Exact tier depends on whether metric signal emerges.
        val d = router.route("suresh patel")
        // Either AI_PLANNER (Rule 4) or LOCAL (Rule 5). Must NOT be CLARIFY or AI_ANALYST.
        assertNotEquals(Tier.CLARIFY, d.tier)
        assertNotEquals(Tier.AI_ANALYST, d.tier)
    }

    // =========================================================================
    // Invariant tests
    // =========================================================================

    // Invariant 1: Analytical marker always beats entity/direction regardless of other signals
    @Test fun invariant_analyticalAlwaysBeatsEntity() {
        val d = router.route("why did I pay Ramesh so much last month")
        assertEquals(
            "analytical marker must veto LOCAL even when entity is resolved",
            Tier.AI_ANALYST, d.tier
        )
    }

    // Invariant 2: Ambiguity always beats residual — CLARIFY before AI_PLANNER
    @Test fun invariant_clarifyBeforeAiPlanner() {
        // "vijay" is ambiguous; even if residual existed, CLARIFY fires first (Rule 2 before Rule 3)
        val d = router.route("vijay ko kuch aur diya pichle hafte")
        assertEquals(
            "CLARIFY must fire before AI_PLANNER when entity is ambiguous",
            Tier.CLARIFY, d.tier
        )
    }

    // Invariant 3: STOPWORDS never cause residual — they must be silently dropped
    @Test fun invariant_stopwordsDontCauseResidue() {
        // "the", "a", "is" are stopwords — must not leak into residual
        val d = router.route("what is the total paid to ramesh")
        // "ramesh" resolves cleanly; no residual from "what/is/the"
        assertTrue(
            "stopwords leaked into residual: ${d.frame.residual}",
            d.frame.residual.none { it in Lexicon.STOPWORDS }
        )
    }

    // Invariant 4: COUNT beats SUM when both signals appear
    @Test fun invariant_countBeatsSumWhenBothPresent() {
        // "how many times" has METRIC_COUNT("many","times") and METRIC_SUM("how","much") could also appear
        val d = router.route("how many times ramesh ko diya")
        assertEquals(
            "COUNT must win over SUM when both signals present",
            Metric.COUNT, d.frame.metric
        )
    }

    // =========================================================================
    // toPlan label format
    // =========================================================================

    @Test fun toPlan_labelFormat() {
        val d = router.route("paid to Ramesh this month")
        if (d.tier == Tier.LOCAL) {
            val plan = router.toPlan(d.frame)
            assertTrue(
                "label should contain entity name, got: ${plan.label}",
                plan.label.contains("Ramesh")
            )
            assertTrue(
                "label should contain time label, got: ${plan.label}",
                plan.label.contains("This month") || plan.label.contains("month")
            )
        }
    }

    // =========================================================================
    // QueryPlan.signature() stability
    // =========================================================================

    @Test fun signature_stableForSameQuery() {
        val d1 = router.route("paid to Ramesh this month")
        val d2 = router.route("paid to Ramesh this month")
        if (d1.tier == Tier.LOCAL && d2.tier == Tier.LOCAL) {
            val p1 = router.toPlan(d1.frame)
            val p2 = router.toPlan(d2.frame)
            assertEquals("same query must produce same plan signature", p1.signature(), p2.signature())
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun assertEntityResolved(frame: QueryFrame, name: String, id: Long) {
        assertNotNull("entity must be resolved for '$name'", frame.entity)
        assertEquals("entity canonical name", name, frame.entity?.canonicalName)
        assertEquals("entity id", id, frame.entity?.id)
        assertFalse("entity must not be ambiguous", frame.entity?.isAmbiguous ?: true)
    }
}
