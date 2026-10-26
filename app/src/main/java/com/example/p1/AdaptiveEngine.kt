package com.example.p1

import android.util.Log
import kotlin.math.pow

class AdaptiveEngine {

    private val TAG = "AdaptiveSystem"

    // ── Mastery tracking (inlined; StudentModel removed) ─────────────────────
    private val masteredKCs = mutableSetOf<Int>()
    private fun isMastered(kcId: Int): Boolean = kcId in masteredKCs
    private fun setMastered(kcId: Int) { masteredKCs.add(kcId) }
    private fun resetStudent() { masteredKCs.clear() }

    /** Prerequisites outside the current level's KC set count as mastered. */
    private fun masteredInLevel(kcId: Int, filterIds: List<Int>): Boolean =
        isMastered(kcId) || kcId !in filterIds

    private fun zpdFor(filterIds: List<Int>): List<Int> =
        KnowledgeRepository.getZPD({ masteredInLevel(it, filterIds) }, filterIds)

    private val fpr   = 0.00009
    private val beta  = CUSUMDetector.thresholdFromFPR(fpr)  // ≈ 9.32

    private val bandit         = KLUCBBandit()
    private val cusumDetectors = mutableMapOf<Int, CUSUMDetector>()
    private var ts             = 0
    private var currentKC      = 1

    // ── Phase tracking ────────────────────────────────────────────────────────
    enum class Phase { ASSESSMENT, LEARNING }
    var currentPhase = Phase.ASSESSMENT
        private set
    private var assessmentResponseString = ""

    // Guard flag — prevents handleTerminalAssessment() from being called twice.
    private var terminalHandled = false

    // ── Confirmation state ────────────────────────────────────────────────────
    private var pendingConfirmKC: Int? = null

    var newlyFoundBoundary: Set<String>? = null
        private set

    fun consumeBoundary(): Set<String>? {
        val b = newlyFoundBoundary
        newlyFoundBoundary = null
        return b
    }

    var newlyAllMastered: Boolean = false
        private set

    var assessmentProvedAllMastered: Boolean = false
        private set

    fun consumeAllMastered(): Boolean {
        val v = newlyAllMastered
        newlyAllMastered = false
        return v
    }

    data class MasteryEvent(val kcId: Int, val conceptName: String, val correctnessRecord: List<Boolean>)
    var lastMasteryEvent: MasteryEvent? = null; private set
    fun consumeMasteryEvent(): MasteryEvent? { val e = lastMasteryEvent; lastMasteryEvent = null; return e }

    var lastZpdUpdate: List<String>? = null; private set
    fun consumeZpdUpdate(): List<String>? { val e = lastZpdUpdate; lastZpdUpdate = null; return e }

    private var operationType: String = "+"
    private var digitMode: Int = 3

    // ── Recent-question tracking (no-repeat window) ───────────────────────────
    // Only KCs with tiny problem spaces need this.
    private val noRepeatKCs   = setOf(1, 2, 10, 18, 19)
    private val recentWindow  = 5
    private val recentPairs   = mutableMapOf<Int, ArrayDeque<Pair<Int, Int>>>()

    fun startSession(op: String, mode: Int) {
        Log.i(TAG, "--- STARTING NEW SESSION ---")
        operationType = op
        digitMode = mode
        Log.i(TAG, "Operation: $op | DigitMode: $digitMode")
        bandit.clearAll()
        cusumDetectors.clear()
        recentPairs.clear()
        ts = 0
        detectionQuestionNo = 0
        practiceQuestionNo = 0
        focusKC = null
        focusQuestionCount = 0
        consecutiveWrong = 0
        pendingConfirmKC = null
        terminalHandled = false
        currentPhase = if (digitMode == 1 || digitMode == 2) Phase.LEARNING else Phase.ASSESSMENT
        assessmentResponseString = ""
        assessmentProvedAllMastered = false
        resetStudent()
        initZPD()
    }

