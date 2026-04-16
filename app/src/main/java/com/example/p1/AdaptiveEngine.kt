package com.example.p1

import kotlin.math.ln

class AdaptiveEngine {

    val student = StudentModel()

    // ── FPR now matches Python notebook exactly: 0.00009 → beta ≈ 9.32
    // Python call: klucbCUSUM({"A"}, ..., fpr=0.00009, ...)
    // beta = log(1/fpr) = log(1/0.00009) ≈ 9.32
    // This requires ~6 consecutive correct answers to declare mastery.
    private val fpr  = 0.00009
    private val beta = CUSUMDetector.thresholdFromFPR(fpr)  // ≈ 9.32

    private val bandit         = KLUCBBandit()
    private val cusumDetectors = mutableMapOf<Int, CUSUMDetector>()
    private var ts             = 0
    private var currentKC      = 1

    // ── Operation type ────────────────────────────────────────────────────────
    // "+" for addition (KCs 1–9), "-" for subtraction (KCs 10–17)
    private var operationType: String = "+"

    /** Set the active operation — resets the engine for that graph */
    fun setOperation(op: String) {
        operationType = op
        // Reset state for clean start on new operation
        bandit.clearAll()
        cusumDetectors.clear()
        ts = 0
        focusKC = null
        focusQuestionCount = 0
        consecutiveWrong = 0
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
    private val MIN_FOCUS                = 5
    private val FRUSTRATION_LIMIT        = 12

    // ── ZPD ancestor tracking — mirrors Python's trace_ancestor_dict ──────────
    // For each KC that is not yet in the ZPD, we track which active ZPD KCs
    // are its ancestors (i.e. prerequisites, direct or transitive).
    // A locked KC is only unlocked when ALL its ancestors have been mastered,
    // matching the Python check:
    //   if t in trace_ancestor_dict and
    //      any(tr in unsolvable for tr in trace_ancestor_dict[t]):
    //        continue   ← don't unlock yet
    private val kcAncestors = mutableMapOf<Int, MutableSet<Int>>()

    var correctAnswer: Int = 0
        private set

    init { initZPD() }

    // ─────────────────────────────────────────────────────────────────────────
    // Build ancestor map and seed initial ZPD — mirrors Python setup:
    //
    //   unsolvable = set of all descendants reachable from init_zpd
    //   trace_ancestor_dict[t] = list of ZPD ancestors of t
    // ─────────────────────────────────────────────────────────────────────────
    private fun initZPD() {
        kcAncestors.clear()
        val filterIds = activeKCIds()
        val initZpd = KnowledgeRepository.getZPD(student, filterIds)

        // Find all KCs reachable (directly or transitively) from the initial ZPD
        val reachable = mutableSetOf<Int>()
        val seed = ArrayDeque(initZpd)
        while (seed.isNotEmpty()) {
            val kcId = seed.removeFirst()
            val children = KnowledgeRepository.getChildren(kcId)
                .filter { it in filterIds }   // only consider KCs in current operation
            for (child in children) {
                if (reachable.add(child)) seed.addLast(child)
            }
        }

        // For each reachable-but-not-yet-ZPD KC, record which initial ZPD KCs
        // are its ancestors — matches Python's trace_ancestor_dict construction
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

    /** Returns true if `ancestor` is a transitive prerequisite of `kcId` */
    private fun isAncestor(ancestor: Int, kcId: Int): Boolean {
        val prereqs = KnowledgeRepository.getPrerequisites(kcId)
        if (ancestor in prereqs) return true
        return prereqs.any { isAncestor(ancestor, it) }
    }

    private fun addArmIfNeeded(kcId: Int) {
        if (!bandit.hasArm(kcId)) {
            bandit.addArm(kcId, ts)
            // pg and ps are now sampled from Beta(20,160) inside KLUCBBandit.addArm(),
            // so we read them back from the node to keep CUSUM consistent.
            val node = bandit.getNode(kcId)!!
            cusumDetectors[kcId] = CUSUMDetector(
                pg        = node.pg,
                ps        = node.ps,
                threshold = beta
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // generateQuestion
    // ─────────────────────────────────────────────────────────────────────────
    fun generateQuestion(): Pair<String, List<Int>> {
        val filterIds = activeKCIds()
        val zpd = KnowledgeRepository.getZPD(student, filterIds)
        for (kcId in zpd) addArmIfNeeded(kcId)

        if (bandit.activeArms().isEmpty()) {
            return Pair("🎉 All concepts mastered!", listOf(0, 0, 0, 0))
        }

        currentKC = chooseKC(zpd)

        val (num1, num2) = generateNumbersForKC(currentKC)
        val op = KnowledgeRepository.getOperationType(currentKC)
        correctAnswer = if (op == "-") num1 - num2 else num1 + num2

        val opSymbol = if (op == "-") "−" else "+"
        val question = "$num1 $opSymbol $num2 = ?"

        val distractors = DistractorGenerator.generate(currentKC, num1, num2, correctAnswer, needed = 3)
        val options = (listOf(correctAnswer) + distractors).shuffled()

        return Pair(question, options)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // submitAnswer
    // ─────────────────────────────────────────────────────────────────────────
    fun submitAnswer(selected: Int): String {
        val isCorrect = selected == correctAnswer
        ts++

        bandit.update(currentKC, isCorrect)

        // BKT is now a stochastic state flip — matches Python Student._update_state()
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
    // onMastery — mirrors Python mastery block:
    //
    //   del ucb1_trace_node_dict[chosen_trace]
    //   for t in progression_graph[chosen_trace]:
    //       if t in trace_ancestor_dict and
    //          any(tr in unsolvable for tr in trace_ancestor_dict[t]):
    //           continue        ← still has unmastered ancestors, skip
    //       if t not in ucb1_trace_node_dict:
    //           ucb1_trace_node_dict[t] = KLUCB_Node2(ts, ...)
    // ─────────────────────────────────────────────────────────────────────────
    private fun onMastery(kcId: Int) {
        student.setMastered(kcId)
        bandit.removeArm(kcId)
        cusumDetectors.remove(kcId)
        focusKC            = null
        focusQuestionCount = 0
        consecutiveWrong   = 0
        println("✅ MASTERED KC $kcId: ${KnowledgeRepository.components[kcId]?.name}")

        // Remove mastered KC from all ancestor sets
        for (ancestors in kcAncestors.values) ancestors.remove(kcId)

        // Unlock children — only if ALL their ancestors are now mastered
        val filterIds = activeKCIds()
        val children = KnowledgeRepository.getChildren(kcId)
            .filter { it in filterIds }
        for (childKc in children) {
            // Matches Python:
            //   if t in trace_ancestor_dict and
            //      any(tr in unsolvable for tr in trace_ancestor_dict[t]):
            //       continue
            val remainingAncestors = kcAncestors[childKc]
            if (remainingAncestors != null && remainingAncestors.isNotEmpty()) {
                // Still has unmastered ancestors — don't unlock yet
                continue
            }
            // All ancestors mastered — add to bandit if not already there
            if (!student.isMastered(childKc)) {
                addArmIfNeeded(childKc)
            }
        }

        // Also re-check the full ZPD in case getZPD picks up anything missed
        for (newKc in KnowledgeRepository.getZPD(student, filterIds)) addArmIfNeeded(newKc)
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
    // Problem generation — addition + subtraction
    // ─────────────────────────────────────────────────────────────────────────
    private fun generateNumbersForKC(kcId: Int): Pair<Int, Int> {

        fun nDigit(n: Int): Int {
            val lo = Math.pow(10.0, (n - 1).toDouble()).toInt()
            val hi = Math.pow(10.0, n.toDouble()).toInt() - 1
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

        /** Check if subtracting b from a requires borrowing at any digit position */
        fun hasBorrow(a: Int, b: Int): Boolean {
            var x = a; var y = b
            while (x > 0 || y > 0) {
                if ((x % 10) < (y % 10)) return true
                x /= 10; y /= 10
            }
            return false
        }

        /** Count how many digit positions need borrowing */
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
            // ── ADDITION ─────────────────────────────────────────────────────
            // 1A: 1-digit + 1-digit, no carry (sum < 10)
            1  -> { var a: Int; var b: Int; do { a = (1..9).random(); b = (1..9).random() } while (a + b >= 10); Pair(a, b) }
            // 1AC: 1-digit + 1-digit, with carry (sum >= 10)
            2  -> { var a: Int; var b: Int; do { a = (1..9).random(); b = (1..9).random() } while (a + b < 10); Pair(a, b) }
            // 2A1: 2-digit + 1-digit, no carry
            3  -> { var a: Int; var b: Int; do { a = nDigit(2); b = (1..9).random() } while (hasCarry(a, b)); Pair(a, b) }
            // 2A1C: 2-digit + 1-digit, with carry
            4  -> { var a: Int; var b: Int; do { a = nDigit(2); b = (1..9).random() } while (!hasCarry(a, b)); Pair(a, b) }
            // 2A2: 2-digit + 2-digit, no carry
            5  -> { var a: Int; var b: Int; do { a = nDigit(2); b = nDigit(2) } while (hasCarry(a, b)); Pair(a, b) }
            // 2A2C: 2-digit + 2-digit, double carry
            6  -> { var a: Int; var b: Int; do { a = nDigit(2); b = nDigit(2) } while (!hasCarry(a, b)); Pair(a, b) }
            // 3A: 3-digit + 3-digit, no carry
            7  -> { var a: Int; var b: Int; do { a = nDigit(3); b = nDigit(3) } while (hasCarry(a, b)); Pair(a, b) }
            // 3AC: 3-digit + 3-digit, single carry
            8  -> { var a: Int; var b: Int; do { a = nDigit(3); b = nDigit(3) } while (carryCount(a, b) != 1); Pair(a, b) }
            // 3AC2: 3-digit + 3-digit, double carry
            9  -> { var a: Int; var b: Int; do { a = nDigit(3); b = nDigit(3) } while (carryCount(a, b) < 2); Pair(a, b) }

            // ── SUBTRACTION ──────────────────────────────────────────────────
            // 1S: 1-digit − 1-digit, no borrow (a > b, single digit result)
            10 -> { var a: Int; var b: Int; do { a = (2..9).random(); b = (1..9).random() } while (b >= a); Pair(a, b) }
            // 2S1: 2-digit − 1-digit, no borrow
            11 -> { var a: Int; var b: Int; do { a = nDigit(2); b = (1..9).random() } while (hasBorrow(a, b)); Pair(a, b) }
            // 2S1B: 2-digit − 1-digit, with borrow
            12 -> { var a: Int; var b: Int; do { a = nDigit(2); b = (1..9).random() } while (!hasBorrow(a, b) || a - b < 1); Pair(a, b) }
            // 2S2: 2-digit − 2-digit, no borrow
            13 -> { var a: Int; var b: Int; do { a = nDigit(2); b = nDigit(2) } while (hasBorrow(a, b) || a <= b); Pair(a, b) }
            // 3S: 3-digit − 3-digit, no borrow
            14 -> { var a: Int; var b: Int; do { a = nDigit(3); b = nDigit(3) } while (hasBorrow(a, b) || a <= b); Pair(a, b) }
            // 2S2B: 2-digit − 2-digit, with borrow
            15 -> { var a: Int; var b: Int; do { a = nDigit(2); b = nDigit(2) } while (!hasBorrow(a, b) || a <= b); Pair(a, b) }
            // 3SB: 3-digit − 3-digit, single borrow
            16 -> { var a: Int; var b: Int; do { a = nDigit(3); b = nDigit(3) } while (borrowCount(a, b) != 1 || a <= b); Pair(a, b) }
            // 3SB2: 3-digit − 3-digit, double borrow
            17 -> { var a: Int; var b: Int; do { a = nDigit(3); b = nDigit(3) } while (borrowCount(a, b) < 2 || a <= b); Pair(a, b) }

            else -> Pair(1, 1)
        }
    }

    private fun hasCarry(a: Int, b: Int): Boolean {
        var x = a; var y = b
        while (x > 0 || y > 0) {
            if ((x % 10) + (y % 10) >= 10) return true
            x /= 10; y /= 10
        }
        return false
    }
}