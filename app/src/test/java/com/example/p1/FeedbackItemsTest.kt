package com.example.p1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One test (or more) per item in for_test_dont_mind/test.pdf that can be checked on the JVM.
 * UI-only items (General 1–4) are checked manually on a device.
 */
class FeedbackItemsTest {

    private val N = 1000

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Builds one question for [kc] through the engine's real code path. */
    private fun q(e: AdaptiveEngine, kc: Int): List<Int> {
        val f = AdaptiveEngine::class.java.getDeclaredField("currentKC")
        f.isAccessible = true
        f.setInt(e, kc)
        val m = AdaptiveEngine::class.java.getDeclaredMethod("buildQuestionForCurrentKC")
        m.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return (m.invoke(e) as Pair<String, List<Int>>).second
    }

    private fun digits(n: Int) = n.toString().length

    private fun borrowCount(a: Int, b: Int): Int {
        var x = a; var y = b; var c = 0; var borrow = 0
        while (x > 0 || y > 0) {
            if (x % 10 - borrow < y % 10) { c++; borrow = 1 } else borrow = 0
            x /= 10; y /= 10
        }
        return c
    }

    private fun levelNames(op: String, level: Int) =
        AdaptiveEngine().apply { startSession(op, level) }.activeConceptsNames

    // ── Baseline: every KC gives correct math and 4 valid options ───────────

    @Test
    fun allKCs_correctAnswer_and_fourUniqueNonNegativeOptions() {
        val e = AdaptiveEngine()
        for (kc in 1..37) repeat(300) {
            val opts = q(e, kc)
            val a = e.lastNum1; val b = e.lastNum2
            val expected = when (KnowledgeRepository.getOperationType(kc)) {
                "-" -> a - b; "×" -> a * b; "÷" -> a / b; else -> a + b
            }
            assertEquals("KC $kc $a?$b", expected, e.correctAnswer)
            assertTrue("KC $kc negative answer", e.correctAnswer >= 0)
            assertEquals("KC $kc options $opts", 4, opts.distinct().size)
            assertTrue("KC $kc negative option $opts", opts.all { it >= 0 })
            assertEquals("KC $kc correct must appear once $opts", 1, opts.count { it == e.correctAnswer })
        }
    }

    // ── Addition / Subtraction ───────────────────────────────────────────────

    /** AS-1: 1A, 1AC, 1S never repeat a question within a 5-question window. */
    @Test
    fun AS1_noRepeatWithinFive_for_1A_1AC_1S() {
        for ((kc, commutative) in listOf(1 to true, 2 to true, 10 to false)) {
            val e = AdaptiveEngine()
            val recent = ArrayDeque<Pair<Int, Int>>()
            repeat(N) {
                q(e, kc)
                val p = e.lastNum1 to e.lastNum2
                val dup = recent.any { it == p || (commutative && it == (p.second to p.first)) }
                assertFalse("KC $kc repeated $p within last 5: $recent", dup)
                recent.addLast(p); if (recent.size > 5) recent.removeFirst()
            }
        }
    }

    /** AS-2: 3S is not in subtraction Level 2. */
    @Test
    fun AS2_3S_notInSubtractionLevel2() {
        assertFalse("3S" in levelNames("-", 2))
    }

    /** AS-3a: 3S/3SB/3SB2 use 1-, 2- and 3-digit subtrahends; 3-digit ones are ≥100 apart. */
    @Test
    fun AS3_threeDigitSubtraction_operandSizes_and_gap() {
        val e = AdaptiveEngine()
        for (kc in listOf(14, 16, 17)) {
            val lengths = mutableSetOf<Int>()
            repeat(N) {
                q(e, kc)
                val a = e.lastNum1; val b = e.lastNum2
                lengths += digits(b)
                assertEquals("KC $kc minuend $a", 3, digits(a))
                assertTrue("KC $kc $a-$b", a > b)
                if (digits(b) == 3) assertTrue("KC $kc too close $a-$b", a - b >= 100)
                val bc = borrowCount(a, b)
                when (kc) {
                    14 -> assertEquals("3S has borrow: $a-$b", 0, bc)
                    16 -> assertEquals("3SB borrow count: $a-$b", 1, bc)
                    17 -> assertTrue("3SB2 borrow count: $a-$b", bc >= 2)
                }
            }
            assertEquals("KC $kc subtrahend lengths seen", setOf(1, 2, 3), lengths)
        }
    }

    /**
     * AS-3b: 3S/3SB/3SB2 offer the "subtracted at the wrong place value" distractor
     * (a − b×10) whenever that value is valid (≥ 0).
     */
    @Test
    fun AS3_wrongPlaceValueDistractor_present() {
        val e = AdaptiveEngine()
        val report = mutableListOf<String>()
        var allOk = true
        for (kc in listOf(14, 16, 17)) {
            var eligible = 0; var shown = 0
            repeat(N) {
                val opts = q(e, kc)
                val wpv = e.lastNum1 - e.lastNum2 * 10
                if (wpv >= 0 && wpv != e.correctAnswer) {
                    eligible++
                    if (wpv in opts) shown++
                }
            }
            report += "KC $kc shown $shown/$eligible"
            if (eligible > 0 && shown != eligible) allOk = false
        }
        assertTrue("wrong-place-value distractor: $report", allOk)
    }

