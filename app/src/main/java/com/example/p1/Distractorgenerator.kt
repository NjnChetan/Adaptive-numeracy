package com.example.p1

import kotlin.math.pow

object DistractorGenerator {

    fun generate(kcId: Int, num1: Int, num2: Int, correctAnswer: Int, needed: Int = 3): List<Int> {
        val a = maxOf(num1, num2)
        val b = minOf(num1, num2)

        val raw = when (kcId) {
            1     -> offByOneGeneric(correctAnswer, listOf(-2, -1, 1, 2))
            2     -> addWrongPlaceValue(a, b, correctAnswer) + offByOneMultidigit(correctAnswer)
            3     -> offByOneGeneric(correctAnswer, listOf(-2, -1, 1, 2))
            4     -> addWrongPlaceValue(a, b, correctAnswer) + offByOneGeneric(correctAnswer, listOf(-1, 1))
            5     -> addWrongPlaceValue(a, b, correctAnswer) + offByOneGeneric(correctAnswer, listOf(-1, 1))
            6     -> addWrongPlaceValue(a, b, correctAnswer) + offByOneMultidigit(correctAnswer)
            7     -> addWrongPlaceValue(a, b, correctAnswer) + offByOneMultidigit(correctAnswer)
            8     -> addWrongPlaceValue(a, b, correctAnswer) + offByOneMultidigit(correctAnswer)
            9     -> addWrongPlaceValue(a, b, correctAnswer) + offByOneMultidigit(correctAnswer)
            10    -> addWrongPlaceValue(a, b, correctAnswer) + offByOneMultidigit(correctAnswer)
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
}