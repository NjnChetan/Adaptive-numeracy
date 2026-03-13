package com.example.p1

enum class KCState { NOT_LEARNED, LEARNED }

class StudentModel {

    // Official mastery state — only CUSUM (via AdaptiveEngine.onMastery) may promote a KC
    // Covers addition ids 1–10 and subtraction ids 11–18
    val kcStates: MutableMap<Int, KCState> = mutableMapOf<Int, KCState>().apply {
        for (id in 1..10)  put(id, KCState.NOT_LEARNED)
        for (id in 11..18) put(id, KCState.NOT_LEARNED)
    }

    // BKT internal belief P(learned) — updated after each answer, never sets mastery
    private val bktBelief: MutableMap<Int, Double> = mutableMapOf<Int, Double>().apply {
        for (id in 1..10)  put(id, 0.0)
        for (id in 11..18) put(id, 0.0)
    }

    fun isMastered(kcId: Int): Boolean = kcStates[kcId] == KCState.LEARNED

    /** Called ONLY by AdaptiveEngine.onMastery() after CUSUM fires */
    fun setMastered(kcId: Int) {
        kcStates[kcId] = KCState.LEARNED
        bktBelief[kcId] = 1.0
    }

    /**
     * BKT belief update — updates bktBelief only, never touches kcStates.
     *
     * Standard BKT:
     *   posterior = P(L | obs)
     *   next      = posterior + (1 - posterior) * pT
     *
     * pT uses lowTransition if any prerequisite is unmastered,
     * highTransition if all prerequisites are mastered.
     */
    fun bktUpdateBelief(kcId: Int, correct: Boolean) {
        val pL  = bktBelief[kcId] ?: 0.0
        val pg  = KnowledgeRepository.getGuessProb(kcId)
        val ps  = KnowledgeRepository.getSlipProb(kcId)
        val tp  = KnowledgeRepository.getTransition(kcId)

        val prereqs = KnowledgeRepository.getPrerequisites(kcId)
        val pT = if (prereqs.any { kcStates[it] == KCState.NOT_LEARNED }) tp.low else tp.high

        val pObs = if (correct) {
            (pL * (1.0 - ps)) / (pL * (1.0 - ps) + (1.0 - pL) * pg)
        } else {
            (pL * ps) / (pL * ps + (1.0 - pL) * (1.0 - pg))
        }

        bktBelief[kcId] = pObs + (1.0 - pObs) * pT
    }

    fun pCorrect(kcId: Int): Double {
        val pL = bktBelief[kcId] ?: 0.0
        val pg = KnowledgeRepository.getGuessProb(kcId)
        val ps = KnowledgeRepository.getSlipProb(kcId)
        return pL * (1.0 - ps) + (1.0 - pL) * pg
    }

    fun getBktBelief(kcId: Int): Double = bktBelief[kcId] ?: 0.0

    fun status(): String = kcStates.entries.sortedBy { it.key }.joinToString("\n") { (id, state) ->
        "KC $id [${KnowledgeRepository.components[id]?.name}]: $state " +
                "(BKT belief: ${"%.2f".format(bktBelief[id] ?: 0.0)})"
    }
}