package com.example.p1

class StudentModel {

    // 0 = not mastered
    // 1 = mastered
    val kcStates: MutableMap<Int, Int> = mutableMapOf<Int, Int>().apply {
        for (i in 1..14) {
            put(i, 0)
        }
    }

    fun isMastered(kcId: Int): Boolean {
        return kcStates[kcId] == 1
    }

    fun setMastered(kcId: Int) {
        kcStates[kcId] = 1
    }
}