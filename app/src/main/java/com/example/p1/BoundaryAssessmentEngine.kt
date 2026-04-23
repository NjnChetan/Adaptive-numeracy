package com.example.p1

import java.io.Serializable

/**
 * Knowledge Boundary Assessment Logic
 * Based on Graph v2 (Addition) and Graph v3 (Subtraction) decision trees.
 */

object BoundaryAssessmentEngine {
    
    sealed class BoundaryState : Serializable {
        data class Ask(val nodeName: String) : BoundaryState()
        data class Terminal(
            val solvable: Set<String>,
            val unsolvable: Set<String>,
            val boundary: Set<String>
        ) : BoundaryState()
    }

    val NODE_TO_ID = mapOf(
        "1A" to 1, "1AC" to 2, "2A1" to 3, "2A1C" to 4, "2A2" to 5, "2A2C" to 6, "3A" to 7, "3AC" to 8, "3AC2" to 9,
        "1S" to 10, "2S1" to 11, "2S1B" to 12, "2S2" to 13, "3S" to 14, "2S2B" to 15, "3SB" to 16, "3SB2" to 17
    )

    val ID_TO_NODE = NODE_TO_ID.entries.associate { (k, v) -> v to k }

    // ─────────────────────────────────────────────────────────────────────────────
    // ADDITION DISPATCH (Graph v2)
    // ─────────────────────────────────────────────────────────────────────────────
    val ADDITION_DISPATCH = mapOf(
        ""      to BoundaryState.Ask("2A1C"),
        "1"     to BoundaryState.Ask("3A"),
        "11"    to BoundaryState.Ask("2A2C"),
        "111"   to BoundaryState.Ask("3AC"),
        "1111"  to BoundaryState.Ask("3AC2"),
        "11111" to BoundaryState.Terminal(
            solvable   = setOf("1A","1AC","2A1","2A1C","2A2","2A2C","3A","3AC","3AC2"),
            unsolvable = emptySet(),
            boundary   = setOf("3AC2")
        ),
        "11110" to BoundaryState.Terminal(
            solvable   = setOf("1A","1AC","2A1","2A1C","2A2","2A2C","3A","3AC"),
            unsolvable = setOf("3AC2"),
            boundary   = setOf("3AC")
        ),
        "1110"  to BoundaryState.Terminal(
            solvable   = setOf("1A","1AC","2A1","2A1C","2A2","2A2C","3A"),
            unsolvable = setOf("3AC","3AC2"),
            boundary   = setOf("2A1C","3A")
        ),
        "110"   to BoundaryState.Ask("3AC"),
        "1101"  to BoundaryState.Ask("3AC2"),
        "11011" to BoundaryState.Terminal(
            solvable   = setOf("1A","1AC","2A1","2A1C","2A2","3A","3AC","3AC2"),
            unsolvable = setOf("2A2C"),
            boundary   = setOf("2A1C")
        ),
        "11010" to BoundaryState.Terminal(
            solvable   = setOf("1A","1AC","2A1","2A1C","2A2","3A","3AC"),
            unsolvable = setOf("2A2C","3AC2"),
            boundary   = setOf("2A1C","3AC")
        ),
        "1100"  to BoundaryState.Terminal(
            solvable   = setOf("1A","1AC","2A1","2A1C","2A2","3A"),
            unsolvable = setOf("2A2C","3AC","3AC2"),
            boundary   = setOf("2A1C","3A")
        ),
        "10"    to BoundaryState.Ask("2A2"),
        "101"   to BoundaryState.Ask("2A2C"),
        "1011"  to BoundaryState.Terminal(
            solvable   = setOf("1A","1AC","2A1","2A1C","2A2","2A2C"),
            unsolvable = setOf("3A","3AC","3AC2"),
            boundary   = setOf("2A1C","2A2")
        ),
        "1010"  to BoundaryState.Terminal(
            solvable   = setOf("1A","1AC","2A1","2A1C","2A2"),
            unsolvable = setOf("2A2C","3A","3AC","3AC2"),
            boundary   = setOf("2A1C","2A2")
        ),
        "100"   to BoundaryState.Ask("2A2C"),
        "1001"  to BoundaryState.Terminal(
            solvable   = setOf("1A","1AC","2A1","2A1C","2A2C"),
            unsolvable = setOf("2A2","3A","3AC","3AC2"),
            boundary   = setOf("2A1","2A1C")
        ),
        "1000"  to BoundaryState.Terminal(
            solvable   = setOf("1A","1AC","2A1","2A1C"),
            unsolvable = setOf("2A2","2A2C","3A","3AC","3AC2"),
            boundary   = setOf("2A1","2A1C")
        ),
        "0"     to BoundaryState.Ask("2A1"),
        "01"    to BoundaryState.Ask("1AC"),
        "011"   to BoundaryState.Ask("2A2"),
        "0111"  to BoundaryState.Ask("3A"),
        "01111" to BoundaryState.Terminal(
            solvable   = setOf("1A","1AC","2A1","2A2","3A"),
            unsolvable = setOf("2A1C","2A2C","3AC","3AC2"),
            boundary   = setOf("1AC","2A1","3A")
        ),
        "01110" to BoundaryState.Terminal(
            solvable   = setOf("1A","1AC","2A1","2A2"),
            unsolvable = setOf("2A1C","2A2C","3A","3AC","3AC2"),
            boundary   = setOf("1AC","2A1","2A2")
        ),
        "0110"  to BoundaryState.Terminal(
            solvable   = setOf("1A","1AC","2A1"),
            unsolvable = setOf("2A1C","2A2","2A2C","3A","3AC","3AC2"),
            boundary   = setOf("1AC","2A1")
        ),
        "010"   to BoundaryState.Ask("2A2"),
        "0101"  to BoundaryState.Ask("3A"),
        "01011" to BoundaryState.Terminal(
            solvable   = setOf("1A","2A1","2A2","3A"),
            unsolvable = setOf("1AC","2A1C","2A2C","3AC","3AC2"),
            boundary   = setOf("1A","2A1","3A")
        ),
        "01010" to BoundaryState.Terminal(
            solvable   = setOf("1A","2A1","2A2"),
            unsolvable = setOf("1AC","2A1C","2A2C","3A","3AC","3AC2"),
            boundary   = setOf("1A","2A1","2A2")
        ),
        "0100"  to BoundaryState.Terminal(
            solvable   = setOf("1A","2A1"),
            unsolvable = setOf("1AC","2A1C","2A2","2A2C","3A","3AC","3AC2"),
            boundary   = setOf("1A","2A1")
        ),
        "00"    to BoundaryState.Ask("1A"),
        "001"   to BoundaryState.Ask("1AC"),
        "0011"  to BoundaryState.Terminal(
            solvable   = setOf("1A","1AC"),
            unsolvable = setOf("2A1","2A1C","2A2","2A2C","3A","3AC","3AC2"),
            boundary   = setOf("1A","1AC")
        ),
        "0010"  to BoundaryState.Terminal(
            solvable   = setOf("1A"),
            unsolvable = setOf("1AC","2A1","2A1C","2A2","2A2C","3A","3AC","3AC2"),
            boundary   = setOf("1A")
        ),
        "000"   to BoundaryState.Terminal(
            solvable   = emptySet(),
            unsolvable = setOf("1A","1AC","2A1","2A1C","2A2","2A2C","3A","3AC","3AC2"),
            boundary   = setOf("1A")
        )
    )

