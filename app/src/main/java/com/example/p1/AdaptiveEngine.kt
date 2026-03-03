package com.example.p1

class AdaptiveEngine {

    private var currentKC: Int = 1

    private val windowSize = 5
    private val masteryThreshold = 0.8
    private val recentAnswers = mutableListOf<Boolean>()

    private val student = StudentModel()

    var correctAnswer: Int = 0
        private set

    // ---------------------------
    // ZPD: Get available KCs

    // ---------------------------
    fun getAvailableKCs(): List<Int> {

        return KnowledgeRepository.components.values.filter { kc ->

            if (student.isMastered(kc.id)) return@filter false

            kc.prerequisites.all { prereq ->
                student.isMastered(prereq)
            }

        }.map { it.id }.sorted()
    }

    // ---------------------------
    // Question Generator
    // ---------------------------
    fun generateQuestion(): Pair<String, List<Int>> {

        val availableKCs = getAvailableKCs()

        if (availableKCs.isEmpty()) {
            return Pair("🎉 All concepts mastered!", listOf(0, 0, 0, 0))
        }

        currentKC = availableKCs.first()

        val (num1, num2) = generateNumbersForKC(currentKC)

        correctAnswer = num1 + num2
        val questionText = "$num1 + $num2 = ?"

        val answers = mutableSetOf<Int>()
        answers.add(correctAnswer)

        while (answers.size < 4) {
            answers.add(
                (correctAnswer + (-5..5).random()).coerceAtLeast(1)
            )
        }

        return Pair(questionText, answers.shuffled())
    }

    // ---------------------------
    // Mastery Update
    // ---------------------------
    fun submitAnswer(selected: Int): String {

        val isCorrect = selected == correctAnswer

        recentAnswers.add(isCorrect)

        if (recentAnswers.size > windowSize) {
            recentAnswers.removeAt(0)
        }

        if (recentAnswers.size == windowSize) {

            val accuracy =
                recentAnswers.count { it }.toDouble() / windowSize

            if (accuracy >= masteryThreshold) {
                student.setMastered(currentKC)
                recentAnswers.clear()
            }
        }

        return if (isCorrect) {
            "Correct! 🎉"
        } else {
            "Wrong! Correct answer: $correctAnswer"
        }
    }

    // ---------------------------
    // Number Generator Per KC
    // ---------------------------
    private fun generateNumbersForKC(kcId: Int): Pair<Int, Int> {

        fun randomNDigit(n: Int): Int {
            val min = Math.pow(10.0, (n - 1).toDouble()).toInt()
            val max = Math.pow(10.0, n.toDouble()).toInt() - 1
            return (min..max).random()
        }

        return when (kcId) {

            1 -> { // 1-digit no carry
                var a: Int
                var b: Int
                do {
                    a = (1..9).random()
                    b = (1..9).random()
                } while (a + b >= 10)
                Pair(a, b)
            }

            2 -> { // 1-digit with carry
                var a: Int
                var b: Int
                do {
                    a = (1..9).random()
                    b = (1..9).random()
                } while (a + b < 10)
                Pair(a, b)
            }

            3, 4 -> { // 2-digit no carry
                var a: Int
                var b: Int
                do {
                    a = randomNDigit(2)
                    b = randomNDigit(2)
                } while (hasCarry(a, b))
                Pair(a, b)
            }

            5, 6 -> { // 2-digit with carry
                var a: Int
                var b: Int
                do {
                    a = randomNDigit(2)
                    b = randomNDigit(2)
                } while (!hasCarry(a, b))
                Pair(a, b)
            }

            7, 8 -> { // 3-digit no carry
                var a: Int
                var b: Int
                do {
                    a = randomNDigit(3)
                    b = randomNDigit(3)
                } while (hasCarry(a, b))
                Pair(a, b)
            }

            9, 10 -> { // 3-digit with carry
                var a: Int
                var b: Int
                do {
                    a = randomNDigit(3)
                    b = randomNDigit(3)
                } while (!hasCarry(a, b))
                Pair(a, b)
            }

            else -> { // 4-digit
                var a: Int
                var b: Int
                do {
                    a = randomNDigit(4)
                    b = randomNDigit(4)
                } while (
                    if (kcId in listOf(11, 12))
                        hasCarry(a, b)
                    else
                        !hasCarry(a, b)
                )
                Pair(a, b)
            }
        }
    }

    private fun hasCarry(a: Int, b: Int): Boolean {

        var x = a
        var y = b

        while (x > 0 || y > 0) {
            if ((x % 10) + (y % 10) >= 10) return true
            x /= 10
            y /= 10
        }
        return false
    }
}