package com.example.p1

// ─────────────────────────────────────────────────────────────────────────────
// CUSUMDetector (now: Sliding Window mastery detector)
//
// Mastery requires:
//   - At least minAttempts responses recorded
//   - 4 of last 5 correct, OR last 4 all correct (streak shortcut)
//
// Class name kept as CUSUMDetector to avoid touching call sites in
// AdaptiveEngine.kt.
// ─────────────────────────────────────────────────────────────────────────────

class CUSUMDetector(
    private val pg: Double = 0.0,
    private val ps: Double = 0.0,
    private val threshold: Double = 0.0,
    private val minAttempts: Int = 5
) {

    val correctnessRecord: MutableList<Boolean> = mutableListOf()

    fun update(correct: Boolean): Boolean {
        correctnessRecord.add(correct)
        val n = correctnessRecord.size

        if (n < minAttempts) return false

        val last5Met = correctnessRecord.takeLast(5).count { it } >= 4
        val streakMet = n >= 4 && correctnessRecord.takeLast(4).all { it }

        return last5Met || streakMet
    }

    fun getProgress(): Double {
        if (correctnessRecord.isEmpty()) return 0.0
        val window = correctnessRecord.takeLast(5)
        return (window.count { it }.toDouble() / 5.0).coerceIn(0.0, 1.0)
    }

    fun getStatistic(): Double = correctnessRecord.takeLast(5).count { it }.toDouble()

    fun reset() {
        correctnessRecord.clear()
    }

    companion object {
        fun thresholdFromFPR(fpr: Double): Double = 0.0
    }
}