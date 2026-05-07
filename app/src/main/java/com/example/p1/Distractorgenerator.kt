package com.example.p1

import kotlin.math.pow

object DistractorGenerator {

    fun generate(kcId: Int, num1: Int, num2: Int, correctAnswer: Int, needed: Int = 3): List<Int> {
        val a = maxOf(num1, num2)
        val b = minOf(num1, num2)

        val raw = when (kcId) {
            // ── ADDITION distractors ─────────────────────────────────────────
            // 1A: off-by-one
            1     -> offByOneGeneric(correctAnswer, listOf(-2, -1, 1, 2))
            // 1AC: off-by-one (carry confusion)
            2     -> offByOneGeneric(correctAnswer, listOf(-2, -1, 1, 2))
            // 2A1: wrong place value + off-by-one
            3     -> addWrongPlaceValue(a, b, correctAnswer) + offByOneGeneric(correctAnswer, listOf(-1, 1))
            // 2A1C: wrong place value + off-by-one
            4     -> addWrongPlaceValue(a, b, correctAnswer) + offByOneGeneric(correctAnswer, listOf(-1, 1))
            // 2A2: wrong place value + off-by-one multidigit
            5     -> addWrongPlaceValue(a, b, correctAnswer) + offByOneMultidigit(correctAnswer)
            // 2A2C: wrong place value + off-by-one multidigit
            6     -> addWrongPlaceValue(a, b, correctAnswer) + offByOneMultidigit(correctAnswer)
            // 3A: wrong place value + off-by-one multidigit
            7     -> addWrongPlaceValue(a, b, correctAnswer) + offByOneMultidigit(correctAnswer)
            // 3AC: wrong place value + off-by-one multidigit
            8     -> addWrongPlaceValue(a, b, correctAnswer) + offByOneMultidigit(correctAnswer)
            // 3AC2: wrong place value + off-by-one multidigit
            9     -> addWrongPlaceValue(a, b, correctAnswer) + offByOneMultidigit(correctAnswer)

            // ── SUBTRACTION distractors ─────────────────────────────────────
            // 1S: off-by-one + adding instead of subtracting
            10    -> offByOneGeneric(correctAnswer, listOf(-2, -1, 1, 2)) + listOf(num1 + num2)
            // 2S1: off-by-one + wrong place value + adding instead
            11    -> subWrongPlaceValue(num1, num2, correctAnswer) + offByOneGeneric(correctAnswer, listOf(-1, 1)) + listOf(num1 + num2)
            // 2S1B: borrow confusion + off-by-one + adding instead
            12    -> borrowConfusion(num1, num2, correctAnswer) + offByOneGeneric(correctAnswer, listOf(-1, 1)) + listOf(num1 + num2)
            // 2S2: wrong place value + off-by-one + adding instead
            13    -> subWrongPlaceValue(num1, num2, correctAnswer) + offByOneMultidigit(correctAnswer) + listOf(num1 + num2)
            // 3S: wrong place value + off-by-one multidigit
            14    -> subWrongPlaceValue(num1, num2, correctAnswer) + offByOneMultidigit(correctAnswer) + listOf(num1 + num2)
            // 2S2B: borrow confusion + off-by-one + adding instead
            15    -> borrowConfusion(num1, num2, correctAnswer) + offByOneMultidigit(correctAnswer) + listOf(num1 + num2)
            // 3SB: borrow confusion + off-by-one multidigit
            16    -> borrowConfusion(num1, num2, correctAnswer) + offByOneMultidigit(correctAnswer) + listOf(num1 + num2)
            // 3SB2: borrow confusion + off-by-one multidigit
            17    -> borrowConfusion(num1, num2, correctAnswer) + offByOneMultidigit(correctAnswer) + listOf(num1 + num2)

            else  -> offByOneGeneric(correctAnswer, listOf(-1, 1, 2, 3))
        }

        val filtered = raw
            .filter { it >= 0 && it != correctAnswer }
            .distinct()
            .toMutableList()

        var offset = 1
        while (filtered.size < needed) {
            val candidate = correctAnswer + offset
            if (candidate != correctAnswer && candidate >= 0 && candidate !in filtered)
                filtered.add(candidate)
            offset++
        }

        return filtered.take(needed)
    }

    private fun offByOneGeneric(correct: Int, offsets: List<Int>): List<Int> =
        offsets.map { correct + it }.filter { it >= 0 }

