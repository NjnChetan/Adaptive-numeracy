package com.example.p1

import kotlin.math.ln

class AdaptiveEngine {

    val student = StudentModel()

    // ── FPR now matches Python notebook exactly: 0.00009 → beta ≈ 9.32
    // Python call: klucbCUSUM({\"A\"}, ..., fpr=0.00009, ...)
    // beta = log(1/fpr) = log(1/0.00009) ≈ 9.32
    // This requires ~6 consecutive correct answers to declare mastery.
    private val fpr  = 0.00009
    private val beta = CUSUMDetector.thresholdFromFPR(fpr)  // ≈ 9.32

    private val bandit         = KLUCBBandit()
    private val cusumDetectors = mutableMapOf<Int, CUSUMDetector>()
    private var ts             = 0
    private var currentKC      = 1

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
        val initZpd = KnowledgeRepository.getZPD(student)

        // Find all KCs reachable (directly or transitively) from the initial ZPD
        val reachable = mutableSetOf<Int>()
        val seed = ArrayDeque(initZpd)
        while (seed.isNotEmpty()) {
            val kcId = seed.removeFirst()
            val children = KnowledgeRepository.getChildren(kcId)
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
        val zpd = KnowledgeRepository.getZPD(student)
        for (kcId in zpd) addArmIfNeeded(kcId)

        if (bandit.activeArms().isEmpty()) {
            return Pair("🎉 All concepts mastered!", listOf(0, 0, 0, 0))
        }

        currentKC = chooseKC(zpd)

        val (num1, num2) = generateNumbersForKC(currentKC)
        correctAnswer    = num1 + num2

        val question = "$num1 + $num2 = ?"

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
        val children = KnowledgeRepository.getChildren(kcId)
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
        for (newKc in KnowledgeRepository.getZPD(student)) addArmIfNeeded(newKc)
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
    // Problem generation — unchanged
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

        return when (kcId) {
            1  -> { var a: Int; var b: Int; do { a = (1..9).random(); b = (1..9).random() } while (a + b >= 10); Pair(a, b) }
            2  -> { var a: Int; var b: Int; do { a = nDigit(2); b = nDigit(2) } while (hasCarry(a, b)); Pair(a, b) }
            3  -> { var a: Int; var b: Int; do { a = (1..9).random(); b = (1..9).random() } while (a + b < 10); Pair(a, b) }
            4  -> { var a: Int; var b: Int; do { a = nDigit(2); b = (1..9).random() } while (hasCarry(a, b)); Pair(a, b) }
            5  -> { var a: Int; var b: Int; do { a = nDigit(2); b = (1..9).random() } while (!hasCarry(a, b)); Pair(a, b) }
            6  -> { var a: Int; var b: Int; do { a = nDigit(2); b = nDigit(2) } while (!hasCarry(a, b)); Pair(a, b) }
            7  -> { var a: Int; var b: Int; do { a = nDigit(3); b = nDigit(3) } while (hasCarry(a, b)); Pair(a, b) }
            8  -> { var a: Int; var b: Int; do { a = nDigit(3); b = nDigit(3) } while (carryCount(a, b) != 1); Pair(a, b) }
            9  -> { var a: Int; var b: Int; do { a = nDigit(3); b = nDigit(3) } while (carryCount(a, b) != 2); Pair(a, b) }
            10 -> { var a: Int; var b: Int; do { a = nDigit(3); b = nDigit(3) } while (carryCount(a, b) < 3); Pair(a, b) }
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