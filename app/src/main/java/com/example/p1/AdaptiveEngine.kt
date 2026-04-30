package com.example.p1

import android.util.Log
import kotlin.math.pow

class AdaptiveEngine {

    private val TAG = "AdaptiveSystem"

    val student = StudentModel()

    private val fpr  = 0.00009
    private val beta = CUSUMDetector.thresholdFromFPR(fpr)  // ≈ 9.32

    private val bandit         = KLUCBBandit()
    private val cusumDetectors = mutableMapOf<Int, CUSUMDetector>()
    private var ts             = 0
    private var currentKC      = 1

    // ── Phase tracking ────────────────────────────────────────────────────────
    enum class Phase { ASSESSMENT, LEARNING }
    private var currentPhase = Phase.ASSESSMENT
    private var assessmentResponseString = ""

    var newlyFoundBoundary: Set<String>? = null
        private set

    fun consumeBoundary(): Set<String>? {
        val b = newlyFoundBoundary
        newlyFoundBoundary = null
        return b
    }

    // ── Operation type ────────────────────────────────────────────────────────
    private var operationType: String = "+"

    // ── Digit mode ────────────────────────────────────────────────────────────
    // 1 = 1-digit only (KCs 1-2 / 10-11), skip assessment, straight to learning
    // 2 = up to 2-digit (KCs 1-6 / 10-15), skip assessment
    // 3 = full graph + boundary assessment (default)
    private var digitMode: Int = 3

    /**
     * Set digit mode — call this before setOperation.
     * Modes 1/2 skip boundary assessment and go straight to learning.
     * Mode 3 runs the full boundary assessment first.
     */
    fun applyDigitMode(mode: Int) {
        digitMode = mode
    }

    /** Set the active operation — resets the engine for that graph */
    fun setOperation(op: String) {
        Log.i(TAG, "--- STARTING NEW SESSION ---")
        Log.i(TAG, "Operation: $op | DigitMode: $digitMode")
        operationType = op
        bandit.clearAll()
        cusumDetectors.clear()
        ts = 0
        focusKC = null
        focusQuestionCount = 0
        consecutiveWrong = 0
        // Mode 3 = full assessment; modes 1/2 skip straight to learning
        currentPhase = if (digitMode == 3) Phase.ASSESSMENT else Phase.LEARNING
        assessmentResponseString = ""
        student.reset()
        assessmentAnswers.clear()
        assessmentNodeDecided = false
        initZPD()
    }

    /** Returns the KC IDs allowed for current operation + digit mode */
    private fun activeKCIds(): List<Int> {
        val base = if (operationType == "-") KnowledgeRepository.subtractionIds
        else KnowledgeRepository.additionIds
        return when (digitMode) {
            1    -> if (operationType == "-") listOf(10, 11) else listOf(1, 2)
            2    -> if (operationType == "-") (10..15).toList() else (1..6).toList()
            else -> base
        }
    }

    // ── Focus mode ────────────────────────────────────────────────────────────
    private var focusKC:            Int? = null
    private var focusQuestionCount: Int  = 0
    private var consecutiveWrong:   Int  = 0
    private val minFocus                 = 5
    private val frustrationLimit         = 12

    // ── ZPD ancestor tracking ─────────────────────────────────────────────────
    private val kcAncestors = mutableMapOf<Int, MutableSet<Int>>()

    var correctAnswer: Int = 0
        private set

    // ── Assessment state ──────────────────────────────────────────────────────
    // assessmentAnswers: per-node list of correct/wrong (max 2 entries)
    // assessmentNodeDecided: true once verdict reached, waiting for next generateQuestion
    private val assessmentAnswers = mutableMapOf<Int, MutableList<Boolean>>()
    private var assessmentNodeDecided = false

    init { initZPD() }

