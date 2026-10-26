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
        "1S" to 10, "2S1" to 11, "2S1B" to 12, "2S2" to 13, "3S" to 14, "2S2B" to 15, "3SB" to 16, "3SB2" to 17,
        "T5" to 18, "T10" to 19, "2M1" to 20, "2M1C" to 21, "3M1" to 22, "3M1C" to 23, "3M1C2" to 24,
        "2M2" to 25, "2M2C" to 26, "3M2" to 27, "3M2C" to 28,
        "2D1" to 29, "3D1" to 30, "2D1R" to 31, "3D1R" to 32, "3D1Z" to 33, "4D1R" to 34,
        "3D2" to 35, "3D2R" to 36, "4D2R" to 37
    )

    val ID_TO_NODE = NODE_TO_ID.entries.associate { (k, v) -> v to k }

    fun getAdditionState(response: String): BoundaryState {
        return when (response) {
            ""      -> BoundaryState.Ask(4)
            "1"     -> BoundaryState.Ask(7)
            "11"    -> BoundaryState.Ask(6)
            "111"   -> BoundaryState.Ask(8)
            "1111"  -> BoundaryState.Ask(9)
            "11111" -> BoundaryState.Terminal(setOf(1,2,3,4,5,6,7,8,9), emptySet(), setOf(9))
            "11110" -> BoundaryState.Terminal(setOf(1,2,3,4,5,6,7,8), setOf(9), setOf(8))
            "1110"  -> BoundaryState.Terminal(setOf(1,2,3,4,5,6,7), setOf(8,9), setOf(4,7))
            "110"   -> BoundaryState.Ask(8)
            "1101"  -> BoundaryState.Ask(9)
            "11011" -> BoundaryState.Terminal(setOf(1,2,3,4,5,7,8,9), setOf(6), setOf(4))
            "11010" -> BoundaryState.Terminal(setOf(1,2,3,4,5,7,8), setOf(6,9), setOf(4,8))
            "1100"  -> BoundaryState.Terminal(setOf(1,2,3,4,5,7), setOf(6,8,9), setOf(4,7))
            "10"    -> BoundaryState.Ask(5)
            "101"   -> BoundaryState.Ask(6)
            "1011"  -> BoundaryState.Terminal(setOf(1,2,3,4,5,6), setOf(7,8,9), setOf(4,5))
            "1010"  -> BoundaryState.Terminal(setOf(1,2,3,4,5), setOf(6,7,8,9), setOf(4,5))
            "100"   -> BoundaryState.Ask(6)
            "1001"  -> BoundaryState.Terminal(setOf(1,2,3,4,6), setOf(5,7,8,9), setOf(3,4))
            "1000"  -> BoundaryState.Terminal(setOf(1,2,3,4), setOf(5,6,7,8,9), setOf(3,4))
            "0"     -> BoundaryState.Ask(3)
            "01"    -> BoundaryState.Ask(2)
            "011"   -> BoundaryState.Ask(5)
            "0111"  -> BoundaryState.Ask(7)
            "01111" -> BoundaryState.Terminal(setOf(1,2,3,5,7), setOf(4,6,8,9), setOf(2,3,7))
            "01110" -> BoundaryState.Terminal(setOf(1,2,3,5), setOf(4,6,7,8,9), setOf(2,3,5))
            "0110"  -> BoundaryState.Terminal(setOf(1,2,3), setOf(4,5,6,7,8,9), setOf(2,3))
            "010"   -> BoundaryState.Ask(5)
            "0101"  -> BoundaryState.Ask(7)
            "01011" -> BoundaryState.Terminal(setOf(1,3,5,7), setOf(2,4,6,8,9), setOf(1,3,7))
            "01010" -> BoundaryState.Terminal(setOf(1,3,5), setOf(2,4,6,7,8,9), setOf(1,3,5))
            "0100"  -> BoundaryState.Terminal(setOf(1,3), setOf(2,4,5,6,7,8,9), setOf(1,3))
            "00"    -> BoundaryState.Ask(1)
            "001"   -> BoundaryState.Ask(2)
            "0011"  -> BoundaryState.Terminal(setOf(1,2), setOf(3,4,5,6,7,8,9), setOf(1,2))
            "0010"  -> BoundaryState.Terminal(setOf(1), setOf(2,3,4,5,6,7,8,9), setOf(1))
            "000"   -> BoundaryState.Terminal(emptySet(), setOf(1,2,3,4,5,6,7,8,9), setOf(1))
            else -> throw IllegalArgumentException("Unknown response string: $response")
        }
    }

    fun getSubtractionState(response: String): BoundaryState {
        return when (response) {
            ""      -> BoundaryState.Ask(12)
            "1"     -> BoundaryState.Ask(14)
            "11"    -> BoundaryState.Ask(16)
            "111"   -> BoundaryState.Ask(17)
            "1111"  -> BoundaryState.Terminal(setOf(10,11,12,13,14,15,16,17), emptySet(), setOf(17))
            "1110"  -> BoundaryState.Terminal(setOf(10,11,12,13,15,14,16), setOf(17), setOf(16))
            "110"   -> BoundaryState.Ask(15)
            "1101"  -> BoundaryState.Terminal(setOf(10,11,12,13,15,14), setOf(16,17), setOf(15,14))
            "1100"  -> BoundaryState.Terminal(setOf(10,11,12,13,14), setOf(15,16,17), setOf(12,14))
            "10"    -> BoundaryState.Ask(13)
            "101"   -> BoundaryState.Ask(15)
            "1011"  -> BoundaryState.Terminal(setOf(10,11,12,13,15), setOf(14,16,17), setOf(13,15))
            "1010"  -> BoundaryState.Terminal(setOf(10,11,12,13), setOf(15,14,16,17), setOf(12,13))
            "100"   -> BoundaryState.Ask(15)
            "1001"  -> BoundaryState.Terminal(setOf(10,11,12,15), setOf(13,14,16,17), setOf(11,15))
            "1000"  -> BoundaryState.Terminal(setOf(10,11,12), setOf(13,15,14,16,17), setOf(11,12))
            "0"     -> BoundaryState.Ask(11)
            "01"    -> BoundaryState.Ask(13)
            "011"   -> BoundaryState.Ask(14)
            "0111"  -> BoundaryState.Terminal(setOf(10,11,13,14), setOf(12,15,16,17), setOf(11,14))
            "0110"  -> BoundaryState.Terminal(setOf(10,11,13), setOf(12,15,14,16,17), setOf(11,13))
            "010"   -> BoundaryState.Terminal(setOf(10,11), setOf(12,13,15,14,16,17), setOf(11))
            "00"    -> BoundaryState.Ask(10)
            "001"   -> BoundaryState.Terminal(setOf(10), setOf(11,12,13,15,14,16,17), setOf(10))
            "000"   -> BoundaryState.Terminal(emptySet(), setOf(10,11,12,13,15,14,16,17), setOf(10))
            else -> throw IllegalArgumentException("Unknown response string: $response")
        }
    }

    /**
     * Multiplication (IDs 18–28). Ported from knowledge_boundary_multiplication.py.
     *
     * T5=18 T10=19 2M1=20 2M1C=21 3M1=22 3M1C=23 3M1C2=24
     * 2M2=25 2M2C=26 3M2=27 3M2C=28
     *
     * Edges: T5→T10, T5→2M1, T10→2M1C, 2M1→2M1C, 2M1→3M1, 2M1C→3M1C,
     *        3M1→3M1C, 3M1C→3M1C2, 3M1C2→2M2, 2M2→2M2C, 2M2→3M2,
     *        2M2C→3M2C, 3M2→3M2C
     */
    fun getMultiplicationState(response: String): BoundaryState {
        return when (response) {
            // ── Root ──────────────────────────────────────────────────────
            ""      -> BoundaryState.Ask(23)   // 3M1C

            // ── 3M1C correct ──────────────────────────────────────────────
            "1"     -> BoundaryState.Ask(26)   // 2M2C

            // 3M1C✓ 2M2C✓
            "11"    -> BoundaryState.Ask(27)   // 3M2
            "111"   -> BoundaryState.Ask(28)   // 3M2C
            "1111"  -> BoundaryState.Terminal(
                setOf(18,19,20,21,22,23,24,25,26,27,28), emptySet(), emptySet())
            "1110"  -> BoundaryState.Terminal(
                setOf(18,19,20,21,22,23,24,25,26,27), setOf(28), setOf(26,27))
            "110"   -> BoundaryState.Terminal(
                setOf(18,19,20,21,22,23,24,25,26), setOf(27,28), setOf(25,26))

            // 3M1C✓ 2M2C✗
            "10"    -> BoundaryState.Ask(25)   // 2M2
            "101"   -> BoundaryState.Ask(27)   // 3M2
            "1011"  -> BoundaryState.Terminal(
                setOf(18,19,20,21,22,23,24,25,27), setOf(26,28), setOf(25,27))
            "1010"  -> BoundaryState.Terminal(
                setOf(18,19,20,21,22,23,24,25), setOf(26,27,28), setOf(25))
            "100"   -> BoundaryState.Ask(24)   // 3M1C2
            "1001"  -> BoundaryState.Terminal(
                setOf(18,19,20,21,22,23,24), setOf(25,26,27,28), setOf(24))
            "1000"  -> BoundaryState.Terminal(
                setOf(18,19,20,21,22,23), setOf(24,25,26,27,28), setOf(23))

            // ── 3M1C wrong ────────────────────────────────────────────────
            "0"     -> BoundaryState.Ask(19)   // T10

            // 3M1C✗ T10✓
            "01"    -> BoundaryState.Ask(21)   // 2M1C
            "011"   -> BoundaryState.Ask(22)   // 3M1
            "0111"  -> BoundaryState.Terminal(
                setOf(18,19,20,21,22), setOf(23,24,25,26,27,28), setOf(21,22))
            "0110"  -> BoundaryState.Terminal(
                setOf(18,19,20,21), setOf(22,23,24,25,26,27,28), setOf(20,21))
            "010"   -> BoundaryState.Ask(20)   // 2M1
            "0101"  -> BoundaryState.Ask(22)   // 3M1
            "01011" -> BoundaryState.Terminal(
                setOf(18,19,20,22), setOf(21,23,24,25,26,27,28), setOf(19,20,22))
            "01010" -> BoundaryState.Terminal(
                setOf(18,19,20), setOf(21,22,23,24,25,26,27,28), setOf(19,20))
            "0100"  -> BoundaryState.Terminal(
                setOf(18,19), setOf(20,21,22,23,24,25,26,27,28), setOf(18,19))

            // 3M1C✗ T10✗
            "00"    -> BoundaryState.Ask(20)   // 2M1
            "001"   -> BoundaryState.Ask(22)   // 3M1
            "0011"  -> BoundaryState.Terminal(
                setOf(18,20,22), setOf(19,21,23,24,25,26,27,28), setOf(18,20,22))
            "0010"  -> BoundaryState.Terminal(
                setOf(18,20), setOf(19,21,22,23,24,25,26,27,28), setOf(18,20))
            "000"   -> BoundaryState.Ask(18)   // T5
            "0001"  -> BoundaryState.Terminal(
                setOf(18), setOf(19,20,21,22,23,24,25,26,27,28), setOf(18))
            "0000"  -> BoundaryState.Terminal(
                emptySet(), setOf(18,19,20,21,22,23,24,25,26,27,28), setOf(18))

            else -> throw IllegalArgumentException("Unknown response string: $response")
        }
    }

    /**
     * Division (IDs 29–37). Ported from knowledge_boundary_division.py.
     *
     * 2D1=29 3D1=30 2D1R=31 3D1R=32 3D1Z=33 4D1R=34 3D2=35 3D2R=36 4D2R=37
     *
     * Edges: 2D1→2D1R, 2D1→3D1, 2D1R→3D1R, 3D1→3D1R, 3D1→3D2, 3D2→3D2R,
     *        3D1R→3D1Z, 3D1R→4D1R, 3D1R→3D2R, 4D1R→4D2R, 3D2R→4D2R
     */
    fun getDivisionState(response: String): BoundaryState {
        return when (response) {
            // ── Root ──────────────────────────────────────────────────────
            ""      -> BoundaryState.Ask(32)   // 3D1R

            // ── 3D1R correct ──────────────────────────────────────────────
            "1"     -> BoundaryState.Ask(36)   // 3D2R

            // 3D1R✓ 3D2R✓
            "11"    -> BoundaryState.Ask(33)   // 3D1Z
            "111"   -> BoundaryState.Ask(34)   // 4D1R
            "1111"  -> BoundaryState.Ask(37)   // 4D2R
            "11111" -> BoundaryState.Terminal(
                setOf(29,30,31,32,33,34,35,36,37), emptySet(), emptySet())
            "11110" -> BoundaryState.Terminal(
                setOf(29,30,31,32,33,34,35,36), setOf(37), setOf(36,34))
            "1110"  -> BoundaryState.Terminal(
                setOf(29,30,31,32,33,35,36), setOf(34,37), setOf(32,36))
            "110"   -> BoundaryState.Ask(34)   // 4D1R
            "1101"  -> BoundaryState.Ask(37)   // 4D2R
            "11011" -> BoundaryState.Terminal(
                setOf(29,30,31,32,34,35,36,37), setOf(33), setOf(32))
            "11010" -> BoundaryState.Terminal(
                setOf(29,30,31,32,34,35,36), setOf(33,37), setOf(32,36,34))
            "1100"  -> BoundaryState.Terminal(
                setOf(29,30,31,32,35,36), setOf(33,34,37), setOf(32,36))

            // 3D1R✓ 3D2R✗
            "10"    -> BoundaryState.Ask(35)   // 3D2
            "101"   -> BoundaryState.Ask(33)   // 3D1Z
            "1011"  -> BoundaryState.Ask(34)   // 4D1R
            "10111" -> BoundaryState.Terminal(
                setOf(29,30,31,32,33,34,35), setOf(36,37), setOf(35,32,34))
            "10110" -> BoundaryState.Terminal(
                setOf(29,30,31,32,33,35), setOf(34,36,37), setOf(35,32))
            "1010"  -> BoundaryState.Ask(34)   // 4D1R
            "10101" -> BoundaryState.Terminal(
                setOf(29,30,31,32,34,35), setOf(33,36,37), setOf(35,32,34))
            "10100" -> BoundaryState.Terminal(
                setOf(29,30,31,32,35), setOf(33,34,36,37), setOf(35,32))
            "100"   -> BoundaryState.Ask(33)   // 3D1Z
            "1001"  -> BoundaryState.Ask(34)   // 4D1R
            "10011" -> BoundaryState.Terminal(
                setOf(29,30,31,32,33,34), setOf(35,36,37), setOf(30,32,34))
            "10010" -> BoundaryState.Terminal(
                setOf(29,30,31,32,33), setOf(34,35,36,37), setOf(30,32))
            "1000"  -> BoundaryState.Ask(34)   // 4D1R
            "10001" -> BoundaryState.Terminal(
                setOf(29,30,31,32,34), setOf(33,35,36,37), setOf(30,32,34))
            "10000" -> BoundaryState.Terminal(
                setOf(29,30,31,32), setOf(33,34,35,36,37), setOf(30,32))

            // ── 3D1R wrong ────────────────────────────────────────────────
            "0"     -> BoundaryState.Ask(30)   // 3D1

            // 3D1R✗ 3D1✓
            "01"    -> BoundaryState.Ask(31)   // 2D1R
            "011"   -> BoundaryState.Ask(35)   // 3D2
            "0111"  -> BoundaryState.Terminal(
                setOf(29,30,31,35), setOf(32,33,34,36,37), setOf(31,30,35))
            "0110"  -> BoundaryState.Terminal(
                setOf(29,30,31), setOf(32,33,34,35,36,37), setOf(31,30))
            "010"   -> BoundaryState.Ask(35)   // 3D2
            "0101"  -> BoundaryState.Terminal(
                setOf(29,30,35), setOf(31,32,33,34,36,37), setOf(29,30,35))
            "0100"  -> BoundaryState.Terminal(
                setOf(29,30), setOf(31,32,33,34,35,36,37), setOf(29,30))

            // 3D1R✗ 3D1✗
            "00"    -> BoundaryState.Ask(29)   // 2D1
            "001"   -> BoundaryState.Ask(31)   // 2D1R
            "0011"  -> BoundaryState.Terminal(
                setOf(29,31), setOf(30,32,33,34,35,36,37), setOf(29,31))
            "0010"  -> BoundaryState.Terminal(
                setOf(29), setOf(30,31,32,33,34,35,36,37), setOf(29))
            "000"   -> BoundaryState.Terminal(
                emptySet(), setOf(29,30,31,32,33,34,35,36,37), setOf(29))

            else -> throw IllegalArgumentException("Unknown response string: $response")
        }
    }
}