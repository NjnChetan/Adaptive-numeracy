package com.example.p1

data class KnowledgeComponent(
    val id: Int,
    val name: String,
    val prerequisites: List<Int>,
    val lowTransition: Double,
    val highTransition: Double,
    val guessProbability: Double,
    val slipProbability: Double
)

data class TransitionProb(val low: Double, val high: Double)

// ─────────────────────────────────────────────────────────────────────────────
// ADDITION syllabus (ids 1–10)
//
//                  1A                        id=1
//           /       |       \
//         2A2      1AC      2A1              id=2, 3, 4
//          |        \       /
//          |        2A1C                    id=5
//          |        /    \
//         3A      3AC    2A2C               id=7, 8, 6
//           \     /
//            3AC                            id=8
//             |
//           3AC2                            id=9
//             |
//           3AC3                            id=10
//
// SUBTRACTION syllabus (ids 11–18)
//
//                  1S                        id=11
//              /          \
//           2S1            2S2              id=12, 14
//          /    \
//       2S1B    (2S2 shares prereq)         id=13
//          \
//          2S2B                             id=15
//            \
//            (3S prereq: 2S2 only)
//
//   2S2  → 3S                              id=16
//   3S, 2S2B → 3SB                         id=17   ← convergence
//   3SB  → 3SB2                            id=18
//
// Edges:
//   1S   → 2S1, 2S2
//   2S1  → 2S1B, 2S2 (2S2 also from 1S directly — prereq: 1S only)
//   2S1  → 2S1B
//   2S1B → 2S2B
//   2S2  → 3S
//   3S, 2S2B → 3SB
//   3SB  → 3SB2
// ─────────────────────────────────────────────────────────────────────────────

object KnowledgeRepository {

