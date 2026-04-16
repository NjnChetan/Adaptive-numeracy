package com.example.p1

import kotlin.random.Random

enum class KCState { NOT_LEARNED, LEARNED }

class StudentModel {

    // Official mastery state
    val kcStates: MutableMap<Int, KCState> = mutableMapOf<Int, KCState>().apply {
        for (id in 1..10) put(id, KCState.NOT_LEARNED)
    }

    fun isMastered(kcId: Int): Boolean = kcStates[kcId] == KCState.LEARNED

    /** Called ONLY by AdaptiveEngine.onMastery() after CUSUM fires */
    fun setMastered(kcId: Int) {
        kcStates[kcId] = KCState.LEARNED
    }

    /**
     * BKT state update — now matches Python Student._update_state() exactly:
     *
     *   def _update_state(self, kc, pre_reqs):
     *       if any(self.kc_states[x] == NOT_LEARNED for x in pre_reqs):
     *           p_t = self.pt[kc]["low"]
     *       else:
     *           p_t = self.pt[kc]["high"]
     *       if random() < p_t:
     *           self.kc_states[kc] = LEARNED
     *
     * This is a stochastic flip: each answer attempt independently rolls
     * against the transition probability. The Python model does NOT use a
     * continuous belief — it flips the hidden state directly.
     *
     * Note: in the Python, _update_state is only called when the KC is
     * NOT yet learned (matching "if self.kc_states[kc] != KC_STATE.LEARNED").
     * We preserve that guard here.
     */
    fun bktUpdateBelief(kcId: Int, correct: Boolean) {
        // Only attempt transition if not yet learned (matches Python guard)
        if (kcStates[kcId] == KCState.LEARNED) return

        val tp      = KnowledgeRepository.getTransition(kcId)
        val prereqs = KnowledgeRepository.getPrerequisites(kcId)

        // Use low transition if any prereq is unmastered, high if all mastered
        val pT = if (prereqs.any { kcStates[it] == KCState.NOT_LEARNED }) tp.low else tp.high

        // Stochastic flip — matches Python: if random() < p_t: state = LEARNED
        if (Random.nextDouble() < pT) {
            kcStates[kcId] = KCState.LEARNED
        }
    }

    fun status(): String = kcStates.entries.joinToString("\n") { (id, state) ->
        "KC $id [${KnowledgeRepository.components[id]?.name}]: $state"
    }
}