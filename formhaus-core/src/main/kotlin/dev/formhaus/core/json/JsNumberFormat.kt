package dev.formhaus.core.json

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991.0
private const val MAX_FIXED_EXPONENT = 21
private const val MAX_SIGNIFICANT_DIGITS = 17

internal fun jsNumberString(value: Double): String {
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return if (value < 0) "-Infinity" else "Infinity"
    if (value == 0.0) return "0"
    val sign = if (value < 0) "-" else ""
    val (digits, exponent) = shortestDigits(abs(value))
    val count = digits.length
    return sign +
        when {
            exponent in count..MAX_FIXED_EXPONENT -> {
                digits + "0".repeat(exponent - count)
            }

            exponent in 1..MAX_FIXED_EXPONENT -> {
                digits.substring(0, exponent) + "." + digits.substring(exponent)
            }

            exponent in -5..0 -> {
                "0." + "0".repeat(-exponent) + digits
            }

            else -> {
                val power = exponent - 1
                val mantissa = if (count == 1) digits else digits.substring(0, 1) + "." + digits.substring(1)
                mantissa + "e" + (if (power < 0) "-" else "+") + abs(power)
            }
        }
}

private fun shortestDigits(value: Double): Pair<String, Int> {
    if (value <= MAX_SAFE_INTEGER && value.isInteger) {
        val whole = value.toLong().toString()
        val digits = whole.trimEnd('0')
        return digits to whole.length
    }
    val exact = BigDecimal(value)
    for (precision in 1..MAX_SIGNIFICANT_DIGITS) {
        val rounded = exact.round(MathContext(precision, RoundingMode.HALF_EVEN))
        if (rounded.toDouble() == value) return decimalDigits(rounded)
    }
    return decimalDigits(exact.round(MathContext(MAX_SIGNIFICANT_DIGITS, RoundingMode.HALF_EVEN)))
}

private fun decimalDigits(decimal: BigDecimal): Pair<String, Int> {
    val stripped = decimal.stripTrailingZeros()
    val digits = stripped.unscaledValue().toString()
    return digits to digits.length - stripped.scale()
}