    private fun activeKCIds(): List<Int> {
        val isSub = operationType == "-"
        val isMul = operationType == "×"
        val isDiv = operationType == "÷"
        val base = when {
            isSub -> KnowledgeRepository.subtractionIds
            isMul -> KnowledgeRepository.multiplicationIds
            isDiv -> KnowledgeRepository.divisionIds
            else  -> KnowledgeRepository.additionIds
        }
        return when (digitMode) {
            1    -> when {
                isSub -> listOf(10, 11)
                isMul -> listOf(18, 19)                      // T5, T10
                isDiv -> listOf(29, 30, 31)                  // 2D1, 3D1, 2D1R
                else  -> listOf(1, 2)
            }
            // Level 2: no 3-digit KCs for +/- (3S = 14 excluded)
            2    -> when {
                isSub -> listOf(10, 11, 12, 13, 15)
                isMul -> listOf(20, 21, 22, 23, 24)          // 2M1, 2M1C, 3M1, 3M1C, 3M1C2
                isDiv -> listOf(32, 33, 34)                  // 3D1R, 3D1Z, 4D1R
                else  -> (1..6).toList()
            }
            3    -> when {
                isMul -> listOf(25, 26, 27, 28)              // 2M2, 2M2C, 3M2, 3M2C
                isDiv -> listOf(35, 36, 37)                  // 3D2, 3D2R, 4D2R
                else  -> base
            }
            else -> base
        }
    }

    // ── Focus mode ────────────────────────────────────────────────────────────
    private var focusKC:            Int? = null
    private var focusQuestionCount: Int  = 0
    private var consecutiveWrong:   Int  = 0
    private val minFocus                 = 5
    private val frustrationLimit         = 12

    private val kcAncestors = mutableMapOf<Int, MutableSet<Int>>()

    var correctAnswer: Int = 0
        private set

    var lastNum1: Int = 0; private set
    var lastNum2: Int = 0; private set
    var lastQuestionText: String = ""; private set
    val currentKCName: String get() = BoundaryDetector.ID_TO_NODE[currentKC] ?: "$currentKC"
    val currentKCId: Int get() = currentKC
    var detectionQuestionNo: Int = 0; private set
    var practiceQuestionNo: Int = 0; private set

    val activeConceptsNames: List<String>
        get() {
            val filterIds = activeKCIds()
            return filterIds.map { BoundaryDetector.ID_TO_NODE[it] ?: "$it" }
        }

    val currentZpdNames: List<String>
        get() {
            val filterIds = activeKCIds()
            val zpd = zpdFor(filterIds)
            return zpd.map { BoundaryDetector.ID_TO_NODE[it] ?: "$it" }
        }

    val masteredConceptNames: Set<String>
        get() {
            val filterIds = activeKCIds()
            return filterIds.filter { isMastered(it) }
                .map { BoundaryDetector.ID_TO_NODE[it] ?: "$it" }
                .toSet()
        }

    init { initZPD() }