    /** AS-4: 2S2 operands are not very close (gap ≥ 20), no borrow. */
    @Test
    fun AS4_2S2_notTooClose() {
        val e = AdaptiveEngine()
        repeat(N) {
            q(e, 13)
            val a = e.lastNum1; val b = e.lastNum2
            assertTrue("2S2 $a-$b", digits(a) == 2 && digits(b) == 2 && a - b >= 20 && borrowCount(a, b) == 0)
        }
    }

    // ── Multiplication ───────────────────────────────────────────────────────

    /** M-1: multiplication levels. */
    @Test
    fun M1_multiplicationLevels() {
        assertEquals(listOf("T5", "T10"), levelNames("×", 1))
        assertEquals(listOf("2M1", "2M1C", "3M1", "3M1C", "3M1C2"), levelNames("×", 2))
        assertEquals(listOf("2M2", "2M2C", "3M2", "3M2C"), levelNames("×", 3))
    }

    /** M-2: no ×1 in T5, T10, 2M1, 3M1. */
    @Test
    fun M2_noMultiplyByOne() {
        val e = AdaptiveEngine()
        for (kc in listOf(18, 19, 20, 22)) repeat(N) {
            q(e, kc)
            assertTrue("KC $kc ${e.lastNum1}×${e.lastNum2}", e.lastNum1 != 1 && e.lastNum2 != 1)
        }
    }

    /** M-3: T10 never asks a T5 fact such as 5×6 (both factors 6..9). */
    @Test
    fun M3_T10_factorsSixToNine() {
        val e = AdaptiveEngine()
        repeat(N) {
            q(e, 19)
            assertTrue("T10 ${e.lastNum1}×${e.lastNum2}", e.lastNum1 in 6..9 && e.lastNum2 in 6..9)
        }
    }

    /** M-4: for 2M1..3M2C the answer cannot be picked by its last digit alone. */
    @Test
    fun M4_multiplicationDistractors_shareLastDigit() {
        val e = AdaptiveEngine()
        for (kc in 20..28) {
            var allThree = 0
            repeat(N) {
                val opts = q(e, kc)
                val c = e.correctAnswer
                val others = opts.filter { it != c }
                assertTrue("KC $kc: last digit unique, ${e.lastNum1}×${e.lastNum2} opts=$opts",
                    others.any { it % 10 == c % 10 })
                if (others.all { it % 10 == c % 10 && digits(it) == digits(c) }) allThree++
            }
            println("M4 KC $kc: all 3 distractors same last digit + length in $allThree/$N")
        }
    }

    /** M-5: 3M2 (and 2M2C/3M2C) multiplier never a multiple of 10. */
    @Test
    fun M5_multiplierNotMultipleOf10() {
        val e = AdaptiveEngine()
        for (kc in listOf(26, 27, 28)) repeat(N) {
            q(e, kc)
            assertTrue("KC $kc ${e.lastNum1}×${e.lastNum2}", e.lastNum2 % 10 != 0 && e.lastNum1 % 10 != 0)
        }
    }

    // ── Division ─────────────────────────────────────────────────────────────

    /**
     * D-2: 3D2 / 3D2R options are not "odd" (the reported case was 352÷88 with option 264 = a−b).
     * Every option must be near the quotient: |opt − q| ≤ max(10, q), and never a−b or a×b.
     * (An option equal to the divisor is allowed: e.g. 650÷26=25 legitimately offers 26 = q+1.)
     */
    @Test
    fun D2_3D2_options_nearQuotient() {
        val e = AdaptiveEngine()
        for (kc in listOf(35, 36)) repeat(N) {
            val opts = q(e, kc)
            val a = e.lastNum1; val b = e.lastNum2; val qt = e.correctAnswer
            for (o in opts) {
                assertTrue("KC $kc $a÷$b=$qt odd option $o in $opts", kotlin.math.abs(o - qt) <= maxOf(10, qt))
                assertTrue("KC $kc $a÷$b=$qt option $o is a−b or a×b", o != a - b && o != a * b)
            }
        }
    }

    // ── M-6 / D-1: boundary trees (structure) ────────────────────────────────

    private fun walk(get: (String) -> BoundaryState, ids: Set<Int>, path: String = "", out: MutableList<String>) {
        when (val s = get(path)) {
            is BoundaryState.Ask -> {
                assertTrue("$path asks ${s.kcId} outside op", s.kcId in ids)
                walk(get, ids, path + "1", out); walk(get, ids, path + "0", out)
            }
            is BoundaryState.Terminal -> {
                assertEquals("partition at '$path'", ids, s.solvable + s.unsolvable)
                assertTrue("overlap at '$path'", (s.solvable intersect s.unsolvable).isEmpty())
                // solvable set is prerequisite-closed
                for (kc in s.solvable) for (p in KnowledgeRepository.getPrerequisites(kc))
                    assertTrue("'$path': $kc solvable but prereq $p not", p in s.solvable)
                out += path
            }
        }
    }

    @Test
    fun M6_D1_boundaryTrees_everyPathTerminates_and_partitions() {
        for ((get, ids) in listOf<Pair<(String) -> BoundaryState, Set<Int>>>(
            BoundaryDetector::getAdditionState to (1..9).toSet(),
            BoundaryDetector::getSubtractionState to (10..17).toSet(),
            BoundaryDetector::getMultiplicationState to (18..28).toSet(),
            BoundaryDetector::getDivisionState to (29..37).toSet(),
        )) {
            val terminals = mutableListOf<String>()
            walk(get, ids, "", terminals)
            assertTrue(terminals.isNotEmpty())
        }
    }
}
