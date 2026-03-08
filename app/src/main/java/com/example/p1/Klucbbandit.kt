package com.example.p1

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

// ─────────────────────────────────────────────────────────────────────────────
// KLUCBNode
//
// Translated from Python class `KLUCB_Node2`:
//   timesPlayed, correctnessSum, estimate, ucb, lcb, timeAdded
//   update_estimate(correctness)
//   compute_lcb_ucb(t)
//
// pg and ps come from KnowledgeRepository instead of Beta sampling so values
// stay consistent with the per-KC tables in the notebook.
// ─────────────────────────────────────────────────────────────────────────────

class KLUCBNode(
    val timeAdded: Int,
    val pg: Double,   // guess probability  (was Beta(a1,b1) in Python)
    val ps: Double    // slip  probability  (was Beta(a2,b2) in Python)
) {
    var timesPlayed:    Int    = 0
    var correctnessSum: Int    = 0
    var estimate:       Double = 0.0
    var ucb:            Double = 0.0
    var lcb:            Double = 0.0

    /** Translated from Python update_estimate(correctness) */
    fun updateEstimate(correctness: Boolean) {
        timesPlayed    += 1
        correctnessSum += if (correctness) 1 else 0
        estimate        = correctnessSum.toDouble() / timesPlayed
    }

    /**
     * Translated from Python compute_lcb_ucb(t):
     *   val = log(1 + t * log(t)^2) / timesPlayed
     *   ucb = getUCB(val, estimate)
     *   lcb = getLCB(val, estimate)
     */
    fun computeUCB(t: Int) {
        val tD   = t.toDouble()
        val val_ = ln(1.0 + tD * ln(tD).pow(2)) / timesPlayed
        ucb = getUCB(val_, estimate)
        lcb = getLCB(val_, estimate)
    }

    override fun toString(): String =
        "timesPlayed=$timesPlayed, estimate=${"%.3f".format(estimate)}, " +
                "ucb=${"%.3f".format(ucb)}, lcb=${"%.3f".format(lcb)}, " +
                "timeAdded=$timeAdded, pg=${"%.3f".format(pg)}, ps=${"%.3f".format(ps)}"
}

// ─────────────────────────────────────────────────────────────────────────────
// KL divergence helpers — translated from Python kl_div / kl / klPrime / klDPrime
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bernoulli KL divergence KL(x || y)
 * Translated from Python kl_div(x, y)
 */
fun klDiv(x: Double, y: Double): Double {
    if (x == y)   return 0.0
    if (x == 1.0) return x * ln(x / y)
    if (y == 1.0) return Double.POSITIVE_INFINITY
    if (x > 0.0 && y > 0.0) return x * ln(x / y) + (1.0 - x) * ln((1.0 - x) / (1.0 - y))
    if (x > 0.0)  return Double.POSITIVE_INFINITY
    return ln(1.0 / (1.0 - y))
}

/** f(q) = KL(p||q) - val — translated from Python kl(p, val) */
private fun klFunc(p: Double, value: Double): (Double) -> Double {
    return when {
        p == 1.0 -> { q: Double -> ln(1.0 / q) - value }
        p == 0.0 -> { q: Double -> ln(1.0 / (1.0 - q)) - value }
        else     -> {
            val pTerm = p * ln(p) + (1.0 - p) * ln(1.0 - p)
            val result: (Double) -> Double = { q -> pTerm - p * ln(q) - (1.0 - p) * ln(1.0 - q) - value }
            result
        }
    }
}

/** f'(q) — translated from Python klPrime(p) */
private fun klPrime(p: Double): (Double) -> Double =
    if (p == 0.0) { q -> 1.0 / (1.0 - q) }
    else          { q -> (-p / q) + ((1.0 - p) / (1.0 - q)) }

/** f''(q) — translated from Python klDPrime(p) */
private fun klDPrime(p: Double): (Double) -> Double =
    { q -> (-p / q.pow(2)) + ((1.0 - p) / (1.0 - q).pow(2)) }

/**
 * Newton–Raphson / Halley's method root finder.
 * Replaces scipy.optimize.newton (which uses fprime + fprime2 = Halley).
 */
private fun newtonRoot(
    f: (Double) -> Double,
    fP: (Double) -> Double,
    fPP: (Double) -> Double,
    x0: Double,
    maxIter: Int = 120,
    tol: Double = 1e-9
): Double? {
    var x = x0.coerceIn(1e-10, 1.0 - 1e-10)
    repeat(maxIter) {
        val fx   = f(x)
        if (abs(fx) < tol) return x
        val fpx  = fP(x)
        if (fpx == 0.0) return null
        val fppx = fPP(x)
        val denom = fpx - fx * fppx / (2.0 * fpx)
        if (denom == 0.0) return null
        x = (x - fx / denom).coerceIn(1e-10, 1.0 - 1e-10)
    }
    return if (abs(f(x)) < tol * 1000) x else null
}

