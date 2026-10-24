package com.example.p1

import kotlin.math.pow

object DistractorGenerator {

    fun generate(kcId: Int, num1: Int, num2: Int, correctAnswer: Int, needed: Int = 3): List<Int> {
        val a = maxOf(num1, num2)
        val b = minOf(num1, num2)

        val raw = when (kcId) {
            // ── ADDITION distractors ─────────────────────────────────────────
            1     -> offByOneGeneric(correctAnswer, listOf(-2, -1, 1, 2))
            // carry KCs: forgot carry / extra carry first, then off-by-one
            2     -> carryConfusion(a, b, correctAnswer) + offByOneGeneric(correctAnswer, listOf(-1, 1, 2))
            3     -> offByOneGeneric(correctAnswer, listOf(-1, 1)) + addWrongPlaceValue(a, b, correctAnswer)
            4     -> carryConfusion(a, b, correctAnswer) + offByOneGeneric(correctAnswer, listOf(-1, 1)) + addWrongPlaceValue(a, b, correctAnswer)
            5     -> offByOneMultidigit(correctAnswer) + addWrongPlaceValue(a, b, correctAnswer)
            6     -> carryConfusion(a, b, correctAnswer) + offByOneMultidigit(correctAnswer)
            7     -> offByOneMultidigit(correctAnswer) + addWrongPlaceValue(a, b, correctAnswer)
            8     -> carryConfusion(a, b, correctAnswer) + offByOneMultidigit(correctAnswer)
            9     -> carryConfusion(a, b, correctAnswer) + offByOneMultidigit(correctAnswer)

            // ── SUBTRACTION distractors ─────────────────────────────────────
            10    -> offByOneGeneric(correctAnswer, listOf(-2, -1, 1, 2)) + listOf(num1 + num2)
            11    -> subWrongPlaceValue(num1, num2, correctAnswer) + offByOneGeneric(correctAnswer, listOf(-1, 1)) + listOf(num1 + num2)
            12    -> borrowConfusion(num1, num2, correctAnswer) + offByOneGeneric(correctAnswer, listOf(-1, 1))
            13    -> listOf(num1 + num2) + offByOneMultidigit(correctAnswer) + subWrongPlaceValue(num1, num2, correctAnswer)
            14    -> listOf(num1 + num2) + offByOneMultidigit(correctAnswer) + subWrongPlaceValue(num1, num2, correctAnswer)
            15    -> borrowConfusion(num1, num2, correctAnswer) + offByOneMultidigit(correctAnswer)
            16    -> borrowConfusion(num1, num2, correctAnswer) + offByOneMultidigit(correctAnswer) + subWrongPlaceValue(num1, num2, correctAnswer)
            17    -> borrowConfusion(num1, num2, correctAnswer) + offByOneMultidigit(correctAnswer) + subWrongPlaceValue(num1, num2, correctAnswer)

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

    /**
     * Multiplication / division distractors (no KCs exist for these yet,
     * so they are keyed by operator: "×" or "÷").
     */
    fun generateForOp(op: String, num1: Int, num2: Int, correctAnswer: Int, needed: Int = 3): List<Int> {
        val raw = when (op) {
            "×", "x", "*" -> mulDistractors(num1, num2, correctAnswer)
            "÷", "/"      -> divDistractors(num1, num2, correctAnswer)
            else          -> offByOneGeneric(correctAnswer, listOf(-1, 1, 2))
        }
        val filtered = raw.filter { it >= 0 && it != correctAnswer }.distinct().toMutableList()
        var offset = 1
        while (filtered.size < needed) {
            val c = correctAnswer + offset
            if (c >= 0 && c !in filtered) filtered.add(c)
            offset++
        }
        return filtered.take(needed)
    }

    private fun mulDistractors(a: Int, b: Int, correct: Int): List<Int> {
        val r = mutableListOf<Int>()
        r.add(a * (b + 1))              // table neighbour
        if (b > 1) r.add(a * (b - 1))   // table neighbour
        r.add((a + 1) * b)
        if (a > 1) r.add((a - 1) * b)
        r.add(a + b)                    // added instead of multiplied
        if (correct >= 10) { r.add(correct + 10); r.add(correct - 10) }  // carry / place error
        r.add(correct + 1); r.add(correct - 1)
        return r
    }

    private fun divDistractors(a: Int, b: Int, correct: Int): List<Int> {
        val r = mutableListOf<Int>()
        r.add(correct + 1); r.add(correct - 1)
        r.add(a - b)                    // subtracted instead of divided
        r.add(a * b)                    // multiplied instead of divided
        r.add(b)                        // returned divisor
        r.add(correct * 10)             // place-value slip
        if (correct >= 10) r.add(correct / 10)
        r.add(correct + 2)
        return r
    }

    fun getMisconceptionForOp(op: String, num1: Int, num2: Int, correct: Int, selected: Int): String {
        if (selected == correct) return ""
        val diff = selected - correct
        return when (op) {
            "×", "x", "*" -> when {
                selected == num1 + num2 -> "Add-instead-of-multiply"
                selected == num1 * (num2 + 1) || selected == num1 * (num2 - 1) ||
                        selected == (num1 + 1) * num2 || selected == (num1 - 1) * num2 -> "Table-neighbour"
                diff == 10 || diff == -10 || diff == 100 || diff == -100 -> "Carry/Place-error"
                diff == 1 || diff == -1 -> "Off-by-one"
                else -> "Other-error"
            }
            "÷", "/" -> when {
                selected == num1 * num2 -> "Multiply-instead-of-divide"
                selected == num1 - num2 -> "Subtract-instead-of-divide"
                selected == num2 -> "Returned-divisor"
                diff == 1 || diff == -1 -> "Off-by-one"
                selected == correct * 10 || (correct >= 10 && selected == correct / 10) -> "Place-value-error"
                else -> "Other-error"
            }
            else -> "Other-error"
        }
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

    /**
     * Addition carry confusion:
     *  - forgot carry: column sums mod 10, carry dropped  (98+6 → 94)
     *  - extra carry:  correct + 10                        (98+6 → 114)
     *  - carry written in place instead of added: correct − 10×k for each carry
     */
    private fun carryConfusion(num1: Int, num2: Int, correct: Int): List<Int> {
        val result = mutableListOf<Int>()
        var x = num1; var y = num2; var place = 1; var noCarry = 0
        while (x > 0 || y > 0) {
            noCarry += ((x % 10 + y % 10) % 10) * place
            x /= 10; y /= 10; place *= 10
        }
        if (noCarry != correct) result.add(noCarry)
        result.add(correct + 10)
        if (correct >= 10) result.add(correct - 10)
        return result.filter { it >= 0 && it != correct }
    }

    /**
     * Subtraction wrong place value — second number subtracted from the wrong
     * column (shifted left by 1 or 2 places): num1 − num2×10, num1 − num2×100.
     */
    private fun subWrongPlaceValue(num1: Int, num2: Int, correct: Int): List<Int> {
        val result = mutableListOf<Int>()
        for (k in 1..2) {
            val wrong = num1 - num2 * 10.0.pow(k).toInt()
            if (wrong >= 0) result.add(wrong)
        }
        val reverseResult = num2 - num1
        if (reverseResult >= 0 && reverseResult != correct) result.add(reverseResult)
        return result.filter { it >= 0 && it != correct }
    }

    /** Borrow confusion — forgot to borrow, giving wrong digit at borrow position */
    private fun borrowConfusion(num1: Int, num2: Int, correct: Int): List<Int> {
        val result = mutableListOf<Int>()
        val s1 = num1.toString().padStart(3, '0')
        val s2 = num2.toString().padStart(3, '0')
        var noBorrow = 0
        for (i in s1.indices) {
            val d1 = s1[i].digitToInt()
            val d2 = s2[i].digitToInt()
            val d = if (d1 >= d2) d1 - d2 else d2 - d1
            noBorrow = noBorrow * 10 + d
        }
        if (noBorrow != correct && noBorrow >= 0) result.add(noBorrow)

        // borrowed but forgot to reduce the next digit
        result.add(correct + 10)
        // reduced next digit without borrowing
        if (correct >= 10) result.add(correct - 10)
        // added instead of subtracted
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

        if (op == "-" && selectedAnswer == num1 + num2) return "Add-instead-of-subtract"
        if (op == "+" && selectedAnswer == num1 - num2) return "Subtract-instead-of-add"

        if (op == "-" && (selectedAnswer == num1 - num2 * 10 || selectedAnswer == num1 - num2 * 100))
            return "Wrong-place-value"

        // Addition: forgot carry / extra carry
        if (op == "+" && kcId in listOf(2, 4, 6, 8, 9)) {
            if (diff == -10 || diff == -100) return "Forgot-carry"
            if (diff == 10 || diff == 100) return "Extra-carry"
        }

        // Subtraction with borrow: borrowed but didn't reduce / reduced without borrowing
        if (op == "-" && kcId in listOf(12, 15, 16, 17)) {
            if (diff == 10 || diff == 100) return "Borrow-not-reduced"
            if (diff == -10 || diff == -100) return "Extra-borrow"
        }

        if (diff == 1 || diff == -1) return "Off-by-one"
        if (diff == 2 || diff == -2) return "Off-by-two"

        val absDiff = kotlin.math.abs(diff)
        if (absDiff == 10 || absDiff == 100) return "Carry/Borrow-error"

        val correctStr = correctAnswer.toString()
        val selectedStr = selectedAnswer.toString()
        if (correctStr.length == selectedStr.length) {
            var digitDiffs = 0
            for (i in correctStr.indices) {
                if (correctStr[i] != selectedStr[i]) digitDiffs++
            }
            if (digitDiffs == 1) return "Single-digit-error"
        }

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

        return "Other-error"
    }
}