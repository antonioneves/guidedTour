package com.guidedtour.app.data

import kotlin.math.min

/** Edit distance between [expected] and [actual] divided by the expected length; whitespace is normalised. */
fun characterErrorRate(expected: String, actual: String): Double {
    val a = expected.trim().replace(Regex("\\s+"), " ")
    val b = actual.trim().replace(Regex("\\s+"), " ")
    if (a.isEmpty()) return if (b.isEmpty()) 0.0 else 1.0
    var previous = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val current = IntArray(b.length + 1)
        current[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost)
        }
        previous = current
    }
    return previous[b.length].toDouble() / a.length
}

/** Variance of the Laplacian of a grayscale image: higher means sharper. [luma] holds 0..255 values row by row. */
fun laplacianVariance(luma: IntArray, width: Int, height: Int): Double {
    if (width < 3 || height < 3) return 0.0
    var sum = 0.0
    var sumSquares = 0.0
    var n = 0
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val i = y * width + x
            val v = 4 * luma[i] - luma[i - 1] - luma[i + 1] - luma[i - width] - luma[i + width]
            sum += v
            sumSquares += v.toDouble() * v
            n++
        }
    }
    val mean = sum / n
    return sumSquares / n - mean * mean
}
