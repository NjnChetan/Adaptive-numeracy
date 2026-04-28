package com.example.p1

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// KLUCBNode
//
// Translated from Python class `KLUCB_Node2`:
//   timesPlayed, correctnessSum, estimate, ucb, lcb, timeAdded
//   update_estimate(correctness)
//   compute_lcb_ucb(t)
//
// pg and ps are now sampled from Beta(a1=20, b1=160) / Beta(a2=20, b2=160)
// exactly as in the Python notebook:
//   self.pg = np.random.beta(a1, b1)
//   self.ps = np.random.beta(a2, b2)
// Mean of Beta(20,160) ≈ 0.111, matching the notebook's intent.
// ─────────────────────────────────────────────────────────────────────────────

class KLUCBNode(
    val timeAdded: Int,
    val pg: Double,   // sampled from Beta(20,160) — guess probability
    val ps: Double    // sampled from Beta(20,160) — slip  probability
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
// Beta distribution sampler (Johnk's method — no external dependency)
// Matches np.random.beta(a, b) for the shape parameters used (a=20, b=160).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sample from Beta(alpha, beta) using the Gamma-ratio method.
 * For integer-ish shape parameters this is accurate and fast.
 */
fun sampleBeta(alpha: Double, beta: Double): Double {
    val x = sampleGamma(alpha)
    val y = sampleGamma(beta)
    return x / (x + y)
}

/**
 * Sample from Gamma(shape, scale=1) using Marsaglia–Tsang's method.
 * Accurate for shape >= 1; for shape < 1, uses the transformation
 * Gamma(shape) = Gamma(shape+1) * U^(1/shape).
 */
private fun sampleGamma(shape: Double): Double {
    if (shape < 1.0) {
        val u = Random.nextDouble()
        return sampleGamma(1.0 + shape) * u.pow(1.0 / shape)
    }
    val d = shape - 1.0 / 3.0
    val c = 1.0 / kotlin.math.sqrt(9.0 * d)
    while (true) {
        var x: Double
        var v: Double
        do {
            x = Random.nextGaussian()
            v = 1.0 + c * x
        } while (v <= 0.0)
        v = v * v * v
        val u = Random.nextDouble()
        if (u < 1.0 - 0.0331 * (x * x) * (x * x)) return d * v
        if (ln(u) < 0.5 * x * x + d * (1.0 - v + ln(v))) return d * v
    }
}

private fun Random.nextGaussian(): Double {
    // Box-Muller
    val u1 = nextDouble()
    val u2 = nextDouble()
    return kotlin.math.sqrt(-2.0 * ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
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
    { q -> (p / q.pow(2)) + ((1.0 - p) / (1.0 - q).pow(2)) }

// ─────────────────────────────────────────────────────────────────────────────
// Newton–Raphson / Halley solver — now matches Python's retry loop exactly:
//
//   Python getUCB:
//     while True:
//       try:
//         res = optimize.newton(kl(est,value),
//                               fprime=klPrime(est), fprime2=klDPrime(est),
//                               x0=0.99 + abs(normal(0.0001, 0.01)))
//         break
//       except ValueError: pass
//
//   Python getLCB:
//     while True:
//       try:
//         res = optimize.newton(..., x0=0.01 - abs(normal(0.0001, 0.01)))
//         break
//       except ValueError: pass
//
// We reproduce the same random x0 perturbation and the same infinite-retry
// until a valid root is found. A binary-search fallback is kept only as a
// last resort after MAX_ATTEMPTS retries to avoid infinite loops on edge cases.
// ─────────────────────────────────────────────────────────────────────────────

private const val MAX_ATTEMPTS = 200

/**
 * One Halley step — equivalent to scipy.optimize.newton with fprime + fprime2.
 * Returns null if the step diverges or a division-by-zero occurs.
 */
private fun halleyRoot(
    f:   (Double) -> Double,
    fP:  (Double) -> Double,
    fPP: (Double) -> Double,
    x0:  Double,
    maxIter: Int = 120,
    tol: Double  = 1e-9
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
        val xNew = (x - fx / denom).coerceIn(1e-10, 1.0 - 1e-10)
        x = xNew
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
 * KL-UCB upper bound — mirrors Python getUCB exactly:
 *   while True:
 *     x0 = 0.99 + abs(normal(0.0001, 0.01))   ← random perturbation near 1
 *     try: res = newton(..., x0); break
 *     except ValueError: pass
 */
fun getUCB(value: Double, est: Double): Double {
    if (klDiv(est, 0.999) < value) return 1.0
    val f = klFunc(est, value); val fP = klPrime(est); val fPP = klDPrime(est)
    repeat(MAX_ATTEMPTS) {
        // x0 = 0.99 + abs(Normal(0.0001, 0.01))  — same distribution as Python
        val x0 = 0.99 + abs(Random.nextGaussian() * 0.01 + 0.0001)
        val res = halleyRoot(f, fP, fPP, x0) ?: return@repeat
        if (res > est && res < 1.0) return res
    }
    return klBinarySearch(est, value, searchHigh = true)
}

/**
 * KL-UCB lower bound — mirrors Python getLCB exactly:
 *   while True:
 *     x0 = 0.01 - abs(normal(0.0001, 0.01))   ← random perturbation near 0
 *     try: res = newton(..., x0); break
 *     except ValueError: pass
 */
fun getLCB(value: Double, est: Double): Double {
    if (klDiv(est, 0.001) < value) return 0.0
    val f = klFunc(est, value); val fP = klPrime(est); val fPP = klDPrime(est)
    repeat(MAX_ATTEMPTS) {
        // x0 = 0.01 - abs(Normal(0.0001, 0.01))  — same distribution as Python
        val x0 = 0.01 - abs(Random.nextGaussian() * 0.01 + 0.0001)
        val res = halleyRoot(f, fP, fPP, x0) ?: return@repeat
        if (res < est && res > 0.0) return res
    }
    return klBinarySearch(est, value, searchHigh = false)
}

// ─────────────────────────────────────────────────────────────────────────────
// KLUCBBandit
// ─────────────────────────────────────────────────────────────────────────────

class KLUCBBandit {

    private val nodes = mutableMapOf<Int, KLUCBNode>()

    /**
     * Add a new KC arm — pg and ps are now sampled from Beta(20,160),
     * matching the Python notebook's KLUCB_Node2(t, a1=20, b1=160, a2=20, b2=160).
     */
    fun addArm(kcId: Int, timeAdded: Int) {
        if (kcId !in nodes) {
            nodes[kcId] = KLUCBNode(
                timeAdded = timeAdded,
                pg        = sampleBeta(20.0, 160.0),
                ps        = sampleBeta(20.0, 160.0)
            )
        }
    }

    fun removeArm(kcId: Int) { nodes.remove(kcId) }

    /** Clear all arms — called when switching between addition and subtraction */
    fun clearAll() { nodes.clear() }

    fun hasArm(kcId: Int): Boolean = kcId in nodes
    fun activeArms(): Set<Int>     = nodes.keys.toSet()
    fun getNode(kcId: Int): KLUCBNode? = nodes[kcId]

    /**
     * Select the KC to present next.
     * Logic unchanged — translated from klucbCUSUM():
     *   1. Play any unplayed arm first
     *   2. Compute nt = total plays across all nodes
     *   3. Compute UCB for each node
     *   4. Return arm with highest UCB
     */
    fun selectConcept(zpd: List<Int>): Int {
        val active = zpd.filter { it in nodes }
        if (active.isEmpty()) {
            return if (zpd.isNotEmpty()) zpd.first() else 1
        }

        active.firstOrNull { nodes[it]!!.timesPlayed == 0 }
            ?.let { return it }

        val nt = nodes.values.sumOf { it.timesPlayed }.coerceAtLeast(2)

        active.forEach { kcId -> nodes[kcId]?.computeUCB(nt) }
        return active.maxByOrNull { nodes[it]?.ucb ?: Double.MIN_VALUE } ?: active.first()
    }

    fun update(kcId: Int, correct: Boolean) {
        nodes[kcId]?.updateEstimate(correct)
    }
}