    // ─────────────────────────────────────────────────────────────────────────────
    // SUBTRACTION DISPATCH (Graph v3)
    // ─────────────────────────────────────────────────────────────────────────────
    val SUBTRACTION_DISPATCH = mapOf(
        ""      to BoundaryState.Ask("2S1B"),
        "1"     to BoundaryState.Ask("3S"),
        "11"    to BoundaryState.Ask("3SB"),
        "111"   to BoundaryState.Ask("3SB2"),
        "1111"  to BoundaryState.Terminal(
            solvable   = setOf("1S","2S1","2S1B","2S2","3S","2S2B","3SB","3SB2"),
            unsolvable = emptySet(),
            boundary   = setOf("3SB2")
        ),
        "1110"  to BoundaryState.Terminal(
            solvable   = setOf("1S","2S1","2S1B","2S2","2S2B","3S","3SB"),
            unsolvable = setOf("3SB2"),
            boundary   = setOf("3SB")
        ),
        "110"   to BoundaryState.Ask("2S2B"),
        "1101"  to BoundaryState.Terminal(
            solvable   = setOf("1S","2S1","2S1B","2S2","2S2B","3S"),
            unsolvable = setOf("3SB","3SB2"),
            boundary   = setOf("2S2B","3S")
        ),
        "1100"  to BoundaryState.Terminal(
            solvable   = setOf("1S","2S1","2S1B","2S2","3S"),
            unsolvable = setOf("2S2B","3SB","3SB2"),
            boundary   = setOf("2S1B","3S")
        ),
        "10"    to BoundaryState.Ask("2S2"),
        "101"   to BoundaryState.Ask("2S2B"),
        "1011"  to BoundaryState.Terminal(
            solvable   = setOf("1S","2S1","2S1B","2S2","2S2B"),
            unsolvable = setOf("3S","3SB","3SB2"),
            boundary   = setOf("2S2","2S2B")
        ),
        "1010"  to BoundaryState.Terminal(
            solvable   = setOf("1S","2S1","2S1B","2S2"),
            unsolvable = setOf("2S2B","3S","3SB","3SB2"),
            boundary   = setOf("2S1B","2S2")
        ),
        "100"   to BoundaryState.Ask("2S2B"),
        "1001"  to BoundaryState.Terminal(
            solvable   = setOf("1S","2S1","2S1B","2S2B"),
            unsolvable = setOf("2S2","3S","3SB","3SB2"),
            boundary   = setOf("2S1","2S2B")
        ),
        "1000"  to BoundaryState.Terminal(
            solvable   = setOf("1S","2S1","2S1B"),
            unsolvable = setOf("2S2","2S2B","3S","3SB","3SB2"),
            boundary   = setOf("2S1","2S1B")
        ),
        "0"     to BoundaryState.Ask("2S1"),
        "01"    to BoundaryState.Ask("2S2"),
        "011"   to BoundaryState.Ask("3S"),
        "0111"  to BoundaryState.Terminal(
            solvable   = setOf("1S","2S1","2S2","3S"),
            unsolvable = setOf("2S1B","2S2B","3SB","3SB2"),
            boundary   = setOf("2S1","3S")
        ),
        "0110"  to BoundaryState.Terminal(
            solvable   = setOf("1S","2S1","2S2"),
            unsolvable = setOf("2S1B","2S2B","3S","3SB","3SB2"),
            boundary   = setOf("2S1","2S2")
        ),
        "010"   to BoundaryState.Terminal(
            solvable   = setOf("1S","2S1"),
            unsolvable = setOf("2S1B","2S2","2S2B","3S","3SB","3SB2"),
            boundary   = setOf("2S1")
        ),
        "00"    to BoundaryState.Ask("1S"),
        "001"   to BoundaryState.Terminal(
            solvable   = setOf("1S"),
            unsolvable = setOf("2S1","2S1B","2S2","2S2B","3S","3SB","3SB2"),
            boundary   = setOf("1S")
        ),
        "000"   to BoundaryState.Terminal(
            solvable   = emptySet(),
            unsolvable = setOf("1S","2S1","2S1B","2S2","3S","2S2B","3SB","3SB2"),
            boundary   = setOf("1S")
        )
    )

    /**
     * Returns the next state based on the current binary response string.
     */
    fun getNextState(responseString: String, isAddition: Boolean): BoundaryState? {
        val dispatch = if (isAddition) ADDITION_DISPATCH else SUBTRACTION_DISPATCH
        return dispatch[responseString]
    }
}
