package com.example.p1

import kotlin.math.ln

class AdaptiveEngine {

    val student = StudentModel()

    private val fpr  = 0.001
    private val beta = CUSUMDetector.thresholdFromFPR(fpr)  // ≈ 6.9

    private val bandit         = KLUCBBandit()
    private val cusumDetectors = mutableMapOf<Int, CUSUMDetector>()
    private var ts             = 0
    private var currentKC      = -1

    // Active operation set — set by MainActivity before generateQuestion()
    // Defaults to addition only; updated when user selects operations.
    var activeOps: Set<String> = setOf("+")

    // ── Focus mode ────────────────────────────────────────────────────────────
    private var focusKC:            Int? = null
    private var focusQuestionCount: Int  = 0
    private var consecutiveWrong:   Int  = 0
    private val MIN_FOCUS                = 5
    private val FRUSTRATION_LIMIT        = 12

    var correctAnswer: Int = 0
        private set

    // ── ZPD / arm initialisation ──────────────────────────────────────────────
    fun initZPD() {
        for (kcId in KnowledgeRepository.getZPD(student, activeOps)) addArmIfNeeded(kcId)
    }

    private fun addArmIfNeeded(kcId: Int) {
        if (!bandit.hasArm(kcId)) {
            bandit.addArm(kcId, ts)
            cusumDetectors[kcId] = CUSUMDetector(
                pg        = KnowledgeRepository.getGuessProb(kcId),
                ps        = KnowledgeRepository.getSlipProb(kcId),
                threshold = beta
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // generateQuestion
    // ─────────────────────────────────────────────────────────────────────────
    fun generateQuestion(): Pair<String, List<Int>> {
        val zpd = KnowledgeRepository.getZPD(student, activeOps)
        for (kcId in zpd) addArmIfNeeded(kcId)

        // Prune arms that are no longer in the ZPD for the active ops
        // (e.g. user switched from + to −, or a new op was deactivated)
        val validIds = zpd.toSet()
        bandit.activeArms().minus(validIds).forEach { staleId ->
            bandit.removeArm(staleId)
            cusumDetectors.remove(staleId)
        }

        if (bandit.activeArms().isEmpty()) {
            return Pair("🎉 All concepts mastered!", listOf(0, 0, 0, 0))
        }

        currentKC = chooseKC(zpd)

        val isSub = KnowledgeRepository.isSubtraction(currentKC)
        val (num1, num2) = generateNumbersForKC(currentKC)
        correctAnswer = if (isSub) num1 - num2 else num1 + num2

        val op       = if (isSub) "−" else "+"
        val question = "$num1 $op $num2 = ?"

        // Distractors — stay non-negative for subtraction
        val options = mutableSetOf(correctAnswer)
        var offset  = 1
        var attempts = 0
        while (options.size < 4 && attempts < 100) {
            val d = (correctAnswer + (-5..5).random()).coerceAtLeast(0)
            if (d != correctAnswer) options.add(d)
            attempts++
        }
        while (options.size < 4) { options.add(correctAnswer + offset); offset++ }

        return Pair(question, options.shuffled())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // submitAnswer
    // ─────────────────────────────────────────────────────────────────────────
    fun submitAnswer(selected: Int): String {
        val isCorrect = selected == correctAnswer
        ts++

        bandit.update(currentKC, isCorrect)
        student.bktUpdateBelief(currentKC, isCorrect)

        val mastered = cusumDetectors[currentKC]?.update(isCorrect) ?: false

        if (mastered) {
            onMastery(currentKC)
        } else {
            updateFocusTracking(isCorrect)
        }

        val stat = cusumDetectors[currentKC]?.getStatistic() ?: 0.0
        val prog = cusumDetectors[currentKC]?.getProgress()  ?: 0.0
        println(
            "[KC $currentKC ${KnowledgeRepository.components[currentKC]?.name}] " +
                    "${if (isCorrect) "✓" else "✗"}  " +
                    "CUSUM=${"%.2f".format(stat)}/${"%.2f".format(beta)} " +
                    "(${"%.0f".format(prog * 100)}%)  " +
                    "focus_q=$focusQuestionCount  consec_wrong=$consecutiveWrong"
        )

        return if (isCorrect) "Correct! 🎉" else "Wrong! The answer was $correctAnswer"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // onMastery
    // ─────────────────────────────────────────────────────────────────────────
    private fun onMastery(kcId: Int) {
        student.setMastered(kcId)
        bandit.removeArm(kcId)
        cusumDetectors.remove(kcId)
        focusKC            = null
        focusQuestionCount = 0
        consecutiveWrong   = 0
        println("✅ MASTERED KC $kcId: ${KnowledgeRepository.components[kcId]?.name}")
        for (newKc in KnowledgeRepository.getZPD(student, activeOps)) addArmIfNeeded(newKc)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // chooseKC
    // ─────────────────────────────────────────────────────────────────────────
    private fun chooseKC(zpd: List<Int>): Int {
        val focus = focusKC
        if (focus != null &&
            focus in zpd &&
            bandit.hasArm(focus) &&
            focusQuestionCount < MIN_FOCUS) {
            focusQuestionCount++
            return focus
        }
        val selected       = bandit.selectConcept(zpd)
        focusKC            = selected
        focusQuestionCount = 1
        consecutiveWrong   = 0
        return selected
    }

    private fun updateFocusTracking(correct: Boolean) {
        if (!correct) {
            consecutiveWrong++
            if (consecutiveWrong >= FRUSTRATION_LIMIT) {
                println("⚠️ Frustration exit on KC $currentKC after $consecutiveWrong wrong")
                focusKC            = null
                focusQuestionCount = 0
                consecutiveWrong   = 0
            }
        } else {
            consecutiveWrong = 0
        }
    }

    fun getCurrentKCName(): String =
        KnowledgeRepository.components[currentKC]?.name ?: "?"

    fun getMasteryProgress(kcId: Int): Double =
        cusumDetectors[kcId]?.getProgress() ?: 0.0

    fun getMasteredKCs(): List<String> =
        KnowledgeRepository.components.values
            .filter { student.isMastered(it.id) }
            .map { it.name }

    // ─────────────────────────────────────────────────────────────────────────
    // Problem generation
    //
    // ADDITION (ids 1–10):
    //  1  = 1A    : 1d + 1d, no carry
    //  2  = 2A2   : 2d + 2d, no carry
    //  3  = 1AC   : 1d + 1d, with carry
    //  4  = 2A1   : 2d + 1d, no carry
    //  5  = 2A1C  : 2d + 1d, with carry
    //  6  = 2A2C  : 2d + 2d, with carry
    //  7  = 3A    : 3d + 3d, no carry
    //  8  = 3AC   : 3d + 3d, exactly 1 carry
    //  9  = 3AC2  : 3d + 3d, exactly 2 carries
    //  10 = 3AC3  : 3d + 3d, 3 carries
    //
    // SUBTRACTION (ids 11–18):
    //  11 = 1S    : 1d − 1d, no borrow  (a > b, result > 0)
    //  12 = 2S1   : 2d − 1d, no borrow
    //  13 = 2S1B  : 2d − 1d, with borrow
    //  14 = 2S2   : 2d − 2d, no borrow
    //  15 = 2S2B  : 2d − 2d, with borrow
    //  16 = 3S    : 3d − 3d, no borrow
    //  17 = 3SB   : 3d − 3d, exactly 1 borrow column
    //  18 = 3SB2  : 3d − 3d, exactly 2 borrow columns
    // ─────────────────────────────────────────────────────────────────────────
    private fun generateNumbersForKC(kcId: Int): Pair<Int, Int> {

        fun nDigit(n: Int): Int {
            val lo = Math.pow(10.0, (n - 1).toDouble()).toInt()
            val hi = Math.pow(10.0, n.toDouble()).toInt() - 1
            return (lo..hi).random()
        }

        // ── Addition helpers ──────────────────────────────────────────────────
        fun hasCarry(a: Int, b: Int): Boolean {
            var x = a; var y = b
            while (x > 0 || y > 0) {
                if ((x % 10) + (y % 10) >= 10) return true
                x /= 10; y /= 10
            }
            return false
        }

        fun carryCount(a: Int, b: Int): Int {
            var x = a; var y = b; var c = 0
            while (x > 0 || y > 0) {
                if ((x % 10) + (y % 10) >= 10) c++
                x /= 10; y /= 10
            }
            return c
        }

        // ── Subtraction helpers ───────────────────────────────────────────────
        // A "borrow" occurs in column i when the top digit < the bottom digit.
        fun hasBorrow(a: Int, b: Int): Boolean {
            var x = a; var y = b
            while (x > 0 || y > 0) {
                if ((x % 10) < (y % 10)) return true
                x /= 10; y /= 10
            }
            return false
        }

        fun borrowCount(a: Int, b: Int): Int {
            var x = a; var y = b; var c = 0
            while (x > 0 || y > 0) {
                if ((x % 10) < (y % 10)) c++
                x /= 10; y /= 10
            }
            return c
        }

        return when (kcId) {

            // ── ADDITION ─────────────────────────────────────────────────────

            // 1A — 1-digit + 1-digit, no carry
            1 -> {
                var a: Int; var b: Int
                do { a = (1..9).random(); b = (1..9).random() } while (a + b >= 10)
                Pair(a, b)
            }

            // 2A2 — 2-digit + 2-digit, no carry
            2 -> {
                var a: Int; var b: Int
                do { a = nDigit(2); b = nDigit(2) } while (hasCarry(a, b))
                Pair(a, b)
            }

            // 1AC — 1-digit + 1-digit, with carry
            3 -> {
                var a: Int; var b: Int
                do { a = (1..9).random(); b = (1..9).random() } while (a + b < 10)
                Pair(a, b)
            }

            // 2A1 — 2-digit + 1-digit, no carry
            4 -> {
                var a: Int; var b: Int
                do { a = nDigit(2); b = (1..9).random() } while (hasCarry(a, b))
                Pair(a, b)
            }

            // 2A1C — 2-digit + 1-digit, with carry
            5 -> {
                var a: Int; var b: Int
                do { a = nDigit(2); b = (1..9).random() } while (!hasCarry(a, b))
                Pair(a, b)
            }

            // 2A2C — 2-digit + 2-digit, with carry
            6 -> {
                var a: Int; var b: Int
                do { a = nDigit(2); b = nDigit(2) } while (!hasCarry(a, b))
                Pair(a, b)
            }

            // 3A — 3-digit + 3-digit, no carry
            7 -> {
                var a: Int; var b: Int
                do { a = nDigit(3); b = nDigit(3) } while (hasCarry(a, b))
                Pair(a, b)
            }

            // 3AC — 3-digit + 3-digit, exactly 1 carry column
            8 -> {
                var a: Int; var b: Int
                do { a = nDigit(3); b = nDigit(3) } while (carryCount(a, b) != 1)
                Pair(a, b)
            }

            // 3AC2 — 3-digit + 3-digit, exactly 2 carry columns
            9 -> {
                var a: Int; var b: Int
                do { a = nDigit(3); b = nDigit(3) } while (carryCount(a, b) != 2)
                Pair(a, b)
            }

            // 3AC3 — 3-digit + 3-digit, all 3 carry columns
            10 -> {
                var a: Int; var b: Int
                do { a = nDigit(3); b = nDigit(3) } while (carryCount(a, b) < 3)
                Pair(a, b)
            }

            // ── SUBTRACTION ──────────────────────────────────────────────────

            // 1S — 1-digit − 1-digit, no borrow (a > b, result ≥ 1)
            11 -> {
                var a: Int; var b: Int
                do { a = (2..9).random(); b = (1..9).random() } while (a <= b)
                Pair(a, b)
            }

            // 2S1 — 2-digit − 1-digit, no borrow
            12 -> {
                var a: Int; var b: Int
                do { a = nDigit(2); b = (1..9).random() } while (hasBorrow(a, b) || a - b <= 0)
                Pair(a, b)
            }

            // 2S1B — 2-digit − 1-digit, with borrow
            13 -> {
                var a: Int; var b: Int
                do { a = nDigit(2); b = (1..9).random() } while (!hasBorrow(a, b) || a - b <= 0)
                Pair(a, b)
            }

            // 2S2 — 2-digit − 2-digit, no borrow (a > b)
            14 -> {
                var a: Int; var b: Int
                do { a = nDigit(2); b = nDigit(2) } while (hasBorrow(a, b) || a <= b)
                Pair(a, b)
            }

            // 2S2B — 2-digit − 2-digit, with borrow (a > b)
            15 -> {
                var a: Int; var b: Int
                do { a = nDigit(2); b = nDigit(2) } while (!hasBorrow(a, b) || a <= b)
                Pair(a, b)
            }

            // 3S — 3-digit − 3-digit, no borrow (a > b)
            16 -> {
                var a: Int; var b: Int
                do { a = nDigit(3); b = nDigit(3) } while (hasBorrow(a, b) || a <= b)
                Pair(a, b)
            }

            // 3SB — 3-digit − 3-digit, exactly 1 borrow column (a > b)
            17 -> {
                var a: Int; var b: Int
                do { a = nDigit(3); b = nDigit(3) } while (borrowCount(a, b) != 1 || a <= b)
                Pair(a, b)
            }

            // 3SB2 — 3-digit − 3-digit, exactly 2 borrow columns (a > b)
            18 -> {
                var a: Int; var b: Int
                do { a = nDigit(3); b = nDigit(3) } while (borrowCount(a, b) != 2 || a <= b)
                Pair(a, b)
            }

            else -> Pair(1, 1)
        }
    }

    // Keep the class-level hasCarry used only by the duplicate at the bottom of the
    // original file — now removed. The local fun inside generateNumbersForKC covers it.
}