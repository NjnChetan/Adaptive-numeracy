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

// ───────────────────────────────────
// ADDITION syllabus graph (9 nodes, IDs 1–9):
//
//                  1A                        id=1
//                /   |
//              1AC   2A1                       id=2, 3
//               \   / \
//                2A1C  2A2                     id=4, 5
//               /  \    \
//             2A2C  |    3A                    id=6, 7
//                   \   /
//                    3AC                       id=8
//                     |
//                   3AC2                       id=9
//
// Edges (parent → child):
//   1A   → 1AC, 2A1
//   1AC, 2A1 → 2A1C
//   2A1  → 2A2
//   2A1C → 2A2C
//   2A2  → 3A
//   2A1C, 3A → 3AC
//   3AC  → 3AC2
// ─────────────────────────────────────────────────────────────────────────────
// SUBTRACTION syllabus graph (8 nodes, IDs 10–17):
//
//                  1S                        id=10
//                  |
//                 2S1                        id=11
//               /     \
//            2S1B     2S2                    id=12, 13
//              |        \
//            2S2B       3S                   id=15, 14
//               \      /
//                3SB                         id=16
//                 |
//               3SB2                         id=17
//
// Edges (parent → child):
//   1S   → 2S1
//   2S1  → 2S1B, 2S2
//   2S1B → 2S2B
//   2S2  → 3S
//   3S, 2S2B → 3SB
//   3SB  → 3SB2
// ─────────────────────────────────────────────────────────────────────────────

object KnowledgeRepository {

    // ── Addition IDs: 1–9 ──────────────────────────────────────────────────
    // ── Subtraction IDs: 10–17 ─────────────────────────────────────────────

