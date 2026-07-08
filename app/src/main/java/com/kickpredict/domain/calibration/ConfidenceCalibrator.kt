package com.kickpredict.domain.calibration

import kotlin.math.roundToInt

/** One past prediction and whether its top pick turned out correct — the training signal. */
data class PredictionRecord(
    val confidence: Int,
    val wasCorrect: Boolean,
)

/** Maps a raw engine confidence to one calibrated against observed hit rate. */
fun interface ConfidenceCalibration {
    fun calibrate(rawConfidence: Int): Int
}

/** No-op calibration used until a real reliability curve has been fitted. */
object IdentityConfidenceCalibration : ConfidenceCalibration {
    override fun calibrate(rawConfidence: Int): Int = rawConfidence
}

/**
 * Piecewise-linear reliability curve over confidence-bin centres (monotone non-decreasing).
 * Produced by [ConfidenceCalibrator.fit]; interpolates a raw confidence to the empirical hit rate.
 */
class IsotonicConfidenceCalibration(
    private val points: List<Point>,
) : ConfidenceCalibration {

    data class Point(val center: Double, val hitRate: Double)

    override fun calibrate(rawConfidence: Int): Int {
        if (points.isEmpty()) return rawConfidence
        val c = rawConfidence.toDouble()
        if (c <= points.first().center) return (points.first().hitRate * 100).roundToInt()
        if (c >= points.last().center) return (points.last().hitRate * 100).roundToInt()
        for (i in 0 until points.size - 1) {
            val (x0, y0) = points[i]
            val (x1, y1) = points[i + 1]
            if (c in x0..x1) {
                val t = (c - x0) / (x1 - x0)
                return ((y0 + t * (y1 - y0)) * 100).roundToInt()
            }
        }
        return rawConfidence
    }
}

/**
 * Fits a reliability calibration from historical predictions using isotonic regression
 * (pool-adjacent-violators) over confidence bins: predictions are bucketed by confidence, each
 * bucket's empirical hit rate is measured, then buckets are pooled to enforce monotonicity. A model
 * that is systematically over-confident (says 90% but only right 60% of the time) gets pulled down.
 */
object ConfidenceCalibrator {

    fun fit(records: List<PredictionRecord>, bins: Int = 10): ConfidenceCalibration {
        if (records.isEmpty()) return IdentityConfidenceCalibration

        val buckets = Array(bins) { mutableListOf<Boolean>() }
        records.forEach { r ->
            val idx = ((r.confidence / 100.0) * bins).toInt().coerceIn(0, bins - 1)
            buckets[idx].add(r.wasCorrect)
        }

        // (center, hit-rate, weight) for each non-empty bucket.
        data class Bin(val center: Double, val hitRate: Double, val weight: Int)
        val pts = ArrayList<Bin>()
        buckets.forEachIndexed { i, b ->
            if (b.isNotEmpty()) {
                pts.add(Bin((i + 0.5) / bins * 100.0, b.count { it }.toDouble() / b.size, b.size))
            }
        }

        // Pool adjacent violators until hit rate is non-decreasing.
        var i = 0
        while (i < pts.size - 1) {
            if (pts[i].hitRate > pts[i + 1].hitRate) {
                val w = pts[i].weight + pts[i + 1].weight
                val pooled = (pts[i].hitRate * pts[i].weight + pts[i + 1].hitRate * pts[i + 1].weight) / w
                pts[i] = Bin(pts[i].center, pooled, w)
                pts.removeAt(i + 1)
                if (i > 0) i--
            } else {
                i++
            }
        }

        return IsotonicConfidenceCalibration(
            pts.map { IsotonicConfidenceCalibration.Point(it.center, it.hitRate) },
        )
    }
}