/** Binary-search fallback for robustness */
private fun klBinarySearch(p: Double, budget: Double, searchHigh: Boolean): Double {
    val eps = 1e-10
    var lo = if (searchHigh) p else eps
    var hi = if (searchHigh) 1.0 - eps else p
    repeat(64) {
        val mid = (lo + hi) / 2.0
        if (klDiv(p, mid) <= budget) { if (searchHigh) lo = mid else hi = mid }
        else                         { if (searchHigh) hi = mid else lo = mid }
    }
    return if (searchHigh) lo else hi
}

/**
 * KL-UCB upper bound — translated from Python getUCB(value, est):
 *   if kl_div(est, 0.999) < value: return 1
 *   newton(kl(est,value), fprime=klPrime(est), fprime2=klDPrime(est), x0≈1)
 */
fun getUCB(value: Double, est: Double): Double {
    if (klDiv(est, 0.999) < value) return 1.0
    val f = klFunc(est, value);  val fP = klPrime(est);  val fPP = klDPrime(est)
    for (x0 in listOf(0.99, 0.95, 0.90, 0.80, 0.70)) {
        val res = newtonRoot(f, fP, fPP, x0) ?: continue
        if (res > est && res < 1.0) return res
    }
    return klBinarySearch(est, value, searchHigh = true)
}

/**
 * KL-UCB lower bound — translated from Python getLCB(value, est):
 *   if kl_div(est, 0.001) < value: return 0
 *   newton(kl(est,value), fprime=klPrime(est), fprime2=klDPrime(est), x0≈0)
 */
fun getLCB(value: Double, est: Double): Double {
    if (klDiv(est, 0.001) < value) return 0.0
    val f = klFunc(est, value);  val fP = klPrime(est);  val fPP = klDPrime(est)
    for (x0 in listOf(0.01, 0.05, 0.10, 0.20)) {
        val res = newtonRoot(f, fP, fPP, x0) ?: continue
        if (res < est && res > 0.0) return res
    }
    return klBinarySearch(est, value, searchHigh = false)
}

// ─────────────────────────────────────────────────────────────────────────────
// KLUCBBandit
//
// Manages the map of kcId → KLUCBNode, mirroring Python's ucb1_trace_node_dict.
// Selection logic translated directly from the klucbCUSUM() main loop.
// ─────────────────────────────────────────────────────────────────────────────

class KLUCBBandit {

    // Active (unmastered) KC nodes
    private val nodes = mutableMapOf<Int, KLUCBNode>()

    /** Add a new KC arm when it first enters the ZPD */
    fun addArm(kcId: Int, timeAdded: Int) {
        if (kcId !in nodes) {
            nodes[kcId] = KLUCBNode(
                timeAdded = timeAdded,
                pg        = KnowledgeRepository.getGuessProb(kcId),
                ps        = KnowledgeRepository.getSlipProb(kcId)
            )
        }
    }

    /** Remove arm once mastery is declared (mirrors del ucb1_trace_node_dict[chosen_trace]) */
    fun removeArm(kcId: Int) { nodes.remove(kcId) }

    fun hasArm(kcId: Int): Boolean = kcId in nodes
    fun activeArms(): Set<Int>     = nodes.keys.toSet()
    fun getNode(kcId: Int): KLUCBNode? = nodes[kcId]

    /**
     * Select the KC to present next.
     *
     * Translated from klucbCUSUM():
     *   1. Play any unplayed arm (timesPlayed == 0) first
     *   2. Compute nt = total plays across all nodes
     *   3. Compute UCB for each node via compute_lcb_ucb(nt)
     *   4. Return arm with highest UCB
     */
    fun selectConcept(zpd: List<Int>): Int {
        // Only consider arms that are both in ZPD and have a node
        val active = zpd.filter { it in nodes }
        if (active.isEmpty()) return zpd.first()

        // Step 1 — bootstrap unplayed arms
        active.firstOrNull { nodes[it]!!.timesPlayed == 0 }
            ?.let { return it }

        // Step 2 — nt = total plays
        val nt = nodes.values.sumOf { it.timesPlayed }.coerceAtLeast(2)

        // Step 3+4 — compute UCBs, pick max
        active.forEach { kcId -> nodes[kcId]?.computeUCB(nt) }
        return active.maxByOrNull { nodes[it]?.ucb ?: Double.MIN_VALUE } ?: active.first()
    }

    /** Record answer — mirrors chosen_node.update_estimate(correctness) */
    fun update(kcId: Int, correct: Boolean) {
        nodes[kcId]?.updateEstimate(correct)
    }
}