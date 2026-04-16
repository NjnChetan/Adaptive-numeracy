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
// Syllabus graph (10 nodes):
//
//                  1A                        id=1
//           /       |       \
//         2A2      1AC      2A1              id=2, 3, 4
//          |        \       /
//          |        2A1C                    id=5
//          |        /    \
//         3A      3AC    2A2C               id=7, 8, 6
//           \     /
//            3AC                            id=8  ← convergence of 3A + 2A1C
//             |
//           3AC2                            id=9
//             |
//           3AC3                            id=10
//
// Edges (parent → child):
//   1A   → 2A2, 1AC, 2A1
//   1AC, 2A1 → 2A1C
//   2A2  → 3A
//   3A, 2A1C → 3AC
//   2A1C → 2A2C
//   3AC  → 3AC2
//   3AC2 → 3AC3
// ─────────────────────────────────────────────────────────────────────────────

object KnowledgeRepository {

    val components = mapOf(

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

    /**
     * Returns the direct children of kcId — i.e. KCs that list kcId as a
     * prerequisite. Used by AdaptiveEngine's ancestor-tracking ZPD unlocking,
     * mirroring Python's progression_graph[chosen_trace].
     */
    fun getChildren(kcId: Int): List<Int> =
        components.values
            .filter { kcId in it.prerequisites }
            .map { it.id }

    /**
     * Zone of Proximal Development:
     * KCs whose prerequisites are ALL mastered but which are not yet mastered.
     */
    fun getZPD(student: StudentModel): List<Int> =
        components.values
            .filter { kc ->
                !student.isMastered(kc.id) &&
                        kc.prerequisites.all { student.isMastered(it) }
            }
            .map { it.id }
            .sorted()
}