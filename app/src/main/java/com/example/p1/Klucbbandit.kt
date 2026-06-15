package com.example.p1

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// KLUCBNode
// ─────────────────────────────────────────────────────────────────────────────

class KLUCBNode(val timeAdded: Int) {
    var timesPlayed:    Int    = 0
    var correctnessSum: Int    = 0
    var estimate:       Double = 0.0
    var ucb:            Double = 0.0
    var lcb:            Double = 0.0

    fun updateEstimate(correctness: Boolean) {
        timesPlayed    += 1
        correctnessSum += if (correctness) 1 else 0
        estimate        = correctnessSum.toDouble() / timesPlayed
    }

    fun computeUCB(ts: Int) {
        val t    = (ts - timeAdded).coerceAtLeast(2)
        val tD   = t.toDouble()
        val val_ = ln(1.0 + tD * ln(tD).pow(2)) / timesPlayed
        ucb = getUCB(val_, estimate)
        lcb = getLCB(val_, estimate)
    }

    override fun toString(): String =
        "timesPlayed=$timesPlayed, estimate=${"%.3f".format(estimate)}, " +
                "ucb=${"%.3f".format(ucb)}, lcb=${"%.3f".format(lcb)}, " +
                "timeAdded=$timeAdded"
}

// ─────────────────────────────────────────────────────────────────────────────
// KL divergence helpers
// ─────────────────────────────────────────────────────────────────────────────

fun klDiv(x: Double, y: Double): Double {
    if (x == y)   return 0.0
    if (x == 1.0) return x * ln(x / y)
    if (y == 1.0) return Double.POSITIVE_INFINITY
    if (x > 0.0 && y > 0.0) return x * ln(x / y) + (1.0 - x) * ln((1.0 - x) / (1.0 - y))
    if (x > 0.0)  return Double.POSITIVE_INFINITY
    return ln(1.0 / (1.0 - y))
}

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

private fun klPrime(p: Double): (Double) -> Double =
    if (p == 0.0) { q -> 1.0 / (1.0 - q) }
    else          { q -> (-p / q) + ((1.0 - p) / (1.0 - q)) }

private fun klDPrime(p: Double): (Double) -> Double =
    { q -> (p / q.pow(2)) + ((1.0 - p) / (1.0 - q).pow(2)) }

private const val MAX_ATTEMPTS = 200

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

fun getUCB(value: Double, est: Double): Double {
    if (klDiv(est, 0.999) < value) return 1.0
    val f = klFunc(est, value); val fP = klPrime(est); val fPP = klDPrime(est)
    repeat(MAX_ATTEMPTS) {
        val x0 = 0.99 + abs(Random.nextGaussian() * 0.01 + 0.0001)
        val res = halleyRoot(f, fP, fPP, x0) ?: return@repeat
        if (res > est && res < 1.0) return res
    }
    return klBinarySearch(est, value, searchHigh = true)
}

fun getLCB(value: Double, est: Double): Double {
    if (klDiv(est, 0.001) < value) return 0.0
    val f = klFunc(est, value); val fP = klPrime(est); val fPP = klDPrime(est)
    repeat(MAX_ATTEMPTS) {
        val x0 = 0.01 - abs(Random.nextGaussian() * 0.01 + 0.0001)
        val res = halleyRoot(f, fP, fPP, x0) ?: return@repeat
        if (res < est && res > 0.0) return res
    }
    return klBinarySearch(est, value, searchHigh = false)
}

private fun Random.nextGaussian(): Double {
    val u1 = nextDouble()
    val u2 = nextDouble()
    return kotlin.math.sqrt(-2.0 * ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
}

// ─────────────────────────────────────────────────────────────────────────────
// KLUCBBandit
// ─────────────────────────────────────────────────────────────────────────────

class KLUCBBandit {

    private val nodes = mutableMapOf<Int, KLUCBNode>()

    fun addArm(kcId: Int, timeAdded: Int) {
        if (kcId !in nodes) {
            nodes[kcId] = KLUCBNode(timeAdded = timeAdded)
        }
    }

    fun removeArm(kcId: Int) { nodes.remove(kcId) }

    fun clearAll() { nodes.clear() }

    fun hasArm(kcId: Int): Boolean = kcId in nodes
    fun activeArms(): Set<Int>     = nodes.keys.toSet()
    fun getNode(kcId: Int): KLUCBNode? = nodes[kcId]

    fun selectConcept(zpd: List<Int>, ts: Int): Int {
        val active = zpd.filter { it in nodes }
        if (active.isEmpty()) {
            return if (zpd.isNotEmpty()) zpd.first() else 1
        }

        // Always play an arm that has never been played yet
        active.firstOrNull { nodes[it]!!.timesPlayed == 0 }
            ?.let { return it }

        // Compute UCB for all active arms
        active.forEach { kcId -> nodes[kcId]?.computeUCB(ts) }

        // Log UCB values
        active.forEach { kcId ->
            val node = nodes[kcId]
            if (node != null) {
                android.util.Log.i(
                    "KLUCBBandit",
                    "KC $kcId | timesPlayed=${node.timesPlayed} " +
                            "est=${"%.3f".format(node.estimate)} " +
                            "ucb=${"%.3f".format(node.ucb)} " +
                            "lcb=${"%.3f".format(node.lcb)}"
                )
            }
        }

        val selected = active.maxByOrNull { nodes[it]?.ucb ?: Double.MIN_VALUE } ?: active.first()
        android.util.Log.i("KLUCBBandit", "→ Selected KC $selected")
        return selected
    }

    fun update(kcId: Int, correct: Boolean) {
        nodes[kcId]?.updateEstimate(correct)
    }
}