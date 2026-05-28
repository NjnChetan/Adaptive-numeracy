package com.example.p1

import android.util.Log
import kotlin.math.pow

class AdaptiveEngine {

    private val TAG = "AdaptiveSystem"



    val student = StudentModel()

    private val fpr       = 0.00009
    private val beta      = CUSUMDetector.thresholdFromFPR(fpr)       // ≈ 9.32 — learning phase
    private val betaAssess = CUSUMDetector.thresholdFromFPR(0.05)     // ≈ 3.0  — assessment (faster)

    private val bandit         = KLUCBBandit()
    private val cusumDetectors = mutableMapOf<Int, CUSUMDetector>()
    private var ts             = 0
    private var currentKC      = 1

    // ── Phase tracking ────────────────────────────────────────────────────────
    enum class Phase { ASSESSMENT, LEARNING }
    var currentPhase = Phase.ASSESSMENT
        private set
    private var assessmentResponseString = ""

    var newlyFoundBoundary: Set<String>? = null
        private set

    fun consumeBoundary(): Set<String>? {
        val b = newlyFoundBoundary
        newlyFoundBoundary = null
        return b
    }

    // ── All-mastered signal (modes 1 & 2) ────────────────────────────────────
    var newlyAllMastered: Boolean = false
        private set

    fun consumeAllMastered(): Boolean {
        val v = newlyAllMastered
        newlyAllMastered = false
        return v
    }

    // ── Mastery event (for CSV logging) ────────────────────────────────
    data class MasteryEvent(val kcId: Int, val conceptName: String, val correctnessRecord: List<Boolean>)
    var lastMasteryEvent: MasteryEvent? = null; private set
    fun consumeMasteryEvent(): MasteryEvent? { val e = lastMasteryEvent; lastMasteryEvent = null; return e }

    // ── ZPD-update event (for CSV logging) ─────────────────────────────
    var lastZpdUpdate: List<String>? = null; private set
    fun consumeZpdUpdate(): List<String>? { val e = lastZpdUpdate; lastZpdUpdate = null; return e }


    // ── Operation type ────────────────────────────────────────────────────────
    private var operationType: String = "+"

    // ── Digit mode ────────────────────────────────────────────────────────────
    private var digitMode: Int = 3

    fun startSession(op: String, mode: Int) {
        Log.i(TAG, "--- STARTING NEW SESSION ---")
        operationType = op
        digitMode = mode
        Log.i(TAG, "Operation: $op | DigitMode: $digitMode")
        bandit.clearAll()
        cusumDetectors.clear()
        assessmentCusum.clear()
        assessmentWrong = 0
        ts = 0
        detectionQuestionNo = 0
        practiceQuestionNo = 0
        focusKC = null
        focusQuestionCount = 0
        consecutiveWrong = 0
        currentPhase = if (digitMode == 1 || digitMode == 2) Phase.LEARNING else Phase.ASSESSMENT
        assessmentResponseString = ""
        student.reset()
        initZPD()
    }

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

    // ── Exposed question metadata for CSV logging ─────────────────────────
    var lastNum1: Int = 0; private set
    var lastNum2: Int = 0; private set
    var lastQuestionText: String = ""; private set
    val currentKCName: String get() = BoundaryAssessmentEngine.ID_TO_NODE[currentKC] ?: "$currentKC"
    val currentKCId: Int get() = currentKC
    var detectionQuestionNo: Int = 0; private set
    var practiceQuestionNo: Int = 0; private set

    val activeConceptsNames: List<String>
        get() {
            val filterIds = activeKCIds()
            return filterIds.map { BoundaryAssessmentEngine.ID_TO_NODE[it] ?: "$it" }
        }

    init { initZPD() }

