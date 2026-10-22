package dev.formhaus.benchmarks

import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale

private val units = listOf(1e10 to (1e9 to "s"), 1e6 to (1e6 to "ms"), 100.0 to (1e3 to "µs"))

fun median(samples: List<Double>): Double {
    val sorted = samples.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}

fun formatNanos(nanos: Double): String {
    val (scale, unit) = units.firstOrNull { nanos >= it.first }?.second ?: (1.0 to "ns")
    return "${formatNumber(nanos / scale)} $unit"
}

fun formatNumber(value: Double): String =
    when {
        value < 1 -> String.format(Locale.US, "%.2f", value)
        value < 10 -> String.format(Locale.US, "%.1f", value)
        else -> String.format(Locale.US, "%,d", Math.round(value))
    }

fun formatRatio(
    baseline: Double,
    measured: Double,
): String {
    val ratio = baseline / measured
    val rounded = { value: Double -> formatNumber(BigDecimal(value).round(MathContext(2)).toDouble()) }
    return if (ratio >= 1) "×${rounded(ratio)} faster" else "×${rounded(1 / ratio)} slower"
}
