package dev.formhaus.core.cldr

import dev.formhaus.core.json.jsNumberString
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs

private const val FRACTION_DIGITS = 3
private val hundred = BigInteger.valueOf(100)
private val million = BigInteger.valueOf(1_000_000)

private class Operands(
    value: Double,
) {
    val decimal: BigDecimal =
        BigDecimal(jsNumberString(abs(value)))
            .setScale(FRACTION_DIGITS, RoundingMode.HALF_UP)
            .stripTrailingZeros()
    val integer: BigInteger = decimal.toBigInteger()
    val whole: Boolean = decimal.scale() <= 0
    val isOneInteger: Boolean get() = whole && integer == BigInteger.ONE
    val isMillions: Boolean get() = whole && integer.signum() != 0 && mod(million) == 0

    fun mod(divisor: BigInteger): Int = integer.mod(divisor).toInt()
}

private fun romance(
    one: Boolean,
    operands: Operands,
): String =
    when {
        one -> "one"
        operands.isMillions -> "many"
        else -> "other"
    }

private fun eastSlavic(operands: Operands): String {
    if (!operands.whole) return "other"
    val last = operands.mod(BigInteger.TEN)
    val lastTwo = operands.mod(hundred)
    return when {
        last == 1 && lastTwo != 11 -> "one"
        last in 2..4 && lastTwo !in 12..14 -> "few"
        else -> "many"
    }
}

internal fun pluralCategory(
    locale: Locale,
    value: Double,
): String {
    val operands = Operands(value)
    return when (locale.language) {
        "en", "de" -> if (operands.isOneInteger) "one" else "other"
        "it" -> romance(operands.isOneInteger, operands)
        "es" -> romance(operands.decimal.compareTo(BigDecimal.ONE) == 0, operands)
        "pt" -> romance(if (locale.country == "PT") operands.isOneInteger else operands.integer <= BigInteger.ONE, operands)
        "fr" -> romance(operands.integer <= BigInteger.ONE, operands)
        "ru", "uk" -> eastSlavic(operands)
        else -> "other"
    }
}
