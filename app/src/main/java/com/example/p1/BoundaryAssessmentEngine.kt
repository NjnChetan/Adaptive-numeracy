package com.example.p1

import java.io.Serializable

sealed class BoundaryState : Serializable {
    data class Ask(val kcId: Int) : BoundaryState()
    data class Terminal(
        val solvable: Set<Int>,
        val unsolvable: Set<Int>,
        val boundary: Set<Int>
    ) : BoundaryState()
}

object BoundaryDetector {

    val NODE_TO_ID = mapOf(
        "1A" to 1, "1AC" to 2, "2A1" to 3, "2A1C" to 4, "2A2" to 5, "2A2C" to 6, "3A" to 7, "3AC" to 8, "3AC2" to 9,
        "1S" to 10, "2S1" to 11, "2S1B" to 12, "2S2" to 13, "3S" to 14, "2S2B" to 15, "3SB" to 16, "3SB2" to 17
    )

    val ID_TO_NODE = NODE_TO_ID.entries.associate { (k, v) -> v to k }

    /**
     * Get the next state for Addition (Graph v2)
     */
    fun getAdditionState(response: String): BoundaryState {
        return when (response) {
            ""      -> BoundaryState.Ask(4)  // 2A1C
            "1"     -> BoundaryState.Ask(7)  // 3A
            "11"    -> BoundaryState.Ask(6)  // 2A2C
            "111"   -> BoundaryState.Ask(8)  // 3AC
            "1111"  -> BoundaryState.Ask(9)  // 3AC2
            "11111" -> BoundaryState.Terminal(
                solvable   = setOf(1, 2, 3, 4, 5, 6, 7, 8, 9),
                unsolvable = emptySet(),
                boundary   = setOf(9)
            )
            "11110" -> BoundaryState.Terminal(
                solvable   = setOf(1, 2, 3, 4, 5, 6, 7, 8),
                unsolvable = setOf(9),
                boundary   = setOf(8)
            )
            "1110"  -> BoundaryState.Terminal(
                solvable   = setOf(1, 2, 3, 4, 5, 6, 7),
                unsolvable = setOf(8, 9),
                boundary   = setOf(4, 7)
            )
            "110"   -> BoundaryState.Ask(8)  // 3AC
            "1101"  -> BoundaryState.Ask(9)  // 3AC2
            "11011" -> BoundaryState.Terminal(
                solvable   = setOf(1, 2, 3, 4, 5, 7, 8, 9),
                unsolvable = setOf(6),
                boundary   = setOf(4)
            )
            "11010" -> BoundaryState.Terminal(
                solvable   = setOf(1, 2, 3, 4, 5, 7, 8),
                unsolvable = setOf(6, 9),
                boundary   = setOf(4, 8)
            )
            "1100"  -> BoundaryState.Terminal(
                solvable   = setOf(1, 2, 3, 4, 5, 7),
                unsolvable = setOf(6, 8, 9),
                boundary   = setOf(4, 7)
            )
            "10"    -> BoundaryState.Ask(5)  // 2A2
            "101"   -> BoundaryState.Ask(6)  // 2A2C
            "1011"  -> BoundaryState.Terminal(
                solvable   = setOf(1, 2, 3, 4, 5, 6),
                unsolvable = setOf(7, 8, 9),
                boundary   = setOf(4, 5)
            )
            "1010"  -> BoundaryState.Terminal(
                solvable   = setOf(1, 2, 3, 4, 5),
                unsolvable = setOf(6, 7, 8, 9),
                boundary   = setOf(4, 5)
            )
            "100"   -> BoundaryState.Ask(6)  // 2A2C
            "1001"  -> BoundaryState.Terminal(
                solvable   = setOf(1, 2, 3, 4, 6),
                unsolvable = setOf(5, 7, 8, 9),
                boundary   = setOf(3, 4)
            )
            "1000"  -> BoundaryState.Terminal(
                solvable   = setOf(1, 2, 3, 4),
                unsolvable = setOf(5, 6, 7, 8, 9),
                boundary   = setOf(3, 4)
            )
            "0"     -> BoundaryState.Ask(3)  // 2A1
            "01"    -> BoundaryState.Ask(2)  // 1AC
            "011"   -> BoundaryState.Ask(5)  // 2A2
            "0111"  -> BoundaryState.Ask(7)  // 3A
            "01111" -> BoundaryState.Terminal(
                solvable   = setOf(1, 2, 3, 5, 7),
                unsolvable = setOf(4, 6, 8, 9),
                boundary   = setOf(2, 3, 7)
            )
            "01110" -> BoundaryState.Terminal(
                solvable   = setOf(1, 2, 3, 5),
                unsolvable = setOf(4, 6, 7, 8, 9),
                boundary   = setOf(2, 3, 5)
            )
            "0110"  -> BoundaryState.Terminal(
                solvable   = setOf(1, 2, 3),
                unsolvable = setOf(4, 5, 6, 7, 8, 9),
                boundary   = setOf(2, 3)
            )
            "010"   -> BoundaryState.Ask(5)  // 2A2
            "0101"  -> BoundaryState.Ask(7)  // 3A
            "01011" -> BoundaryState.Terminal(
                solvable   = setOf(1, 3, 5, 7),
                unsolvable = setOf(2, 4, 6, 8, 9),
                boundary   = setOf(1, 3, 7)
            )
            "01010" -> BoundaryState.Terminal(
                solvable   = setOf(1, 3, 5),
                unsolvable = setOf(2, 4, 6, 7, 8, 9),
                boundary   = setOf(1, 3, 5)
            )
            "0100"  -> BoundaryState.Terminal(
                solvable   = setOf(1, 3),
                unsolvable = setOf(2, 4, 5, 6, 7, 8, 9),
                boundary   = setOf(1, 3)
            )
            "00"    -> BoundaryState.Ask(1)  // 1A
            "001"   -> BoundaryState.Ask(2)  // 1AC
            "0011"  -> BoundaryState.Terminal(
                solvable   = setOf(1, 2),
                unsolvable = setOf(3, 4, 5, 6, 7, 8, 9),
                boundary   = setOf(1, 2)
            )
            "0010"  -> BoundaryState.Terminal(
                solvable   = setOf(1),
                unsolvable = setOf(2, 3, 4, 5, 6, 7, 8, 9),
                boundary   = setOf(1)
            )
            "000"   -> BoundaryState.Terminal(
                solvable   = emptySet(),
                unsolvable = setOf(1, 2, 3, 4, 5, 6, 7, 8, 9),
                boundary   = setOf(1)
            )
            else -> throw IllegalArgumentException("Unknown response string: $response")
        }
    }

