package com.example.p1

import kotlin.math.pow

class AdaptiveEngine {

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

    // ── Operation type ────────────────────────────────────────────────────────
    private var operationType: String = "+"

    /** Set the active operation — resets the engine for that graph */
    fun setOperation(op: String) {
        operationType = op
        bandit.clearAll()
        cusumDetectors.clear()
        ts = 0
        focusKC = null
        focusQuestionCount = 0
        consecutiveWrong = 0
        currentPhase = Phase.ASSESSMENT
        assessmentResponseString = ""
        student.reset()
        // Clear assessment CUSUM state
        assessmentCusum.clear()
        assessmentAnswers.clear()
        assessmentNodeDecided = false
        initZPD()
    }

    /** Returns the list of KC IDs for the currently selected operation */
    private fun activeKCIds(): List<Int> = when (operationType) {
        "-"  -> KnowledgeRepository.subtractionIds
        else -> KnowledgeRepository.additionIds
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

    // ── Assessment CUSUM state ────────────────────────────────────────────────
    // One CUSUMDetector per assessment node, created fresh each time we enter a node.
    // assessmentAnswers tracks raw correct/wrong for fallback majority vote.
    // assessmentNodeDecided = true means CUSUM has already fired for current node
    // (either pass or fail) and we're waiting for generateQuestion to advance.
    private val assessmentCusum   = mutableMapOf<Int, CUSUMDetector>()
    private val assessmentAnswers = mutableMapOf<Int, MutableList<Boolean>>()
    private var assessmentNodeDecided = false

    // Max questions per node before forcing a majority decision (safety cap)
    private val assessmentMaxPerNode = 15

    // Minimum attempts before we can declare FAIL.
    // Must be high enough that a single accidental wrong doesn't trigger failure.
    // We declare FAIL when: attempts >= minFailAttempts AND no correct answer
    // in the last failStreakRequired consecutive attempts.
    private val minFailAttempts     = 5
    private val failStreakRequired  = 4   // 4 consecutive wrong → FAIL

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
    // Assessment CUSUM helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Get or create the CUSUM detector for an assessment node */
    private fun assessmentDetector(kcId: Int): CUSUMDetector {
        return assessmentCusum.getOrPut(kcId) {
            val kc = KnowledgeRepository.components[kcId]
            CUSUMDetector(
                pg        = kc?.guessProbability ?: 0.10,
                ps        = kc?.slipProbability  ?: 0.10,
                threshold = beta
            )
        }
    }

    /**
     * Feed an answer into the assessment CUSUM for the current node.
     * Returns:
     *   true  → node PASSED  (append "1" to response string)
     *   false → node FAILED  (append "0" to response string)
     *   null  → not decided yet, ask another question for this node
     */
    private fun updateAssessmentNode(kcId: Int, correct: Boolean): Boolean? {
        val answers = assessmentAnswers.getOrPut(kcId) { mutableListOf() }
        answers.add(correct)

        val detector = assessmentDetector(kcId)
        val mastered = detector.update(correct)

        val attempts = answers.size
        val correctCount = answers.count { it }

        println(
            "[ASSESS KC $kcId] ${if (correct) "✓" else "✗"}  " +
                    "CUSUM=${"%.2f".format(detector.getStatistic())}/${"%.2f".format(beta)}  " +
                    "attempts=$attempts  correct=$correctCount"
        )

        // PASS: CUSUM crossed threshold
        if (mastered) {
            println("[ASSESS KC $kcId] → PASS (CUSUM mastery)")
            return true
        }

        // FAIL: N consecutive wrong answers after minimum attempts reached
        if (attempts >= minFailAttempts) {
            val lastN = answers.takeLast(failStreakRequired)
            if (lastN.size == failStreakRequired && lastN.none { it }) {
                println("[ASSESS KC $kcId] → FAIL ($failStreakRequired consecutive wrong)")
                return false
            }
        }

        // Safety cap: majority vote after assessmentMaxPerNode attempts
        if (attempts >= assessmentMaxPerNode) {
            val pass = correctCount.toDouble() / attempts >= 0.5
            println("[ASSESS KC $kcId] → ${if (pass) "PASS" else "FAIL"} (cap reached, rate=${"%.2f".format(correctCount.toDouble()/attempts)})")
            return pass
        }

        return null  // undecided — keep asking
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
            // If node is already decided this round, ignore (shouldn't happen — UI disables buttons)
            if (assessmentNodeDecided) {
                return if (isCorrect) "Correct! 🎉" else "Wrong! The answer was $correctAnswer"
            }

            val decision = updateAssessmentNode(currentKC, isCorrect)

            if (decision != null) {
                // Node verdict reached — append to response string and advance dispatch
                assessmentNodeDecided = true
                assessmentAnswers.remove(currentKC)   // reset for next time (if node re-visited)
                assessmentCusum.remove(currentKC)

                assessmentResponseString += if (decision) "1" else "0"

                val nextState = BoundaryAssessmentEngine.getNextState(assessmentResponseString, operationType == "+")
                if (nextState is BoundaryAssessmentEngine.BoundaryState.Terminal) {
                    handleTerminalAssessment(nextState)
                    currentPhase = Phase.LEARNING
                }
            }
            // else: undecided — next generateQuestion() call will keep currentKC the same

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
        println(
            "[KC $currentKC ${KnowledgeRepository.components[currentKC]?.name}] " +
                    "${if (isCorrect) "✓" else "✗"}  " +
                    "CUSUM=${"%.2f".format(stat)}/${"%.2f".format(beta)} " +
                    "(${"%.0f".format(prog * 100)}%)  " +
                    "focus_q=$focusQuestionCount  consecutive_wrong=$consecutiveWrong"
        )

        return if (isCorrect) "Correct! 🎉" else "Wrong! The answer was $correctAnswer"
    }

    private fun handleTerminalAssessment(terminal: BoundaryAssessmentEngine.BoundaryState.Terminal) {
        println("── Assessment Terminal State reached ──")
        println("  Solvable: ${terminal.solvable}")
        println("  Boundary: ${terminal.boundary}")

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
        println("✅ MASTERED KC $kcId: ${KnowledgeRepository.components[kcId]?.name}")

        for (ancestors in kcAncestors.values) ancestors.remove(kcId)

        val filterIds = activeKCIds()
        val children = KnowledgeRepository.getChildren(kcId)
            .filter { it in filterIds }
        for (childKc in children) {
            val remainingAncestors = kcAncestors[childKc]
            if (!remainingAncestors.isNullOrEmpty()) continue
            if (!student.isMastered(childKc)) addArmIfNeeded(childKc)
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
                println("⚠️ Frustration exit on KC $currentKC after $consecutiveWrong wrong")
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