    private fun offByOneMultidigit(correct: Int): List<Int> {
        val s = correct.toString()
        val result = mutableListOf<Int>()
        for (i in s.indices) {
            val digit = s[i].digitToInt()
            for (offset in listOf(-1, 1)) {
                val newDigit = digit + offset
                if (newDigit in 0..9) {
                    val place = s.length - 1 - i
                    result.add(correct + offset * 10.0.pow(place).toInt())
                }
            }
        }
        return result.filter { it >= 0 }
    }

    private fun addWrongPlaceValue(num1: Int, num2: Int, correct: Int): List<Int> {
        val diff = num1.toString().length - num2.toString().length
        val result = mutableListOf<Int>()
        for (i in 0 until diff) {
            result.add(num1 + num2 * 10.0.pow(i + 1).toInt())
        }
        return result.filter { it >= 0 && it != correct }
    }

    /** Subtraction wrong place value — subtract from wrong column */
    private fun subWrongPlaceValue(num1: Int, num2: Int, correct: Int): List<Int> {
        val diff = num1.toString().length - num2.toString().length
        val result = mutableListOf<Int>()
        for (i in 0 until diff) {
            val wrong = num1 - num2 * 10.0.pow(i + 1).toInt()
            if (wrong >= 0) result.add(wrong)
        }
        // Also: subtract digits in wrong order (b - a instead of a - b per column)
        val reverseResult = num2 - num1
        if (reverseResult >= 0 && reverseResult != correct) result.add(reverseResult)
        return result.filter { it >= 0 && it != correct }
    }

    /** Borrow confusion — forgot to borrow, giving wrong digit at borrow position */
    private fun borrowConfusion(num1: Int, num2: Int, correct: Int): List<Int> {
        val result = mutableListOf<Int>()
        val s1 = num1.toString().padStart(3, '0')
        val s2 = num2.toString().padStart(3, '0')
        // Simulate "forgot to borrow" — just subtract digits without borrowing
        var noBorrow = 0
        for (i in s1.indices) {
            val d1 = s1[i].digitToInt()
            val d2 = s2[i].digitToInt()
            val d = if (d1 >= d2) d1 - d2 else d2 - d1  // takes absolute diff per digit
            noBorrow = noBorrow * 10 + d
        }
        if (noBorrow != correct && noBorrow >= 0) result.add(noBorrow)

        // Also: add instead of subtract
        result.add(num1 + num2)

        return result.filter { it >= 0 && it != correct }
    }

    /**
     * Identifies the misconception behind a wrong answer.
     * Returns a human-readable label for the CSV log.
     */
    fun getMisconception(kcId: Int, num1: Int, num2: Int, correctAnswer: Int, selectedAnswer: Int): String {
        if (selectedAnswer == correctAnswer) return ""

        val diff = selectedAnswer - correctAnswer
        val op = KnowledgeRepository.getOperationType(kcId)

        // Check: did the student add instead of subtract (or vice versa)?
        if (op == "-" && selectedAnswer == num1 + num2) return "Add-instead-of-subtract"
        if (op == "+" && selectedAnswer == num1 - num2) return "Subtract-instead-of-add"

        // Check: off-by-one error
        if (diff == 1 || diff == -1) return "Off-by-one"
        if (diff == 2 || diff == -2) return "Off-by-two"

        // Check: borrow/carry confusion (off by 10 at some place)
        val absDiff = kotlin.math.abs(diff)
        if (absDiff == 10 || absDiff == 100) return "Carry/Borrow-error"

        // Check: wrong place value — digit-level off by a power of 10
        val correctStr = correctAnswer.toString()
        val selectedStr = selectedAnswer.toString()
        if (correctStr.length == selectedStr.length) {
            var digitDiffs = 0
            for (i in correctStr.indices) {
                if (correctStr[i] != selectedStr[i]) digitDiffs++
            }
            if (digitDiffs == 1) return "Single-digit-error"
        }

        // Check: forgot borrow (subtraction KCs with borrow)
        if (kcId in listOf(12, 15, 16, 17)) {
            val s1 = num1.toString().padStart(3, '0')
            val s2 = num2.toString().padStart(3, '0')
            var noBorrow = 0
            for (i in s1.indices) {
                val d1 = s1[i].digitToInt()
                val d2 = s2[i].digitToInt()
                val d = if (d1 >= d2) d1 - d2 else d2 - d1
                noBorrow = noBorrow * 10 + d
            }
            if (selectedAnswer == noBorrow) return "Forgot-borrow"
        }

        // Check: forgot carry (addition KCs with carry)
        if (kcId in listOf(2, 4, 6, 8, 9)) {
            if (diff == -10 || diff == -100) return "Forgot-carry"
        }

        return "Other-error"
    }
}