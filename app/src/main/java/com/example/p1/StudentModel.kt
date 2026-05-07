package com.example.p1

import kotlin.random.Random

enum class KCState { NOT_LEARNED, LEARNED }

class StudentModel {

    val kcStates: MutableMap<Int, KCState> = mutableMapOf<Int, KCState>().apply {
        for (id in 1..17) put(id, KCState.NOT_LEARNED)
    }

    val bktBelief: MutableMap<Int, Double> = mutableMapOf<Int, Double>().apply {
        for (id in 1..17) put(id, 0.0)
    }

    fun isMastered(kcId: Int): Boolean = kcStates[kcId] == KCState.LEARNED

    fun setMastered(kcId: Int) {
        kcStates[kcId] = KCState.LEARNED
        bktBelief[kcId] = 1.0
    }

    fun reset() {
        for (id in 1..17) {
            kcStates[id] = KCState.NOT_LEARNED
            bktBelief[id] = 0.0
        }
    }

    fun bktUpdateBelief(kcId: Int, correct: Boolean) {
        if (kcStates[kcId] == KCState.LEARNED) return

        val tp      = KnowledgeRepository.getTransition(kcId)
        val prereqs = KnowledgeRepository.getPrerequisites(kcId)
        val pT      = if (prereqs.any { kcStates[it] == KCState.NOT_LEARNED }) tp.low else tp.high

        val prior = bktBelief[kcId] ?: 0.0
        bktBelief[kcId] = prior + pT * (1.0 - prior)
    }

    fun getBktBelief(kcId: Int): Double = bktBelief[kcId] ?: 0.0

    fun status(): String = kcStates.entries.joinToString("\n") { (id, state) ->
        "KC $id [${KnowledgeRepository.components[id]?.name}]: $state"
    }
}