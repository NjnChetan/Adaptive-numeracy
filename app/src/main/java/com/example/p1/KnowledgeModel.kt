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

object KnowledgeRepository {

    val components = mapOf(

        // ════════════════════════════════════════════════════════════════════
        // ADDITION GRAPH
        // ════════════════════════════════════════════════════════════════════
        1 to KnowledgeComponent(
            id = 1, name = "1A: 1-digit + 1-digit, no carry",
            prerequisites    = emptyList(),
            lowTransition    = 0.30, highTransition    = 0.30,
            guessProbability = 0.20, slipProbability   = 0.050),

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

        4 to KnowledgeComponent(
            id = 4, name = "2A1C: 2-digit + 1-digit, with carry",
            prerequisites    = listOf(2, 3),
            lowTransition    = 0.01, highTransition    = 0.20,
            guessProbability = 0.10, slipProbability   = 0.075),

        5 to KnowledgeComponent(
            id = 5, name = "2A2: 2-digit + 2-digit, no carry",
            prerequisites    = listOf(3),
            lowTransition    = 0.01, highTransition    = 0.30,
            guessProbability = 0.15, slipProbability   = 0.050),

        6 to KnowledgeComponent(
            id = 6, name = "2A2C: 2-digit + 2-digit, double carry",
            prerequisites    = listOf(4),
            lowTransition    = 0.01, highTransition    = 0.20,
            guessProbability = 0.10, slipProbability   = 0.075),

        7 to KnowledgeComponent(
            id = 7, name = "3A: 3-digit + 3-digit, no carry",
            prerequisites    = listOf(5),
            lowTransition    = 0.01, highTransition    = 0.15,
            guessProbability = 0.05, slipProbability   = 0.100),

        8 to KnowledgeComponent(
            id = 8, name = "3AC: 3-digit + 2-digit, single carry",
            prerequisites    = listOf(4, 7),
            lowTransition    = 0.01, highTransition    = 0.15,
            guessProbability = 0.05, slipProbability   = 0.100),

        9 to KnowledgeComponent(
            id = 9, name = "3AC2: 3-digit + 3-digit, double carry",
            prerequisites    = listOf(8),
            lowTransition    = 0.01, highTransition    = 0.10,
            guessProbability = 0.02, slipProbability   = 0.150),

        // ════════════════════════════════════════════════════════════════════
        // SUBTRACTION GRAPH
        // ════════════════════════════════════════════════════════════════════
        10 to KnowledgeComponent(
            id = 10, name = "1S: 1-digit subtraction, no borrow",
            prerequisites    = emptyList(),
            lowTransition    = 0.30, highTransition    = 0.30,
            guessProbability = 0.20, slipProbability   = 0.050),

        11 to KnowledgeComponent(
            id = 11, name = "2S1: 2-digit subtraction, no borrow",
            prerequisites    = listOf(10),
            lowTransition    = 0.01, highTransition    = 0.30,
            guessProbability = 0.20, slipProbability   = 0.050),

        12 to KnowledgeComponent(
            id = 12, name = "2S1B: 2-digit subtraction, with borrow",
            prerequisites    = listOf(11),
            lowTransition    = 0.01, highTransition    = 0.20,
            guessProbability = 0.10, slipProbability   = 0.075),

        13 to KnowledgeComponent(
            id = 13, name = "2S2: 2-digit − 2-digit, no borrow",
            prerequisites    = listOf(11),
            lowTransition    = 0.01, highTransition    = 0.30,
            guessProbability = 0.15, slipProbability   = 0.050),

        14 to KnowledgeComponent(
            id = 14, name = "3S: 3-digit subtraction, no borrow",
            prerequisites    = listOf(13),
            lowTransition    = 0.01, highTransition    = 0.15,
            guessProbability = 0.05, slipProbability   = 0.100),

        15 to KnowledgeComponent(
            id = 15, name = "2S2B: 2-digit − 2-digit, with borrow",
            prerequisites    = listOf(12),
            lowTransition    = 0.01, highTransition    = 0.20,
            guessProbability = 0.10, slipProbability   = 0.075),

        16 to KnowledgeComponent(
            id = 16, name = "3SB: 3-digit subtraction, single borrow",
            prerequisites    = listOf(14, 15),
            lowTransition    = 0.01, highTransition    = 0.15,
            guessProbability = 0.05, slipProbability   = 0.100),

        17 to KnowledgeComponent(
            id = 17, name = "3SB2: 3-digit subtraction, double borrow",
            prerequisites    = listOf(16),
            lowTransition    = 0.01, highTransition    = 0.10,
            guessProbability = 0.02, slipProbability   = 0.150),

        // ════════════════════════════════════════════════════════════════════
        // MULTIPLICATION GRAPH (11 nodes, IDs 18–28)
        // ════════════════════════════════════════════════════════════════════
        18 to KnowledgeComponent(
            id = 18, name = "T5: Multiplication tables up to 5",
            prerequisites    = emptyList(),
            lowTransition    = 0.30, highTransition    = 0.30,
            guessProbability = 0.20, slipProbability   = 0.050),

        19 to KnowledgeComponent(
            id = 19, name = "T10: Multiplication tables 5 to 10",
            prerequisites    = listOf(18),
            lowTransition    = 0.01, highTransition    = 0.30,
            guessProbability = 0.20, slipProbability   = 0.050),

        20 to KnowledgeComponent(
            id = 20, name = "2M1: 2x1 multiplication, no carry",
            prerequisites    = listOf(18),
            lowTransition    = 0.01, highTransition    = 0.30,
            guessProbability = 0.20, slipProbability   = 0.050),

        21 to KnowledgeComponent(
            id = 21, name = "2M1C: 2x1 multiplication, carry",
            prerequisites    = listOf(19, 20),
            lowTransition    = 0.01, highTransition    = 0.20,
            guessProbability = 0.10, slipProbability   = 0.075),

        22 to KnowledgeComponent(
            id = 22, name = "3M1: 3x1 multiplication, no carry",
            prerequisites    = listOf(20),
            lowTransition    = 0.01, highTransition    = 0.30,
            guessProbability = 0.15, slipProbability   = 0.050),

        23 to KnowledgeComponent(
            id = 23, name = "3M1C: 3x1 multiplication, single carry",
            prerequisites    = listOf(21, 22),
            lowTransition    = 0.01, highTransition    = 0.20,
            guessProbability = 0.10, slipProbability   = 0.075),

        24 to KnowledgeComponent(
            id = 24, name = "3M1C2: 3x1 multiplication, double carry",
            prerequisites    = listOf(23),
            lowTransition    = 0.01, highTransition    = 0.15,
            guessProbability = 0.05, slipProbability   = 0.100),

        25 to KnowledgeComponent(
            id = 25, name = "2M2: 2x2 multiplication, no carry",
            prerequisites    = listOf(24),
            lowTransition    = 0.01, highTransition    = 0.15,
            guessProbability = 0.05, slipProbability   = 0.100),

        26 to KnowledgeComponent(
            id = 26, name = "2M2C: 2x2 multiplication, carry",
            prerequisites    = listOf(25),
            lowTransition    = 0.01, highTransition    = 0.10,
            guessProbability = 0.03, slipProbability   = 0.120),

        27 to KnowledgeComponent(
            id = 27, name = "3M2: 3x2 multiplication, no carry",
            prerequisites    = listOf(25),
            lowTransition    = 0.01, highTransition    = 0.10,
            guessProbability = 0.03, slipProbability   = 0.120),

        28 to KnowledgeComponent(
            id = 28, name = "3M2C: 3x2 multiplication, carry",
            prerequisites    = listOf(27, 26),
            lowTransition    = 0.01, highTransition    = 0.08,
            guessProbability = 0.02, slipProbability   = 0.150),

        // ════════════════════════════════════════════════════════════════════
        // DIVISION GRAPH (9 nodes, IDs 29–37)
        // ════════════════════════════════════════════════════════════════════
        29 to KnowledgeComponent(
            id = 29, name = "2D1: 2/1 division without remainder",
            prerequisites    = emptyList(),
            lowTransition    = 0.30, highTransition    = 0.30,
            guessProbability = 0.20, slipProbability   = 0.050),

        30 to KnowledgeComponent(
            id = 30, name = "3D1: 3/1 division without remainder",
            prerequisites    = listOf(29),
            lowTransition    = 0.01, highTransition    = 0.30,
            guessProbability = 0.15, slipProbability   = 0.050),

        31 to KnowledgeComponent(
            id = 31, name = "2D1R: 2/1 division with remainder",
            prerequisites    = listOf(29),
            lowTransition    = 0.01, highTransition    = 0.30,
            guessProbability = 0.15, slipProbability   = 0.050),

        32 to KnowledgeComponent(
            id = 32, name = "3D1R: 3/1 division with remainder",
            prerequisites    = listOf(30, 31),
            lowTransition    = 0.01, highTransition    = 0.20,
            guessProbability = 0.10, slipProbability   = 0.075),

        33 to KnowledgeComponent(
            id = 33, name = "3D1Z: 3/1 division with 0 in quotient",
            prerequisites    = listOf(32),
            lowTransition    = 0.01, highTransition    = 0.15,
            guessProbability = 0.05, slipProbability   = 0.100),

        34 to KnowledgeComponent(
            id = 34, name = "4D1R: 4/1 division with remainder",
            prerequisites    = listOf(32),
            lowTransition    = 0.01, highTransition    = 0.15,
            guessProbability = 0.05, slipProbability   = 0.100),

        35 to KnowledgeComponent(
            id = 35, name = "3D2: 3/2 division without remainder",
            prerequisites    = listOf(30),
            lowTransition    = 0.01, highTransition    = 0.15,
            guessProbability = 0.05, slipProbability   = 0.100),

        36 to KnowledgeComponent(
            id = 36, name = "3D2R: 3/2 division with remainder",
            prerequisites    = listOf(35, 32),
            lowTransition    = 0.01, highTransition    = 0.10,
            guessProbability = 0.03, slipProbability   = 0.120),

        37 to KnowledgeComponent(
            id = 37, name = "4D2R: 4/2 division with remainder",
            prerequisites    = listOf(36, 34),
            lowTransition    = 0.01, highTransition    = 0.08,
            guessProbability = 0.02, slipProbability   = 0.150)
    )

    val additionIds       = (1..9).toList()
    val subtractionIds    = (10..17).toList()
    val multiplicationIds = (18..28).toList()
    val divisionIds       = (29..37).toList()

    /** Returns "+" / "−" / "×" / "÷" for the corresponding KC id range */
    fun getOperationType(kcId: Int): String = when (kcId) {
        in 10..17 -> "-"
        in 18..28 -> "×"
        in 29..37 -> "÷"
        else      -> "+"
    }

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

    fun getChildren(kcId: Int): List<Int> =
        components.values
            .filter { kcId in it.prerequisites }
            .map { it.id }

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