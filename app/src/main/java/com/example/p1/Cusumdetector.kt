package com.example.p1

import kotlin.math.ln

// ─────────────────────────────────────────────────────────────────────────────
// CUSUMDetector
//
// Translated from Python KLUCB_Node2.cd(h).
//
// CUSUM accumulates log-likelihood ratio evidence:
//   correct answer → +ln((1-ps)/pg)   ≈ +1.56  for KC1 (pg=0.20, ps=0.05)
//   wrong   answer → +ln(ps/(1-pg))   ≈ -2.77  for KC1
//
// Threshold h = ln(1/fpr).
//   fpr = 0.00009  →  h ≈  9.32  (notebook value, needs ~6 consecutive correct)
//   fpr = 0.05     →  h ≈  3.0   (too easy, 2 lucky correct = mastery)
//
// We use fpr = 0.001 → h ≈ 6.9, requiring ~5 net-correct answers.
// This is intentionally more lenient than the Python notebook (which was
// designed for offline simulation, not a child-facing real-time app) while
// still being statistically meaningful.
//
// The CUSUM stat accumulates across ALL questions for this KC regardless of
// how many other KCs were shown in between — it is per-KC, not per-session.
// ─────────────────────────────────────────────────────────────────────────────

class CUSUMDetector(
    private val pg: Double,        // guess probability for this KC
    private val ps: Double,        // slip  probability for this KC
    private val threshold: Double  // h = ln(1/fpr)
) {

    private var cdEstimate: Double = 0.0
    private var index:      Int    = 0

    val correctnessRecord: MutableList<Boolean> = mutableListOf()

    /**
     * Direct translation of Python KLUCB_Node2.cd(h):
     *
     *   est = log((1-ps)/pg)  if correct else log(ps/(1-pg))
     *   if est > 0 and cdEstimate == 0: index = timesPlayed
     *   cdEstimate += est
     *   if cdEstimate < 0: cdEstimate = 0; index = 0
     *   return cdEstimate >= h
     */
    fun update(correct: Boolean): Boolean {
        correctnessRecord.add(correct)
        val timesPlayed = correctnessRecord.size

        val est = if (correct) ln((1.0 - ps) / pg)
        else         ln(ps / (1.0 - pg))

        if (est > 0.0 && cdEstimate == 0.0) {
            index = timesPlayed
        }

        cdEstimate += est

        if (cdEstimate < 0.0) {
            cdEstimate = 0.0
            index = 0
        }

        return cdEstimate >= threshold
    }

    fun getProgress(): Double = (cdEstimate / threshold).coerceIn(0.0, 1.0)
    fun getStatistic(): Double = cdEstimate

    fun reset() {
        cdEstimate = 0.0
        index = 0
        correctnessRecord.clear()
    }

    companion object {
        /** h = ln(1/fpr) — translated from Python beta = log(1/fpr) */
        fun thresholdFromFPR(fpr: Double): Double = ln(1.0 / fpr)
    }
}