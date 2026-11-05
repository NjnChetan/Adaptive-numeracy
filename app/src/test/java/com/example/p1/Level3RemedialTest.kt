package com.example.p1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Level 3 × and ÷: KCs below the level that the boundary assessment finds
 * unsolvable must be practised first (remedial), instead of being treated as mastered.
 */
class Level3RemedialTest {

    /** Runs the assessment; the simulated student answers correctly only for KCs in [knows]. */
    private fun runAssessment(engine: AdaptiveEngine, knows: Set<Int>) {
        var guard = 0
        while (engine.currentPhase == AdaptiveEngine.Phase.ASSESSMENT) {
            engine.generateQuestion()
            val kc = engine.currentKCId
            engine.submitAnswer(if (kc in knows) engine.correctAnswer else engine.correctAnswer + 1)
            check(++guard < 50) { "assessment did not finish" }
        }
    }

    /** Answers practice questions correctly until [kcId] is mastered. */
    private fun masterNext(engine: AdaptiveEngine, kcId: Int) {
        repeat(5) {
            engine.generateQuestion()
            assertEquals("practice KC", kcId, engine.currentKCId)
            engine.submitAnswer(engine.correctAnswer)
        }
    }

    private fun firstPracticeKC(engine: AdaptiveEngine): Int {
        engine.generateQuestion()
        return engine.currentKCId
    }

    @Test
    fun mul_L3_knowsNothing_startsAtT5() {
        val e = AdaptiveEngine().apply { startSession("×", 3) }
        runAssessment(e, emptySet())
        assertEquals(18, firstPracticeKC(e))
        assertTrue("T5" in e.activeConceptsNames)
        assertFalse(e.consumeAllMastered())
    }

    @Test
    fun div_L3_knowsNothing_starts_at_2D1() {
        val e = AdaptiveEngine().apply { startSession("÷", 3) }
        runAssessment(e, emptySet())
        assertEquals(29, firstPracticeKC(e))
    }

    @Test
    fun mul_L3_knowsUpTo3M1_practises3M1C_then3M1C2_then2M2() {
        val e = AdaptiveEngine().apply { startSession("×", 3) }
        runAssessment(e, (18..22).toSet())       // path 0111 → unsolvable 23..28
        masterNext(e, 23)                          // 3M1C
        masterNext(e, 24)                          // 3M1C2
        assertEquals(25, firstPracticeKC(e))       // 2M2 unlocks only now
    }

    @Test
    fun mul_L3_knowsAllOfLevel2_noRemedial_startsAt2M2() {
        val e = AdaptiveEngine().apply { startSession("×", 3) }
        runAssessment(e, (18..24).toSet())
        assertEquals(25, firstPracticeKC(e))
        assertEquals(listOf("2M2", "2M2C", "3M2", "3M2C"), e.activeConceptsNames)
    }

    @Test
    fun div_L3_knowsUpTo3D1R_practises4D1R_before4D2R() {
        val e = AdaptiveEngine().apply { startSession("÷", 3) }
        // knows everything except 4D1R (34) and 4D2R (37)
        runAssessment(e, setOf(29, 30, 31, 32, 33, 35, 36))
        assertTrue("4D1R" in e.activeConceptsNames)
        masterNext(e, 34)
        assertEquals(37, firstPracticeKC(e))
    }

    @Test
    fun mul_L3_knowsEverything_provesAllMastered() {
        val e = AdaptiveEngine().apply { startSession("×", 3) }
        runAssessment(e, (18..28).toSet())
        assertTrue(e.assessmentProvedAllMastered)
        assertTrue(e.consumeAllMastered())
    }

    @Test
    fun remedial_isClearedOnNewSession() {
        val e = AdaptiveEngine().apply { startSession("×", 3) }
        runAssessment(e, emptySet())
        e.startSession("×", 3)
        assertEquals(listOf("2M2", "2M2C", "3M2", "3M2C"), e.activeConceptsNames)
    }

    @Test
    fun add_L3_unchanged_knowsNothing_startsAt1A() {
        val e = AdaptiveEngine().apply { startSession("+", 3) }
        runAssessment(e, emptySet())
        assertEquals(1, firstPracticeKC(e))
    }
}
