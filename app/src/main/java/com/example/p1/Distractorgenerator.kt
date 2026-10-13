package com.example.p1

import kotlin.math.pow

object DistractorGenerator {

    fun generate(kcId: Int, num1: Int, num2: Int, correctAnswer: Int, needed: Int = 3): List<Int> {
        val a = maxOf(num1, num2)
        val b = minOf(num1, num2)

        val raw = when (kcId) {
            // ── ADDITION ─────────────────────────────────────────────────
            1     -> offByOneGeneric(correctAnswer, listOf(-2, -1, 1, 2))
            2     -> offByOneGeneric(correctAnswer, listOf(-2, -1, 1, 2))
            3     -> addWrongPlaceValue(a, b, correctAnswer) + offByOneGeneric(correctAnswer, listOf(-1, 1))
            4     -> addWrongPlaceValue(a, b, correctAnswer) + offByOneGeneric(correctAnswer, listOf(-1, 1))
            5     -> addWrongPlaceValue(a, b, correctAnswer) + offByOneMultidigit(correctAnswer)
            6     -> addWrongPlaceValue(a, b, correctAnswer) + offByOneMultidigit(correctAnswer)
            7     -> addWrongPlaceValue(a, b, correctAnswer) + offByOneMultidigit(correctAnswer)
            8     -> addWrongPlaceValue(a, b, correctAnswer) + offByOneMultidigit(correctAnswer)
            9     -> addWrongPlaceValue(a, b, correctAnswer) + offByOneMultidigit(correctAnswer)

            // ── SUBTRACTION ──────────────────────────────────────────────
            10    -> offByOneGeneric(correctAnswer, listOf(-2, -1, 1, 2)) + listOf(num1 + num2)
            11    -> subWrongPlaceValue(num1, num2, correctAnswer) + offByOneGeneric(correctAnswer, listOf(-1, 1)) + listOf(num1 + num2)
            12    -> borrowConfusion(num1, num2, correctAnswer) + offByOneGeneric(correctAnswer, listOf(-1, 1)) + listOf(num1 + num2)
            13    -> subWrongPlaceValue(num1, num2, correctAnswer) + offByOneMultidigit(correctAnswer) + listOf(num1 + num2)
            14    -> subWrongPlaceValue(num1, num2, correctAnswer) + offByOneMultidigit(correctAnswer) + listOf(num1 + num2)
            15    -> borrowConfusion(num1, num2, correctAnswer) + offByOneMultidigit(correctAnswer) + listOf(num1 + num2)
            16    -> borrowConfusion(num1, num2, correctAnswer) + offByOneMultidigit(correctAnswer) + listOf(num1 + num2)
            17    -> borrowConfusion(num1, num2, correctAnswer) + offByOneMultidigit(correctAnswer) + listOf(num1 + num2)

            // ── MULTIPLICATION ───────────────────────────────────────────
            18, 19 -> offByOneGeneric(correctAnswer, listOf(-2, -1, 1, 2)) +
                    listOf(num1 + num2, num1 * (num2 + 1), num1 * maxOf(num2 - 1, 0))
            20, 22 -> offByOneGeneric(correctAnswer, listOf(-1, 1)) + listOf(num1 + num2)
            21, 23, 24 -> offByOneGeneric(correctAnswer, listOf(-1, 1)) +
                    listOf(num1 + num2, correctAnswer - 10, correctAnswer + 10)
            25, 27 -> offByOneMultidigit(correctAnswer) + listOf(num1 + num2)
            26, 28 -> offByOneMultidigit(correctAnswer) +
                    listOf(num1 + num2, correctAnswer - 10, correctAnswer + 10)

            // ── DIVISION ──────────────────────────────────────────────────
            29, 30 -> offByOneGeneric(correctAnswer, listOf(-2, -1, 1, 2)) +
                    listOf(num1 - num2, num1 + num2)
            31, 32, 34 -> offByOneGeneric(correctAnswer, listOf(-2, -1, 1, 2)) +
                    listOf(num1 - num2, num1 + num2, correctAnswer + 10, correctAnswer - 10)
            33 -> run {
                val qStr = correctAnswer.toString()
                val noZero = qStr.replace("0", "").toIntOrNull() ?: correctAnswer
                offByOneGeneric(correctAnswer, listOf(-1, 1)) + listOf(noZero, num1 - num2, num1 + num2)
            }
            35, 36, 37 -> offByOneGeneric(correctAnswer, listOf(-1, 1)) + offByOneMultidigit(correctAnswer) +
                    listOf(num1 - num2, num1 + num2)

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

    private fun subWrongPlaceValue(num1: Int, num2: Int, correct: Int): List<Int> {
        val diff = num1.toString().length - num2.toString().length
        val result = mutableListOf<Int>()
        for (i in 0 until diff) {
            val wrong = num1 - num2 * 10.0.pow(i + 1).toInt()
            if (wrong >= 0) result.add(wrong)
        }
        val reverseResult = num2 - num1
        if (reverseResult >= 0 && reverseResult != correct) result.add(reverseResult)
        return result.filter { it >= 0 && it != correct }
    }

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

        result.add(num1 + num2)

        return result.filter { it >= 0 && it != correct }
    }

    fun getMisconception(kcId: Int, num1: Int, num2: Int, correctAnswer: Int, selectedAnswer: Int): String {
        if (selectedAnswer == correctAnswer) return ""

        val diff = selectedAnswer - correctAnswer
        val op = KnowledgeRepository.getOperationType(kcId)

        if (op == "-" && selectedAnswer == num1 + num2) return "Add-instead-of-subtract"
        if (op == "+" && selectedAnswer == num1 - num2) return "Subtract-instead-of-add"
        if (op == "×" && selectedAnswer == num1 + num2) return "Add-instead-of-multiply"
        if (op == "÷" && selectedAnswer == num1 - num2) return "Subtract-instead-of-divide"
        if (op == "÷" && selectedAnswer == num1 + num2) return "Add-instead-of-divide"

        if (op == "×") {
            if (kcId == 18 || kcId == 19) {
                for (wrongFactor in 1..10) {
                    if (wrongFactor != num2 && num1 * wrongFactor == selectedAnswer) return "Confused-table"
                }
            }

            if (num1 * (num2 + 1) == selectedAnswer || num1 * (num2 - 1) == selectedAnswer) return "Off-by-one-factor"

            if (kcId in listOf(21, 23, 24, 26, 28)) {
                val noCarryProduct = run {
                    var x = num1; var result = 0; var place = 1
                    while (x > 0) {
                        val digit = x % 10
                        result += (digit * num2 % 10) * place
                        place *= 10
                        x /= 10
                    }
                    result
                }
                if (selectedAnswer == noCarryProduct) return "Forgot-carry"
            }

            if (kcId in listOf(25, 26, 27, 28)) {
                val tens = num2 / 10
                val ones = num2 % 10
                if (tens > 0) {
                    val wrongPlaceProduct = num1 * ones + num1 * tens
                    if (selectedAnswer == wrongPlaceProduct) return "Wrong-place-value-multiply"
                }
                if (selectedAnswer == num1 * ones) return "Only-multiplied-ones"
                if (tens > 0 && selectedAnswer == num1 * tens) return "Only-multiplied-tens"

                if (tens > 0) {
                    val partial1 = num1 * ones
                    val partial2 = num1 * tens * 10
                    val noCarryAdd = run {
                        var x = partial1; var y = partial2; var result = 0; var place = 1
                        while (x > 0 || y > 0) {
                            val d = (x % 10 + y % 10) % 10
                            result += d * place
                            place *= 10
                            x /= 10; y /= 10
                        }
                        result
                    }
                    if (selectedAnswer == noCarryAdd) return "Forgot-carry-in-addition"
                }
            }
        }

        if (op == "÷") {
            if (diff == 1 || diff == -1) return "Off-by-one"

            if (kcId == 33) {
                val quotientStr = correctAnswer.toString()
                if (quotientStr.contains('0')) {
                    val noZeroQuotient = quotientStr.replace("0", "").toIntOrNull()
                    if (noZeroQuotient != null && selectedAnswer == noZeroQuotient) return "Forgot-zero-in-quotient"
                }
            }

            val correctQuotient = num1 / num2

            run {
                val noBorrowQuotientDigit = run {
                    var remainder = 0
                    var result = 0
                    for (ch in num1.toString()) {
                        val digit = ch.digitToInt()
                        remainder = remainder * 10 + digit
                        val qDigit = if (remainder >= num2) remainder / num2 else 0
                        remainder = kotlin.math.abs(remainder - qDigit * num2)
                        result = result * 10 + qDigit
                    }
                    result
                }
                if (selectedAnswer == noBorrowQuotientDigit && noBorrowQuotientDigit != correctAnswer) {
                    return "Subtraction-error-during-division"
                }
            }

            if (selectedAnswer == correctQuotient + 10 || selectedAnswer == correctQuotient - 10) {
                return "Addition-error-during-division"
            }
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

        if (kcId in listOf(2, 4, 6, 8, 9)) {
            if (diff == -10 || diff == -100) return "Forgot-carry"
        }

        return "Other-error"
    }
}