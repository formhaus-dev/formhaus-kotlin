package dev.formhaus.core.cldr

import dev.formhaus.core.json.jsNumberString
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

private const val CACHE_LIMIT = 32

private data class Grouping(
    val primary: Int,
    val secondary: Int,
    val minimum: Int,
)

private fun grouping(encoded: String): Grouping {
    val (primary, secondary, minimum) = encoded.split(',').map(String::toInt)
    return Grouping(primary, secondary, minimum)
}

private val groupings by lazy { CldrTable(NUMBER_GROUPINGS, FALLBACK_NUMBER_GROUPINGS, ::grouping) }

private fun decimalFormat(locale: Locale): DecimalFormat =
    NumberFormat.getNumberInstance(locale) as? DecimalFormat ?: DecimalFormat("#,##0.###", DecimalFormatSymbols(locale))

private class NumberStyle(
    locale: Locale,
) {
    private val grouping = groupings[locale]
    private val format = decimalFormat(locale.withDefault("nu", numberingSystems[locale]))
    private val symbols = format.decimalFormatSymbols

    fun format(
        value: BigDecimal,
        negative: Boolean,
    ): String {
        val plain = value.toPlainString()
        val integer = plain.substringBefore('.')
        val fraction = plain.substringAfter('.', "")
        val output = StringBuilder(if (negative) format.negativePrefix else format.positivePrefix)
        appendGrouped(output, integer)
        if (fraction.isNotEmpty()) {
            output.append(symbols.decimalSeparator)
            fraction.forEach { output.append(localDigit(it)) }
        }
        return output.append(if (negative) format.negativeSuffix else format.positiveSuffix).toString()
    }

    private fun appendGrouped(
        output: StringBuilder,
        integer: String,
    ) {
        val grouped = integer.length >= grouping.primary + grouping.minimum
        for ((index, digit) in integer.withIndex()) {
            if (grouped && index > 0 && isGroupStart(integer.length - index)) output.append(symbols.groupingSeparator)
            output.append(localDigit(digit))
        }
    }

    private fun isGroupStart(remaining: Int): Boolean =
        remaining == grouping.primary ||
            (remaining > grouping.primary && (remaining - grouping.primary) % grouping.secondary == 0)

    private fun localDigit(digit: Char): Char = symbols.zeroDigit + (digit - '0')
}

private val styles = BoundedCache<Locale, NumberStyle>(CACHE_LIMIT)

internal fun cldrNumber(
    value: Double,
    maximumSignificantDigits: Int,
    locale: Locale,
): String {
    val number = value + 0.0
    val rounded =
        BigDecimal(jsNumberString(number))
            .abs()
            .round(MathContext(maximumSignificantDigits, RoundingMode.HALF_UP))
            .stripTrailingZeros()
    return styles.getOrPut(locale, ::NumberStyle).format(rounded, number < 0 && rounded.signum() != 0)
}