    private fun initZPD() {
        kcAncestors.clear()
        val filterIds = activeKCIds()
        val initZpd = KnowledgeRepository.getZPD(student, filterIds)

        Log.i(TAG, "── initZPD() ──")
        Log.i(TAG, "  filterIds = $filterIds")
        Log.i(TAG, "  mastered  = ${filterIds.filter { student.isMastered(it) }.map { "$it(${KnowledgeRepository.components[it]?.name})" }}")
        Log.i(TAG, "  ZPD       = ${initZpd.map { "$it(${KnowledgeRepository.components[it]?.name})" }}")


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

    // ── Pick next assessment state based on digitMode ─────────────────────────
    private fun getNextAssessmentState(responseString: String): BoundaryAssessmentEngine.BoundaryState? {
        val isAddition = operationType == "+"
        return when (digitMode) {
            1    -> BoundaryAssessmentEngine.getNextStateLevel1(responseString, isAddition)
            2    -> BoundaryAssessmentEngine.getNextStateLevel2(responseString, isAddition)
            else -> BoundaryAssessmentEngine.getNextState(responseString, isAddition)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Assessment CUSUM — one detector per node being assessed.
    // Pass  = CUSUM fires (mastery threshold reached).
    // Fail  = consecutiveWrong hits frustrationLimit on this node.
    // ─────────────────────────────────────────────────────────────────────────
    private val assessmentCusum   = mutableMapOf<Int, CUSUMDetector>()
    private var assessmentWrong   = 0   // consecutive wrong for current assessment node

    private fun getOrCreateAssessmentCusum(kcId: Int): CUSUMDetector {
        return assessmentCusum.getOrPut(kcId) {
            val pg = KnowledgeRepository.getGuessProb(kcId)
            val ps = KnowledgeRepository.getSlipProb(kcId)
            CUSUMDetector(pg = pg, ps = ps, threshold = betaAssess)
        }
    }

    // Returns true = PASS, false = FAIL, null = still accumulating
    private fun updateAssessmentNode(kcId: Int, correct: Boolean): Boolean? {
        val cusum = getOrCreateAssessmentCusum(kcId)
        val mastered = cusum.update(correct)
        Log.d(TAG, "[ASSESS KC $kcId] ${if (correct) "✓" else "✗"}  CUSUM=${cusum.getStatistic()}")
        if (mastered) {
            assessmentCusum.remove(kcId)
            assessmentWrong = 0
            Log.d(TAG, "[ASSESS KC $kcId] → PASS (CUSUM mastery)")
            return true
        }
        if (!correct) {
            assessmentWrong++
            if (assessmentWrong >= 3) {
                assessmentCusum.remove(kcId)
                assessmentWrong = 0
                Log.d(TAG, "[ASSESS KC $kcId] → FAIL (3 wrong)")
                return false
            }
        } else {
            assessmentWrong = 0
        }
        return null  // keep asking this node
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

        Log.d(TAG, "[generateLearningQuestion] ZPD = ${zpd.map { "$it(${BoundaryAssessmentEngine.ID_TO_NODE[it] ?: it})" }}")
        Log.d(TAG, "  mastered = ${filterIds.filter { student.isMastered(it) }.map { BoundaryAssessmentEngine.ID_TO_NODE[it] ?: "$it" }}")


        for (kcId in zpd) addArmIfNeeded(kcId)

        // If ZPD is empty, all eligible KCs are mastered → signal completion
        if (zpd.isEmpty()) {
            if (filterIds.all { student.isMastered(it) }) {
                Log.i(TAG, "🎓 All KCs mastered! filterIds=$filterIds")
                newlyAllMastered = true
            }
        }

        currentKC = if (zpd.isEmpty()) {
            if (filterIds.isNotEmpty()) filterIds.last() else 1
        } else {
            chooseKC(zpd)
        }

        practiceQuestionNo++
        val (num1, num2) = generateNumbersForKC(currentKC)
        lastNum1 = num1; lastNum2 = num2
        val op = KnowledgeRepository.getOperationType(currentKC)
        correctAnswer = if (op == "-") num1 - num2 else num1 + num2

        val opSymbol = if (op == "-") "−" else "+"
        val question = "$num1 $opSymbol $num2 = ?"
        lastQuestionText = "$num1${if (op == "-") "-" else "+"}$num2"

        val distractors = DistractorGenerator.generate(currentKC, num1, num2, correctAnswer, needed = 3)
        val options = (listOf(correctAnswer) + distractors).shuffled()

        return Pair(question, options)
    }

    private fun generateAssessmentQuestion(): Pair<String, List<Int>> =
        when (val state = getNextAssessmentState(assessmentResponseString)) {
            is BoundaryAssessmentEngine.BoundaryState.Ask -> {
                val kcId = BoundaryAssessmentEngine.NODE_TO_ID[state.nodeName] ?: 1
                currentKC = kcId
                Log.i(TAG, "[ASSESSMENT] Asking KC $kcId (${state.nodeName}) | path='$assessmentResponseString'")


                detectionQuestionNo++
                val (num1, num2) = generateNumbersForKC(currentKC)
                lastNum1 = num1; lastNum2 = num2
                val op = KnowledgeRepository.getOperationType(currentKC)
                correctAnswer = if (op == "-") num1 - num2 else num1 + num2

                val opSymbol = if (op == "-") "−" else "+"
                val question = "$num1 $opSymbol $num2 = ?"
                lastQuestionText = "$num1${if (op == "-") "-" else "+"}$num2"

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
            val decision = updateAssessmentNode(currentKC, isCorrect)

            if (decision != null) {
                assessmentResponseString += if (decision) "1" else "0"
                Log.d(TAG, "Assessment path updated: $assessmentResponseString")

                val nextState = getNextAssessmentState(assessmentResponseString)
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

        val solvableIds = terminal.solvable.mapNotNull { BoundaryAssessmentEngine.NODE_TO_ID[it] }.toSet()

        // Pre-master solvable-minus-boundary (clearly already known).
        // Also pre-master boundary KCs whose ALL prereqs are in solvable —
        // those are transitively proven by the assessment (e.g. passed 2A1 → 1A is proven).
        val filterIds = activeKCIds()
        val toPremaster = terminal.solvable.filter { nodeName ->
            val kcId = BoundaryAssessmentEngine.NODE_TO_ID[nodeName] ?: return@filter false
            if (nodeName !in terminal.boundary) return@filter true   // solvable - boundary: always premaster
            // boundary KC: premaster only if all its prereqs are in solvable
            val prereqs = KnowledgeRepository.getPrerequisites(kcId).filter { it in filterIds }
            prereqs.all { it in solvableIds }
        }
        for (nodeName in toPremaster) {
            val kcId = BoundaryAssessmentEngine.NODE_TO_ID[nodeName]
            if (kcId != null) {
                student.setMastered(kcId)
                bandit.removeArm(kcId)
                cusumDetectors.remove(kcId)
            }
        }

        currentPhase = Phase.LEARNING
        initZPD()

        if (filterIds.all { student.isMastered(it) }) {
            Log.i(TAG, "🎓 Assessment proved ALL KCs mastered for digitMode=$digitMode!")
            newlyAllMastered = true
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // onMastery
    // ─────────────────────────────────────────────────────────────────────────
    private fun onMastery(kcId: Int) {
        // Save correctness record before removing detector
        val record = cusumDetectors[kcId]?.correctnessRecord?.toList() ?: emptyList()
        val conceptName = BoundaryAssessmentEngine.ID_TO_NODE[kcId] ?: "$kcId"
        lastMasteryEvent = MasteryEvent(kcId, conceptName, record)

        student.setMastered(kcId)
        bandit.removeArm(kcId)
        cusumDetectors.remove(kcId)
        focusKC            = null
        focusQuestionCount = 0
        consecutiveWrong   = 0
        Log.i(TAG, "✅ MASTERED KC $kcId: ${KnowledgeRepository.components[kcId]?.name}")
        Log.i(TAG, "  $conceptName mastered")

        for (ancestors in kcAncestors.values) ancestors.remove(kcId)

        val filterIds = activeKCIds()
        val children = KnowledgeRepository.getChildren(kcId)
            .filter { it in filterIds }

        val unlockedKCs = mutableListOf<Int>()
        for (childKc in children) {
            val prereqs = KnowledgeRepository.getPrerequisites(childKc)
                .filter { it in filterIds }
            val allPrereqsMet = prereqs.all { student.isMastered(it) }
            if (!allPrereqsMet) {
                Log.d(TAG, "  KC $childKc (${BoundaryAssessmentEngine.ID_TO_NODE[childKc]}) still locked: " +
                        "unmastered prereqs = ${prereqs.filter { !student.isMastered(it) }.map { BoundaryAssessmentEngine.ID_TO_NODE[it] ?: "$it" }}")
                continue
            }
            if (!student.isMastered(childKc)) {
                addArmIfNeeded(childKc)
                unlockedKCs.add(childKc)
            }
        }
        if (unlockedKCs.isNotEmpty()) {
            Log.d(TAG, "Unlocked KCs into ZPD: ${unlockedKCs.map { "$it(${BoundaryAssessmentEngine.ID_TO_NODE[it]})" }}")
            lastZpdUpdate = unlockedKCs.map { BoundaryAssessmentEngine.ID_TO_NODE[it] ?: "$it" }
        }

        val newZpd = KnowledgeRepository.getZPD(student, filterIds)
        for (newKc in newZpd) addArmIfNeeded(newKc)

        Log.i(TAG, "  After mastery of ${BoundaryAssessmentEngine.ID_TO_NODE[kcId] ?: kcId}:")
        Log.i(TAG, "  New ZPD = ${newZpd.map { "${BoundaryAssessmentEngine.ID_TO_NODE[it] ?: it}" }}")
        Log.i(TAG, "  Mastered so far: ${filterIds.filter { student.isMastered(it) }.map { BoundaryAssessmentEngine.ID_TO_NODE[it] ?: "$it" }}")

        if (filterIds.all { student.isMastered(it) }) {
            Log.i(TAG, "🎓 ALL KCs MASTERED for digitMode=$digitMode!")
            newlyAllMastered = true
        }
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
            logUCBValues(zpd, focus)
            return focus
        }
        val selected       = bandit.selectConcept(zpd, practiceQuestionNo)
        focusKC            = selected
        focusQuestionCount = 1
        consecutiveWrong   = 0
        logUCBValues(zpd, selected)
        return selected
    }

    private fun logUCBValues(zpd: List<Int>, selected: Int) {
        val activeNodes = zpd.filter { bandit.hasArm(it) }
        for (kcId in activeNodes) {
            val node = bandit.getNode(kcId)
            if (node != null && node.timesPlayed > 0) {
                node.computeUCB(practiceQuestionNo)
            }
        }

        Log.i(TAG, "Qno: $practiceQuestionNo")
        for (kcId in zpd) {
            val node = bandit.getNode(kcId)
            val name = BoundaryAssessmentEngine.ID_TO_NODE[kcId] ?: "$kcId"
            if (node != null) {
                val mean = node.estimate.coerceIn(0.0, 1.0)
                val ucb = node.ucb.coerceIn(0.0, 1.0)
                Log.i(TAG, "  $name  mean: ${"%.2f".format(mean)}  ucb: ${"%.2f".format(ucb)}")
            } else {
                Log.i(TAG, "  $name  mean: 0.00  ucb: 0.00 (no arm)")
            }
        }
        val selName = BoundaryAssessmentEngine.ID_TO_NODE[selected] ?: "$selected"
        Log.i(TAG, "Concept-selected: $selName")
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
    private val MAX_GEN_ATTEMPTS = 10_000

    private fun generateNumbersForKC(kcId: Int): Pair<Int, Int> {

        fun nDigit(n: Int): Int {
            val lo = 10.0.pow(n - 1).toInt()
            val hi = 10.0.pow(n).toInt() - 1
            return (lo..hi).random()
        }

        fun hasCarry(a: Int, b: Int): Boolean {
            var x = a; var y = b; var carry = 0
            while (x > 0 || y > 0) {
                if ((x % 10) + (y % 10) + carry >= 10) return true
                carry = 0
                x /= 10; y /= 10
            }
            return false
        }

        fun carryCount(a: Int, b: Int): Int {
            var x = a; var y = b; var c = 0; var carry = 0
            while (x > 0 || y > 0) {
                val colSum = (x % 10) + (y % 10) + carry
                if (colSum >= 10) { c++; carry = 1 } else { carry = 0 }
                x /= 10; y /= 10
            }
            return c
        }

        fun hasBorrow(a: Int, b: Int): Boolean {
            var x = a; var y = b; var borrow = 0
            while (x > 0 || y > 0) {
                if ((x % 10) - borrow < (y % 10)) return true
                borrow = 0
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

        fun safePair(gen: () -> Pair<Int, Int>, predicate: (Int, Int) -> Boolean): Pair<Int, Int> {
            var a: Int; var b: Int; var attempts = 0
            do {
                val (ga, gb) = gen()
                a = ga; b = gb
                if (++attempts >= MAX_GEN_ATTEMPTS) {
                    Log.w(TAG, "⚠️ generateNumbersForKC: gave up after $MAX_GEN_ATTEMPTS attempts for kcId (returning last pair $a,$b)")
                    return Pair(a, b)
                }
            } while (!predicate(a, b))
            return Pair(a, b)
        }

        return when (kcId) {
            1  -> safePair({ Pair((1..9).random(), (1..9).random()) }) { a, b -> a + b < 10 }
            2  -> safePair({ Pair((1..9).random(), (1..9).random()) }) { a, b -> a + b >= 10 }
            3  -> safePair({ Pair(nDigit(2), (1..9).random()) }) { a, b -> !hasCarry(a, b) }
            4  -> safePair({ Pair(nDigit(2), (1..9).random()) }) { a, b -> hasCarry(a, b) }
            5  -> safePair({ Pair(nDigit(2), nDigit(2)) }) { a, b -> !hasCarry(a, b) }
            6  -> safePair({ Pair(nDigit(2), nDigit(2)) }) { a, b -> carryCount(a, b) == 2 }
            7  -> safePair({ Pair(nDigit(3), nDigit(3)) }) { a, b -> !hasCarry(a, b) }
            8  -> safePair({ Pair(nDigit(3), nDigit(2)) }) { a, b -> carryCount(a, b) == 1 }
            9  -> safePair({ Pair(nDigit(3), nDigit(3)) }) { a, b -> carryCount(a, b) >= 2 }
            10 -> safePair({ Pair((2..9).random(), (1..9).random()) }) { a, b -> b < a }
            11 -> safePair({ Pair(nDigit(2), (1..9).random()) }) { a, b -> !hasBorrow(a, b) }
            12 -> safePair({ Pair(nDigit(2), (1..9).random()) }) { a, b -> hasBorrow(a, b) && a - b >= 1 }
            13 -> safePair({ Pair(nDigit(2), nDigit(2)) }) { a, b -> !hasBorrow(a, b) && a > b }
            14 -> safePair({ Pair(nDigit(3), nDigit(3)) }) { a, b -> !hasBorrow(a, b) && a > b }
            15 -> safePair({ Pair(nDigit(2), nDigit(2)) }) { a, b -> hasBorrow(a, b) && a > b }
            16 -> safePair({ Pair(nDigit(3), nDigit(3)) }) { a, b -> borrowCount(a, b) == 1 && a > b }
            17 -> safePair({ Pair(nDigit(3), nDigit(3)) }) { a, b -> borrowCount(a, b) >= 2 && a > b }
            else -> Pair(1, 1)
        }
    }
}