    val components = mapOf(

        // ══════════════════════════════════════════════════════════════════════
        // ADDITION (ids 1–10)
        // ══════════════════════════════════════════════════════════════════════

        // ── Root ──────────────────────────────────────────────────────────────
        1 to KnowledgeComponent(
            id = 1, name = "1A: 1-digit + 1-digit, no carry",
            prerequisites    = emptyList(),
            lowTransition    = 0.30, highTransition    = 0.30,
            guessProbability = 0.20, slipProbability   = 0.050),

        // ── Level 2 (all prereq: 1A) ──────────────────────────────────────────
        2 to KnowledgeComponent(
            id = 2, name = "2A2: 2-digit + 2-digit, no carry",
            prerequisites    = listOf(1),
            lowTransition    = 0.01, highTransition    = 0.30,
            guessProbability = 0.15, slipProbability   = 0.050),

        3 to KnowledgeComponent(
            id = 3, name = "1AC: 1-digit + 1-digit, with carry",
            prerequisites    = listOf(1),
            lowTransition    = 0.01, highTransition    = 0.30,
            guessProbability = 0.20, slipProbability   = 0.050),

        4 to KnowledgeComponent(
            id = 4, name = "2A1: 2-digit + 1-digit, no carry",
            prerequisites    = listOf(1),
            lowTransition    = 0.01, highTransition    = 0.30,
            guessProbability = 0.20, slipProbability   = 0.050),

        // ── Level 3 (prereq: 1AC + 2A1) ──────────────────────────────────────
        5 to KnowledgeComponent(
            id = 5, name = "2A1C: 2-digit + 1-digit, with carry",
            prerequisites    = listOf(3, 4),
            lowTransition    = 0.01, highTransition    = 0.20,
            guessProbability = 0.10, slipProbability   = 0.075),

        // ── Level 4a (prereq: 2A1C only) ─────────────────────────────────────
        6 to KnowledgeComponent(
            id = 6, name = "2A2C: 2-digit + 2-digit, with carry",
            prerequisites    = listOf(5),
            lowTransition    = 0.01, highTransition    = 0.20,
            guessProbability = 0.10, slipProbability   = 0.075),

        // ── Level 4b (prereq: 2A2 only) ──────────────────────────────────────
        7 to KnowledgeComponent(
            id = 7, name = "3A: 3-digit + 3-digit, no carry",
            prerequisites    = listOf(2),
            lowTransition    = 0.01, highTransition    = 0.15,
            guessProbability = 0.05, slipProbability   = 0.100),

        // ── Level 5 — convergence (prereq: 3A + 2A1C) ────────────────────────
        8 to KnowledgeComponent(
            id = 8, name = "3AC: 3-digit + 3-digit, with carry",
            prerequisites    = listOf(7, 5),
            lowTransition    = 0.01, highTransition    = 0.15,
            guessProbability = 0.05, slipProbability   = 0.100),

        // ── Level 6 (prereq: 3AC) ─────────────────────────────────────────────
        9 to KnowledgeComponent(
            id = 9, name = "3AC2: 3-digit + 3-digit, 2 carry columns",
            prerequisites    = listOf(8),
            lowTransition    = 0.01, highTransition    = 0.10,
            guessProbability = 0.02, slipProbability   = 0.150),

        // ── Level 7 (prereq: 3AC2) ────────────────────────────────────────────
        10 to KnowledgeComponent(
            id = 10, name = "3AC3: 3-digit + 3-digit, 3 carry columns",
            prerequisites    = listOf(9),
            lowTransition    = 0.01, highTransition    = 0.10,
            guessProbability = 0.02, slipProbability   = 0.150),

        // ══════════════════════════════════════════════════════════════════════
        // SUBTRACTION (ids 11–18)
        //
        //  11 = 1S    : 1d − 1d, no borrow          (a > b, result > 0)
        //  12 = 2S1   : 2d − 1d, no borrow
        //  13 = 2S1B  : 2d − 1d, with borrow
        //  14 = 2S2   : 2d − 2d, no borrow
        //  15 = 2S2B  : 2d − 2d, with borrow
        //  16 = 3S    : 3d − 3d, no borrow
        //  17 = 3SB   : 3d − 3d, exactly 1 borrow column
        //  18 = 3SB2  : 3d − 3d, exactly 2 borrow columns
        // ══════════════════════════════════════════════════════════════════════

        // ── Root ──────────────────────────────────────────────────────────────
        11 to KnowledgeComponent(
            id = 11, name = "1S: 1-digit − 1-digit, no borrow",
            prerequisites    = emptyList(),
            lowTransition    = 0.30, highTransition    = 0.30,
            guessProbability = 0.20, slipProbability   = 0.050),

        // ── Level 2 ───────────────────────────────────────────────────────────
        12 to KnowledgeComponent(
            id = 12, name = "2S1: 2-digit − 1-digit, no borrow",
            prerequisites    = listOf(11),
            lowTransition    = 0.01, highTransition    = 0.30,
            guessProbability = 0.20, slipProbability   = 0.050),

        14 to KnowledgeComponent(
            id = 14, name = "2S2: 2-digit − 2-digit, no borrow",
            prerequisites    = listOf(11),
            lowTransition    = 0.01, highTransition    = 0.30,
            guessProbability = 0.15, slipProbability   = 0.050),

        // ── Level 3 ───────────────────────────────────────────────────────────
        13 to KnowledgeComponent(
            id = 13, name = "2S1B: 2-digit − 1-digit, with borrow",
            prerequisites    = listOf(12),
            lowTransition    = 0.01, highTransition    = 0.20,
            guessProbability = 0.10, slipProbability   = 0.075),

        // ── Level 4 ───────────────────────────────────────────────────────────
        15 to KnowledgeComponent(
            id = 15, name = "2S2B: 2-digit − 2-digit, with borrow",
            prerequisites    = listOf(13),
            lowTransition    = 0.01, highTransition    = 0.20,
            guessProbability = 0.10, slipProbability   = 0.075),

        // ── Level 5 ───────────────────────────────────────────────────────────
        16 to KnowledgeComponent(
            id = 16, name = "3S: 3-digit − 3-digit, no borrow",
            prerequisites    = listOf(14),
            lowTransition    = 0.01, highTransition    = 0.15,
            guessProbability = 0.05, slipProbability   = 0.100),

        // ── Level 6 — convergence (prereq: 3S + 2S2B) ────────────────────────
        17 to KnowledgeComponent(
            id = 17, name = "3SB: 3-digit − 3-digit, 1 borrow column",
            prerequisites    = listOf(16, 15),
            lowTransition    = 0.01, highTransition    = 0.15,
            guessProbability = 0.05, slipProbability   = 0.100),

        // ── Level 7 ───────────────────────────────────────────────────────────
        18 to KnowledgeComponent(
            id = 18, name = "3SB2: 3-digit − 3-digit, 2 borrow columns",
            prerequisites    = listOf(17),
            lowTransition    = 0.01, highTransition    = 0.10,
            guessProbability = 0.02, slipProbability   = 0.150)
    )

    // ── Helper functions ──────────────────────────────────────────────────────

    fun getGuessProb(kcId: Int): Double =
        components[kcId]?.guessProbability ?: 0.20

    fun getSlipProb(kcId: Int): Double =
        components[kcId]?.slipProbability ?: 0.05

    fun getTransition(kcId: Int): TransitionProb {
        val kc = components[kcId] ?: return TransitionProb(0.01, 0.10)
        return TransitionProb(kc.lowTransition, kc.highTransition)
    }

    fun getPrerequisites(kcId: Int): List<Int> =
        components[kcId]?.prerequisites ?: emptyList()

    /** True if this KC is a subtraction node (ids 11–18) */
    fun isSubtraction(kcId: Int): Boolean = kcId in 11..18

    /**
     * Zone of Proximal Development:
     * KCs whose prerequisites are ALL mastered but which are not yet mastered.
     * Only returns KCs belonging to the active operation set.
     */
    fun getZPD(student: StudentModel, ops: Set<String>): List<Int> {
        val additionIds     = 1..10
        val subtractionIds  = 11..18
        return components.values
            .filter { kc ->
                !student.isMastered(kc.id) &&
                        kc.prerequisites.all { student.isMastered(it) } &&
                        (("+" in ops && kc.id in additionIds) ||
                                ("-" in ops && kc.id in subtractionIds))
            }
            .map { it.id }
            .sorted()
    }
}