    val components = mapOf(

        // ════════════════════════════════════════════════════════════════════
        // ADDITION GRAPH
        // ════════════════════════════════════════════════════════════════════

        // ── Root ──────────────────────────────────────────────────────────────
        1 to KnowledgeComponent(
            id = 1, name = "1A: 1-digit + 1-digit, no carry",
            prerequisites    = emptyList(),
            lowTransition    = 0.30, highTransition    = 0.30,
            guessProbability = 0.20, slipProbability   = 0.050),

        // ── Level 2 (both prereq: 1A) ─────────────────────────────────────────
        2 to KnowledgeComponent(
            id = 2, name = "1AC: 1-digit + 1-digit, with carry",
            prerequisites    = listOf(1),
            lowTransition    = 0.01, highTransition    = 0.30,
            guessProbability = 0.20, slipProbability   = 0.050),

        3 to KnowledgeComponent(
            id = 3, name = "2A1: 2-digit + 1-digit, no carry",
            prerequisites    = listOf(1),
            lowTransition    = 0.01, highTransition    = 0.30,
            guessProbability = 0.20, slipProbability   = 0.050),

        // ── Level 3 ───────────────────────────────────────────────────────────
        4 to KnowledgeComponent(
            id = 4, name = "2A1C: 2-digit + 1-digit, with carry",
            prerequisites    = listOf(2, 3),   // 1AC + 2A1
            lowTransition    = 0.01, highTransition    = 0.20,
            guessProbability = 0.10, slipProbability   = 0.075),

        5 to KnowledgeComponent(
            id = 5, name = "2A2: 2-digit + 2-digit, no carry",
            prerequisites    = listOf(3),       // 2A1 (FIXED — was listOf(1))
            lowTransition    = 0.01, highTransition    = 0.30,
            guessProbability = 0.15, slipProbability   = 0.050),

        // ── Level 4 ───────────────────────────────────────────────────────────
        6 to KnowledgeComponent(
            id = 6, name = "2A2C: 2-digit + 2-digit, double carry",
            prerequisites    = listOf(4),       // 2A1C
            lowTransition    = 0.01, highTransition    = 0.20,
            guessProbability = 0.10, slipProbability   = 0.075),

        7 to KnowledgeComponent(
            id = 7, name = "3A: 3-digit + 3-digit, no carry",
            prerequisites    = listOf(5),       // 2A2
            lowTransition    = 0.01, highTransition    = 0.15,
            guessProbability = 0.05, slipProbability   = 0.100),

        // ── Level 5 — convergence (prereq: 2A1C + 3A) ─────────────────────────
        8 to KnowledgeComponent(
            id = 8, name = "3AC: 3-digit + 2-digit, single carry",
            prerequisites    = listOf(4, 7),    // 2A1C + 3A
            lowTransition    = 0.01, highTransition    = 0.15,
            guessProbability = 0.05, slipProbability   = 0.100),

        // ── Level 6 (prereq: 3AC) ─────────────────────────────────────────────
        9 to KnowledgeComponent(
            id = 9, name = "3AC2: 3-digit + 3-digit, double carry",
            prerequisites    = listOf(8),       // 3AC
            lowTransition    = 0.01, highTransition    = 0.10,
            guessProbability = 0.02, slipProbability   = 0.150),

        // ════════════════════════════════════════════════════════════════════
        // SUBTRACTION GRAPH
        // ════════════════════════════════════════════════════════════════════

        // ── Root ──────────────────────────────────────────────────────────────
        10 to KnowledgeComponent(
            id = 10, name = "1S: 1-digit subtraction, no borrow",
            prerequisites    = emptyList(),
            lowTransition    = 0.30, highTransition    = 0.30,
            guessProbability = 0.20, slipProbability   = 0.050),

        // ── Level 2 ──────────────────────────────────────────────────────────
        11 to KnowledgeComponent(
            id = 11, name = "2S1: 2-digit subtraction, no borrow",
            prerequisites    = listOf(10),      // 1S
            lowTransition    = 0.01, highTransition    = 0.30,
            guessProbability = 0.20, slipProbability   = 0.050),

        // ── Level 3 ──────────────────────────────────────────────────────────
        12 to KnowledgeComponent(
            id = 12, name = "2S1B: 2-digit subtraction, with borrow",
            prerequisites    = listOf(11),      // 2S1
            lowTransition    = 0.01, highTransition    = 0.20,
            guessProbability = 0.10, slipProbability   = 0.075),

        13 to KnowledgeComponent(
            id = 13, name = "2S2: 2-digit − 2-digit, no borrow",
            prerequisites    = listOf(11),      // 2S1
            lowTransition    = 0.01, highTransition    = 0.30,
            guessProbability = 0.15, slipProbability   = 0.050),

        // ── Level 4 ──────────────────────────────────────────────────────────
        14 to KnowledgeComponent(
            id = 14, name = "3S: 3-digit subtraction, no borrow",
            prerequisites    = listOf(13),      // 2S2
            lowTransition    = 0.01, highTransition    = 0.15,
            guessProbability = 0.05, slipProbability   = 0.100),

        15 to KnowledgeComponent(
            id = 15, name = "2S2B: 2-digit − 2-digit, with borrow",
            prerequisites    = listOf(12),      // 2S1B
            lowTransition    = 0.01, highTransition    = 0.20,
            guessProbability = 0.10, slipProbability   = 0.075),

        // ── Level 5 — convergence (prereq: 3S + 2S2B) ─────────────────────────
        16 to KnowledgeComponent(
            id = 16, name = "3SB: 3-digit subtraction, single borrow",
            prerequisites    = listOf(14, 15),  // 3S + 2S2B
            lowTransition    = 0.01, highTransition    = 0.15,
            guessProbability = 0.05, slipProbability   = 0.100),

        // ── Level 6 (prereq: 3SB) ────────────────────────────────────────────
        17 to KnowledgeComponent(
            id = 17, name = "3SB2: 3-digit subtraction, double borrow",
            prerequisites    = listOf(16),      // 3SB
            lowTransition    = 0.01, highTransition    = 0.10,
            guessProbability = 0.02, slipProbability   = 0.150)
    )

    // ── Addition KC IDs ──────────────────────────────────────────────────────
    val additionIds = (1..9).toList()

    // ── Subtraction KC IDs ───────────────────────────────────────────────────
    val subtractionIds = (10..17).toList()

    /** Returns "+" for addition KCs, "−" for subtraction KCs */
    fun getOperationType(kcId: Int): String =
        if (kcId in 10..17) "-" else "+"

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
     * Optionally filtered to a specific set of KC IDs (e.g. addition-only or subtraction-only).
     */
    fun getZPD(isMastered: (Int) -> Boolean, filterIds: List<Int>? = null): List<Int> {
        val pool = if (filterIds != null)
            components.values.filter { it.id in filterIds }
        else
            components.values.toList()

        return pool
            .filter { kc ->
                !isMastered(kc.id) &&
                        kc.prerequisites.all { isMastered(it) }
            }
            .map { it.id }
            .sorted()
    }
}