    private fun initZPD() {
        kcAncestors.clear()
        val filterIds = activeKCIds()
        val initZpd = KnowledgeRepository.getZPD(student, filterIds)

        val reachable = mutableSetOf<Int>()
        val seed = ArrayDeque(initZpd)
        while (seed.isNotEmpty()) {
            val kcId = seed.removeFirst()
            val children = KnowledgeRepository.getChildren(kcId)
                .filter { it in filterIds }
            for (child in children) {
                if (reachable.add(child)) seed.addLast(child)
            }
        }

        val locked = reachable - initZpd.toSet()
        for (lockedKc in locked) {
            val ancestors = mutableSetOf<Int>()
            for (zpd in initZpd) {
                if (isAncestor(zpd, lockedKc)) ancestors.add(zpd)
            }
            if (ancestors.isNotEmpty()) kcAncestors[lockedKc] = ancestors
        }

        for (kcId in initZpd) addArmIfNeeded(kcId)
    }

    private fun isAncestor(ancestor: Int, kcId: Int): Boolean {
        val prereqs = KnowledgeRepository.getPrerequisites(kcId)
        if (ancestor in prereqs) return true
        return prereqs.any { isAncestor(ancestor, it) }
    }

    private fun addArmIfNeeded(kcId: Int) {
        if (!bandit.hasArm(kcId)) {
            bandit.addArm(kcId, ts)
            val node = bandit.getNode(kcId)!!
            cusumDetectors[kcId] = CUSUMDetector(
                pg        = node.pg,
                ps        = node.ps,
                threshold = beta
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2-question assessment rule
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 2-question assessment rule:
     *   Q1 wrong → FAIL immediately
     *   Q1 right, Q2 right → PASS
     *   Q1 right, Q2 wrong → FAIL
     */
    private fun updateAssessmentNode(kcId: Int, correct: Boolean): Boolean? {
        val answers = assessmentAnswers.getOrPut(kcId) { mutableListOf() }
        answers.add(correct)

        Log.d(TAG, "[ASSESS KC $kcId] Q${answers.size}: ${if (correct) "✓" else "✗"}")

        return when {
            answers.size == 1 && !correct -> { Log.d(TAG, "[ASSESS KC $kcId] → FAIL (Q1 wrong)"); false }
            answers.size == 2 && correct  -> { Log.d(TAG, "[ASSESS KC $kcId] → PASS (Q1+Q2 correct)"); true }
            answers.size == 2 && !correct -> { Log.d(TAG, "[ASSESS KC $kcId] → FAIL (Q2 wrong)"); false }
            else -> null  // Q1 correct, waiting for Q2
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // generateQuestion
    // ─────────────────────────────────────────────────────────────────────────
    fun generateQuestion(): Pair<String, List<Int>> {
        return if (currentPhase == Phase.ASSESSMENT) {
            generateAssessmentQuestion()
        } else {
            generateLearningQuestion()
        }
    }

    private fun generateLearningQuestion(): Pair<String, List<Int>> {

        val filterIds = activeKCIds()
        val zpd = KnowledgeRepository.getZPD(student, filterIds)

        for (kcId in zpd) addArmIfNeeded(kcId)

        currentKC = if (zpd.isEmpty()) {
            if (filterIds.isNotEmpty()) filterIds.last() else 1
        } else {
            chooseKC(zpd)
        }

        val (num1, num2) = generateNumbersForKC(currentKC)
        val op = KnowledgeRepository.getOperationType(currentKC)
        correctAnswer = if (op == "-") num1 - num2 else num1 + num2

        val opSymbol = if (op == "-") "−" else "+"
        val question = "$num1 $opSymbol $num2 = ?"

        val distractors = DistractorGenerator.generate(currentKC, num1, num2, correctAnswer, needed = 3)
        val options = (listOf(correctAnswer) + distractors).shuffled()

        return Pair(question, options)
    }

    private fun generateAssessmentQuestion(): Pair<String, List<Int>> =
        when (val state = BoundaryAssessmentEngine.getNextState(assessmentResponseString, operationType == "+")) {
            is BoundaryAssessmentEngine.BoundaryState.Ask -> {
                val kcId = BoundaryAssessmentEngine.NODE_TO_ID[state.nodeName] ?: 1
                currentKC = kcId
                assessmentNodeDecided = false

                val (num1, num2) = generateNumbersForKC(currentKC)
                val op = KnowledgeRepository.getOperationType(currentKC)
                correctAnswer = if (op == "-") num1 - num2 else num1 + num2

                val opSymbol = if (op == "-") "−" else "+"
                val question = "$num1 $opSymbol $num2 = ?"

                val distractors = DistractorGenerator.generate(currentKC, num1, num2, correctAnswer, needed = 3)
                val options = (listOf(correctAnswer) + distractors).shuffled()

                Pair(question, options)
            }
            is BoundaryAssessmentEngine.BoundaryState.Terminal -> {
                handleTerminalAssessment(state)
                currentPhase = Phase.LEARNING
                generateLearningQuestion()
            }
            else -> {
                currentPhase = Phase.LEARNING
                initZPD()
                generateLearningQuestion()
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // submitAnswer
    // ─────────────────────────────────────────────────────────────────────────
    fun submitAnswer(selected: Int): String {
        val isCorrect = selected == correctAnswer
        ts++

        if (currentPhase == Phase.ASSESSMENT) {
            if (assessmentNodeDecided) {
                return if (isCorrect) "Correct! 🎉" else "Wrong! The answer was $correctAnswer"
            }

            val decision = updateAssessmentNode(currentKC, isCorrect)

            if (decision != null) {
                assessmentNodeDecided = true
                assessmentAnswers.remove(currentKC)

                assessmentResponseString += if (decision) "1" else "0"
                Log.d(TAG, "Assessment path updated: $assessmentResponseString")

                val nextState = BoundaryAssessmentEngine.getNextState(assessmentResponseString, operationType == "+")
                if (nextState is BoundaryAssessmentEngine.BoundaryState.Terminal) {
                    handleTerminalAssessment(nextState)
                    currentPhase = Phase.LEARNING
                }
            }

            return if (isCorrect) "Correct! 🎉" else "Wrong! The answer was $correctAnswer"
        }

        // ── LEARNING phase ────────────────────────────────────────────────────
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
        Log.d(TAG,
            "[KC $currentKC ${KnowledgeRepository.components[currentKC]?.name}] " +
                    "${if (isCorrect) "✓" else "✗"}  " +
                    "CUSUM=${"%.2f".format(stat)}/${"%.2f".format(beta)} " +
                    "(${"%.0f".format(prog * 100)}%)  " +
                    "focus_q=$focusQuestionCount  consecutive_wrong=$consecutiveWrong"
        )

        return if (isCorrect) "Correct! 🎉" else "Wrong! The answer was $correctAnswer"
    }

    private fun handleTerminalAssessment(terminal: BoundaryAssessmentEngine.BoundaryState.Terminal) {
        Log.i(TAG, "── Assessment Terminal State reached ──")
        Log.i(TAG, "  Solvable: ${terminal.solvable}")
        Log.i(TAG, "  Boundary (Current Level): ${terminal.boundary}")

        newlyFoundBoundary = terminal.boundary

        for (nodeName in terminal.solvable) {
            val kcId = BoundaryAssessmentEngine.NODE_TO_ID[nodeName]
            if (kcId != null) {
                student.setMastered(kcId)
                bandit.removeArm(kcId)
                cusumDetectors.remove(kcId)
            }
        }

        currentPhase = Phase.LEARNING
        initZPD()
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
        Log.i(TAG, "✅ MASTERED KC $kcId: ${KnowledgeRepository.components[kcId]?.name}")

        for (ancestors in kcAncestors.values) ancestors.remove(kcId)

        val filterIds = activeKCIds()
        val children = KnowledgeRepository.getChildren(kcId)
            .filter { it in filterIds }
            
        val unlockedKCs = mutableListOf<Int>()
        for (childKc in children) {
            val remainingAncestors = kcAncestors[childKc]
            if (!remainingAncestors.isNullOrEmpty()) continue
            if (!student.isMastered(childKc)) {
                addArmIfNeeded(childKc)
                unlockedKCs.add(childKc)
            }
        }
        if (unlockedKCs.isNotEmpty()) {
            Log.d(TAG, "Unlocked KCs into ZPD: $unlockedKCs")
        }

        for (newKc in KnowledgeRepository.getZPD(student, filterIds)) addArmIfNeeded(newKc)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // chooseKC
    // ─────────────────────────────────────────────────────────────────────────
    private fun chooseKC(zpd: List<Int>): Int {
        val focus = focusKC
        if (focus != null &&
            focusKC in zpd &&
            bandit.hasArm(focus) &&
            focusQuestionCount < minFocus) {
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
            if (consecutiveWrong >= frustrationLimit) {
                Log.w(TAG, "⚠️ Frustration exit on KC $currentKC after $consecutiveWrong wrong")
                focusKC            = null
                focusQuestionCount = 0
                consecutiveWrong   = 0
            }
        } else {
            consecutiveWrong = 0
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Problem generation
    // ─────────────────────────────────────────────────────────────────────────
    private fun generateNumbersForKC(kcId: Int): Pair<Int, Int> {

        fun nDigit(n: Int): Int {
            val lo = 10.0.pow(n - 1).toInt()
            val hi = 10.0.pow(n).toInt() - 1
            return (lo..hi).random()
        }

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

        fun hasBorrow(a: Int, b: Int): Boolean {
            var x = a; var y = b
            while (x > 0 || y > 0) {
                if ((x % 10) < (y % 10)) return true
                x /= 10; y /= 10
            }
            return false
        }

        fun borrowCount(a: Int, b: Int): Int {
            var x = a; var y = b; var c = 0; var borrow = 0
            while (x > 0 || y > 0) {
                val xd = x % 10 - borrow
                val yd = y % 10
                if (xd < yd) { c++; borrow = 1 } else { borrow = 0 }
                x /= 10; y /= 10
            }
            return c
        }

        return when (kcId) {
            1  -> { var a: Int; var b: Int; do { a = (1..9).random(); b = (1..9).random() } while (a + b >= 10); Pair(a, b) }
            2  -> { var a: Int; var b: Int; do { a = (1..9).random(); b = (1..9).random() } while (a + b < 10); Pair(a, b) }
            3  -> { var a: Int; var b: Int; do { a = nDigit(2); b = (1..9).random() } while (hasCarry(a, b)); Pair(a, b) }
            4  -> { var a: Int; var b: Int; do { a = nDigit(2); b = (1..9).random() } while (!hasCarry(a, b)); Pair(a, b) }
            5  -> { var a: Int; var b: Int; do { a = nDigit(2); b = nDigit(2) } while (hasCarry(a, b)); Pair(a, b) }
            6  -> { var a: Int; var b: Int; do { a = nDigit(2); b = nDigit(2) } while (carryCount(a, b) != 2); Pair(a, b) }
            7  -> { var a: Int; var b: Int; do { a = nDigit(3); b = nDigit(3) } while (hasCarry(a, b)); Pair(a, b) }
            8  -> { var a: Int; var b: Int; do { a = nDigit(3); b = nDigit(2) } while (carryCount(a, b) != 1); Pair(a, b) }
            9  -> { var a: Int; var b: Int; do { a = nDigit(3); b = nDigit(3) } while (carryCount(a, b) < 2); Pair(a, b) }
            10 -> { var a: Int; var b: Int; do { a = (2..9).random(); b = (1..9).random() } while (b >= a); Pair(a, b) }
            11 -> { var a: Int; var b: Int; do { a = nDigit(2); b = (1..9).random() } while (hasBorrow(a, b)); Pair(a, b) }
            12 -> { var a: Int; var b: Int; do { a = nDigit(2); b = (1..9).random() } while (!hasBorrow(a, b) || a - b < 1); Pair(a, b) }
            13 -> { var a: Int; var b: Int; do { a = nDigit(2); b = nDigit(2) } while (hasBorrow(a, b) || a <= b); Pair(a, b) }
            14 -> { var a: Int; var b: Int; do { a = nDigit(3); b = nDigit(3) } while (hasBorrow(a, b) || a <= b); Pair(a, b) }
            15 -> { var a: Int; var b: Int; do { a = nDigit(2); b = nDigit(2) } while (!hasBorrow(a, b) || a <= b); Pair(a, b) }
            16 -> { var a: Int; var b: Int; do { a = nDigit(3); b = nDigit(3) } while (borrowCount(a, b) != 1 || a <= b); Pair(a, b) }
            17 -> { var a: Int; var b: Int; do { a = nDigit(3); b = nDigit(3) } while (borrowCount(a, b) < 2 || a <= b); Pair(a, b) }
            else -> Pair(1, 1)
        }
    }
}