    private fun initZPD() {
        kcAncestors.clear()
        val filterIds = activeKCIds()
        val initZpd = zpdFor(filterIds)

        Log.i(TAG, "── initZPD() ──")
        Log.i(TAG, "  filterIds = $filterIds")
        Log.i(TAG, "  mastered  = ${filterIds.filter { isMastered(it) }.map { "$it(${KnowledgeRepository.components[it]?.name})" }}")
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
            val kc = KnowledgeRepository.components[kcId]!!
            cusumDetectors[kcId] = CUSUMDetector(
                pg        = kc.guessProbability,
                ps        = kc.slipProbability,
                threshold = beta
            )
        }
    }

    private fun getNextAssessmentState(responseString: String): BoundaryState {
        return when (operationType) {
            "-"  -> BoundaryDetector.getSubtractionState(responseString)
            "×"  -> BoundaryDetector.getMultiplicationState(responseString)
            "÷"  -> BoundaryDetector.getDivisionState(responseString)
            else -> BoundaryDetector.getAdditionState(responseString)
        }
    }

    private fun updateAssessmentNode(kcId: Int, correct: Boolean): Boolean? {
        return if (pendingConfirmKC == kcId) {
            pendingConfirmKC = null
            Log.d(TAG, "[ASSESS KC $kcId] confirmation response: ${if (correct) "PASS" else "FAIL"}")
            correct
        } else {
            if (correct) {
                pendingConfirmKC = kcId
                Log.d(TAG, "[ASSESS KC $kcId] first answer correct — asking confirmation question")
                null
            } else {
                Log.d(TAG, "[ASSESS KC $kcId] first answer wrong — FAIL")
                false
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Question building (shared by all phases / operations)
    // ─────────────────────────────────────────────────────────────────────────
    private fun buildQuestionForCurrentKC(): Pair<String, List<Int>> {
        val (num1, num2) = generateNumbersForKC(currentKC)
        lastNum1 = num1; lastNum2 = num2
        val op = KnowledgeRepository.getOperationType(currentKC)
        correctAnswer = when (op) {
            "-"  -> num1 - num2
            "×"  -> num1 * num2
            "÷"  -> num1 / num2          // quotient (remainder KCs: floor quotient)
            else -> num1 + num2
        }

        val opSymbol = when (op) { "-" -> "−"; "×" -> "×"; "÷" -> "÷"; else -> "+" }
        val question = "$num1 $opSymbol $num2 = ?"
        lastQuestionText = "$num1$op$num2"

        val distractors = DistractorGenerator.generate(currentKC, num1, num2, correctAnswer, needed = 3)
        val options = (listOf(correctAnswer) + distractors).shuffled()
        return Pair(question, options)
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
        val zpd = zpdFor(filterIds)

        Log.d(TAG, "[generateLearningQuestion] ZPD = ${zpd.map { "$it(${BoundaryDetector.ID_TO_NODE[it] ?: it})" }}")
        Log.d(TAG, "  mastered = ${filterIds.filter { isMastered(it) }.map { BoundaryDetector.ID_TO_NODE[it] ?: "$it" }}")

        for (kcId in zpd) addArmIfNeeded(kcId)

        if (zpd.isEmpty()) {
            if (filterIds.all { isMastered(it) }) {
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
        return buildQuestionForCurrentKC()
    }

    private fun generateAssessmentQuestion(): Pair<String, List<Int>> {
        val confirmKC = pendingConfirmKC
        if (confirmKC != null) {
            currentKC = confirmKC
            Log.i(TAG, "[ASSESSMENT] Confirmation question for KC $confirmKC (${BoundaryDetector.ID_TO_NODE[confirmKC]}) | path='$assessmentResponseString'")

            detectionQuestionNo++
            return buildQuestionForCurrentKC()
        }

        return when (val state = getNextAssessmentState(assessmentResponseString)) {
            is BoundaryState.Ask -> {
                val kcId = state.kcId
                currentKC = kcId
                Log.i(TAG, "[ASSESSMENT] Asking KC $kcId (${BoundaryDetector.ID_TO_NODE[kcId]}) | path='$assessmentResponseString'")

                detectionQuestionNo++
                buildQuestionForCurrentKC()
            }
            is BoundaryState.Terminal -> {
                if (!terminalHandled) {
                    terminalHandled = true
                    handleTerminalAssessment(state)
                }
                currentPhase = Phase.LEARNING
                generateLearningQuestion()
            }
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
                if (nextState is BoundaryState.Terminal && !terminalHandled) {
                    terminalHandled = true
                    handleTerminalAssessment(nextState)
                    currentPhase = Phase.LEARNING
                }
            }

            return if (isCorrect) "Correct! 🎉" else "Wrong! The answer was $correctAnswer"
        }

        // ── LEARNING phase ────────────────────────────────────────────────────

        bandit.update(currentKC, isCorrect)

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

    private fun handleTerminalAssessment(terminal: BoundaryState.Terminal) {
        Log.i(TAG, "── Assessment Terminal State reached ──")
        Log.i(TAG, "  Solvable: ${terminal.solvable}")
        Log.i(TAG, "  Boundary (Current Level): ${terminal.boundary}")

        newlyFoundBoundary = terminal.boundary.mapNotNull { BoundaryDetector.ID_TO_NODE[it] }.toSet()

        val solvableIds = terminal.solvable
        val filterIds   = activeKCIds()

        val toPremaster = terminal.solvable.filter { kcId ->
            if (kcId !in terminal.boundary) return@filter true
            val prereqs = KnowledgeRepository.getPrerequisites(kcId).filter { it in filterIds }
            prereqs.all { it in solvableIds }
        }
        for (kcId in toPremaster) {
            setMastered(kcId)
            bandit.removeArm(kcId)
            cusumDetectors.remove(kcId)
        }

        val boundaryNotPremastered = terminal.boundary.filter { it !in toPremaster }
        for (kcId in boundaryNotPremastered) {
            bandit.removeArm(kcId)
            cusumDetectors.remove(kcId)
        }

        currentPhase = Phase.LEARNING
        initZPD()

        if (filterIds.all { isMastered(it) }) {
            Log.i(TAG, "🎓 Assessment proved ALL KCs mastered for digitMode=$digitMode!")
            newlyAllMastered = true
            assessmentProvedAllMastered = true
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // onMastery
    // ─────────────────────────────────────────────────────────────────────────
    private fun onMastery(kcId: Int) {
        val record = cusumDetectors[kcId]?.correctnessRecord?.toList() ?: emptyList()
        val conceptName = BoundaryDetector.ID_TO_NODE[kcId] ?: "$kcId"
        lastMasteryEvent = MasteryEvent(kcId, conceptName, record)

        setMastered(kcId)
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
            val allPrereqsMet = prereqs.all { isMastered(it) }
            if (!allPrereqsMet) {
                Log.d(TAG, "  KC $childKc (${BoundaryDetector.ID_TO_NODE[childKc]}) still locked: " +
                        "unmastered prereqs = ${prereqs.filter { !isMastered(it) }.map { BoundaryDetector.ID_TO_NODE[it] ?: "$it" }}")
                continue
            }
            if (!isMastered(childKc)) {
                addArmIfNeeded(childKc)
                unlockedKCs.add(childKc)
            }
        }
        if (unlockedKCs.isNotEmpty()) {
            Log.d(TAG, "Unlocked KCs into ZPD: ${unlockedKCs.map { "$it(${BoundaryDetector.ID_TO_NODE[it]})" }}")
            lastZpdUpdate = unlockedKCs.map { BoundaryDetector.ID_TO_NODE[it] ?: "$it" }
        }

        val newZpd = zpdFor(filterIds)
        for (newKc in newZpd) addArmIfNeeded(newKc)

        Log.i(TAG, "  After mastery of ${BoundaryDetector.ID_TO_NODE[kcId] ?: kcId}:")
        Log.i(TAG, "  New ZPD = ${newZpd.map { "${BoundaryDetector.ID_TO_NODE[it] ?: it}" }}")
        Log.i(TAG, "  Mastered so far: ${filterIds.filter { isMastered(it) }.map { BoundaryDetector.ID_TO_NODE[it] ?: "$it" }}")

        if (filterIds.all { isMastered(it) }) {
            Log.i(TAG, "🎓 ALL KCs MASTERED for digitMode=$digitMode!")
            newlyAllMastered = true
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // chooseKC
    // ─────────────────────────────────────────────────────────────────────────
    private fun chooseKC(zpd: List<Int>): Int {
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
            val name = BoundaryDetector.ID_TO_NODE[kcId] ?: "$kcId"
            if (node != null) {
                val mean = node.estimate.coerceIn(0.0, 1.0)
                val ucb = node.ucb.coerceIn(0.0, 1.0)
                Log.i(TAG, "  $name  mean: ${"%.2f".format(mean)}  ucb: ${"%.2f".format(ucb)}")
            } else {
                Log.i(TAG, "  $name  mean: 0.00  ucb: 0.00 (no arm)")
            }
        }
        val selName = BoundaryDetector.ID_TO_NODE[selected] ?: "$selected"
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

    // Minimum |a − b| so operands are not "very close"
    private val MIN_GAP_2D = 20    // 2-digit − 2-digit (KC 13)
    private val MIN_GAP_3D = 100   // 3-digit − 3-digit (KC 14, 16, 17)

    /**
     * Wrapper: for tiny-problem-space KCs (1, 2, 10, 18, 19) avoids repeating any
     * of the last [recentWindow] questions. Addition/multiplication treat a∘b and b∘a as same.
     */
    private fun generateNumbersForKC(kcId: Int): Pair<Int, Int> {
        if (kcId !in noRepeatKCs) return generateRawNumbersForKC(kcId)

        val recent = recentPairs.getOrPut(kcId) { ArrayDeque() }
        val commutative = KnowledgeRepository.getOperationType(kcId) != "-"

        var pair = generateRawNumbersForKC(kcId)
        var attempts = 0
        while (attempts < 100 &&
            recent.any { it == pair || (commutative && it == Pair(pair.second, pair.first)) }
        ) {
            pair = generateRawNumbersForKC(kcId)
            attempts++
        }

        recent.addLast(pair)
        while (recent.size > recentWindow) recent.removeFirst()
        return pair
    }

    private fun generateRawNumbersForKC(kcId: Int): Pair<Int, Int> {

        fun nDigit(n: Int): Int {
            val lo = 10.0.pow(n - 1).toInt()
            val hi = 10.0.pow(n).toInt() - 1
            return (lo..hi).random()
        }

        // n-digit number whose digits are each random in 1..maxD (never 0)
        fun randDigits(n: Int, maxD: Int): Int =
            (1..n).fold(0) { acc, _ -> acc * 10 + (1..maxD).random() }

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

        // Number of carries when multiplying multi-digit a by single digit b
        fun mulCarryCount(a: Int, b: Int): Int {
            var x = a; var carry = 0; var c = 0
            while (x > 0) {
                val p = (x % 10) * b + carry
                if (p >= 10) c++
                carry = p / 10
                x /= 10
            }
            return c
        }

        // True if every column of the long-multiplication convolution is ≤ 9
        fun mulColsNoCarry(a: Int, b: Int): Boolean {
            val da = a.toString().reversed().map { it.digitToInt() }
            val db = b.toString().reversed().map { it.digitToInt() }
            for (k in 0..(da.size + db.size - 2)) {
                var sum = 0
                for (i in da.indices) {
                    val j = k - i
                    if (j in db.indices) sum += da[i] * db[j]
                }
                if (sum > 9) return false
            }
            return true
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

        // 3-digit minuend, subtrahend with 1, 2 or 3 digits
        fun threeDigitMinuend(): Pair<Int, Int> = Pair(nDigit(3), nDigit((1..3).random()))

        // If subtrahend is 3-digit, operands must not be very close
        fun notTooClose3(a: Int, b: Int): Boolean = b < 100 || a - b >= MIN_GAP_3D

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
            13 -> safePair({ Pair(nDigit(2), nDigit(2)) }) { a, b ->
                !hasBorrow(a, b) && a > b && a - b >= MIN_GAP_2D
            }
            14 -> safePair({ threeDigitMinuend() }) { a, b ->
                !hasBorrow(a, b) && a > b && notTooClose3(a, b)
            }
            15 -> safePair({ Pair(nDigit(2), nDigit(2)) }) { a, b -> hasBorrow(a, b) && a > b }
            16 -> safePair({ threeDigitMinuend() }) { a, b ->
                borrowCount(a, b) == 1 && a > b && notTooClose3(a, b)
            }
            17 -> safePair({ threeDigitMinuend() }) { a, b ->
                borrowCount(a, b) >= 2 && a > b && notTooClose3(a, b)
            }

            // ── MULTIPLICATION ───────────────────────────────────────────────
            // T5: one factor 2..5, other 2..9 (no ×1)
            18 -> {
                val a = (2..5).random(); val b = (2..9).random()
                if ((0..1).random() == 0) Pair(a, b) else Pair(b, a)
            }
            // T10: both factors 6..9 (excludes 5×6 etc. which belong to T5)
            19 -> Pair((6..9).random(), (6..9).random())
            // 2M1: 2-digit × (2..4), every digit product < 10, no zero digits
            20 -> {
                val b = (2..4).random()
                Pair(randDigits(2, 9 / b), b)
            }
            // 2M1C: 2-digit × (2..9) with carry, ones digit ≠ 0
            21 -> safePair({ Pair(nDigit(2), (2..9).random()) }) { a, b ->
                a % 10 != 0 && mulCarryCount(a, b) >= 1
            }
            // 3M1: 3-digit × (2..3), no carry, no zero digits
            22 -> {
                val b = (2..3).random()
                Pair(randDigits(3, 9 / b), b)
            }
            // 3M1C: exactly one carry
            23 -> safePair({ Pair(nDigit(3), (2..9).random()) }) { a, b ->
                a % 10 != 0 && mulCarryCount(a, b) == 1
            }
            // 3M1C2: two or more carries
            24 -> safePair({ Pair(nDigit(3), (2..9).random()) }) { a, b ->
                a % 10 != 0 && mulCarryCount(a, b) >= 2
            }
            // 2M2: no carry in any column
            25 -> safePair({ Pair(randDigits(2, 3), randDigits(2, 3)) }) { a, b ->
                mulColsNoCarry(a, b)
            }
            // 2M2C: carry, neither operand a multiple of 10
            26 -> safePair({ Pair(nDigit(2), nDigit(2)) }) { a, b ->
                a % 10 != 0 && b % 10 != 0 && !mulColsNoCarry(a, b)
            }
            // 3M2: no carry; multiplier has no zero digit (so never a multiple of 10)
            27 -> safePair({ Pair(randDigits(3, 3), randDigits(2, 3)) }) { a, b ->
                mulColsNoCarry(a, b)
            }
            // 3M2C: carry, neither operand a multiple of 10
            28 -> safePair({ Pair(nDigit(3), nDigit(2)) }) { a, b ->
                a % 10 != 0 && b % 10 != 0 && !mulColsNoCarry(a, b)
            }

            // ── DIVISION (answer = quotient) ─────────────────────────────────
            // 2D1: 2-digit ÷ 1-digit, exact
            29 -> safePair({ Pair(nDigit(2), (2..9).random()) }) { a, b -> a % b == 0 && a / b >= 2 }
            // 3D1: 3-digit ÷ 1-digit, exact
            30 -> safePair({ Pair(nDigit(3), (2..9).random()) }) { a, b -> a % b == 0 }
            // 2D1R: 2-digit ÷ 1-digit, remainder
            31 -> safePair({ Pair(nDigit(2), (2..9).random()) }) { a, b -> a % b != 0 && a / b >= 2 }
            // 3D1R: 3-digit ÷ 1-digit, remainder
            32 -> safePair({ Pair(nDigit(3), (2..9).random()) }) { a, b -> a % b != 0 }
            // 3D1Z: 3-digit ÷ 1-digit, zero digit in quotient
            33 -> safePair({ Pair(nDigit(3), (2..9).random()) }) { a, b ->
                a / b >= 10 && a / b < 1000 && a / b % 10 == 0 || (a / b >= 100 && (a / b) / 10 % 10 == 0)
            }
            // 4D1R: 4-digit ÷ 1-digit, remainder
            34 -> safePair({ Pair(nDigit(4), (2..9).random()) }) { a, b -> a % b != 0 }
            // 3D2: 3-digit ÷ 2-digit, exact (divisor not a multiple of 10)
            35 -> safePair({ Pair(nDigit(3), nDigit(2)) }) { a, b ->
                b % 10 != 0 && a % b == 0 && a / b >= 2
            }
            // 3D2R: 3-digit ÷ 2-digit, remainder
            36 -> safePair({ Pair(nDigit(3), nDigit(2)) }) { a, b ->
                b % 10 != 0 && a % b != 0 && a / b >= 2
            }
            // 4D2R: 4-digit ÷ 2-digit, remainder
            37 -> safePair({ Pair(nDigit(4), nDigit(2)) }) { a, b ->
                b % 10 != 0 && a % b != 0 && a / b >= 10
            }
            else -> Pair(1, 1)
        }
    }
}