    /**
     * Get the next state for Subtraction (Graph v3)
     */
    fun getSubtractionState(response: String): BoundaryState {
        return when (response) {
            ""      -> BoundaryState.Ask(12) // 2S1B
            "1"     -> BoundaryState.Ask(14) // 3S
            "11"    -> BoundaryState.Ask(16) // 3SB
            "111"   -> BoundaryState.Ask(17) // 3SB2
            "1111"  -> BoundaryState.Terminal(
                solvable   = setOf(10, 11, 12, 13, 14, 15, 16, 17),
                unsolvable = emptySet(),
                boundary   = setOf(17)
            )
            "1110"  -> BoundaryState.Terminal(
                solvable   = setOf(10, 11, 12, 13, 15, 14, 16),
                unsolvable = setOf(17),
                boundary   = setOf(16)
            )
            "110"   -> BoundaryState.Ask(15) // 2S2B
            "1101"  -> BoundaryState.Terminal(
                solvable   = setOf(10, 11, 12, 13, 15, 14),
                unsolvable = setOf(16, 17),
                boundary   = setOf(15, 14)
            )
            "1100"  -> BoundaryState.Terminal(
                solvable   = setOf(10, 11, 12, 13, 14),
                unsolvable = setOf(15, 16, 17),
                boundary   = setOf(12, 14)
            )
            "10"    -> BoundaryState.Ask(13) // 2S2
            "101"   -> BoundaryState.Ask(15) // 2S2B
            "1011"  -> BoundaryState.Terminal(
                solvable   = setOf(10, 11, 12, 13, 15),
                unsolvable = setOf(14, 16, 17),
                boundary   = setOf(13, 15)
            )
            "1010"  -> BoundaryState.Terminal(
                solvable   = setOf(10, 11, 12, 13),
                unsolvable = setOf(15, 14, 16, 17),
                boundary   = setOf(12, 13)
            )
            "100"   -> BoundaryState.Ask(15) // 2S2B
            "1001"  -> BoundaryState.Terminal(
                solvable   = setOf(10, 11, 12, 15),
                unsolvable = setOf(13, 14, 16, 17),
                boundary   = setOf(11, 15)
            )
            "1000"  -> BoundaryState.Terminal(
                solvable   = setOf(10, 11, 12),
                unsolvable = setOf(13, 15, 14, 16, 17),
                boundary   = setOf(11, 12)
            )
            "0"     -> BoundaryState.Ask(11) // 2S1
            "01"    -> BoundaryState.Ask(13) // 2S2
            "011"   -> BoundaryState.Ask(14) // 3S
            "0111"  -> BoundaryState.Terminal(
                solvable   = setOf(10, 11, 13, 14),
                unsolvable = setOf(12, 15, 16, 17),
                boundary   = setOf(11, 14)
            )
            "0110"  -> BoundaryState.Terminal(
                solvable   = setOf(10, 11, 13),
                unsolvable = setOf(12, 15, 14, 16, 17),
                boundary   = setOf(11, 13)
            )
            "010"   -> BoundaryState.Terminal(
                solvable   = setOf(10, 11),
                unsolvable = setOf(12, 13, 15, 14, 16, 17),
                boundary   = setOf(11)
            )
            "00"    -> BoundaryState.Ask(10) // 1S
            "001"   -> BoundaryState.Terminal(
                solvable   = setOf(10),
                unsolvable = setOf(11, 12, 13, 15, 14, 16, 17),
                boundary   = setOf(10)
            )
            "000"   -> BoundaryState.Terminal(
                solvable   = emptySet(),
                unsolvable = setOf(10, 11, 12, 13, 15, 14, 16, 17),
                boundary   = setOf(10)
            )
            else -> throw IllegalArgumentException("Unknown response string: $response")
        }
    }
}