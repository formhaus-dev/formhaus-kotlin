package dev.formhaus.core.validation

import dev.formhaus.core.model.scaleTypes
import dev.formhaus.core.regex.JsRegex

internal val booleanTypes = listOf("checkbox", "switch")
internal val builtInTypes =
    listOf(
        "text",
        "email",
        "phone",
        "number",
        "password",
        "select",
        "autocomplete",
        "multiselect",
        "checkbox",
        "radio",
        "switch",
        "file",
        "date",
        "datetime",
        "time",
        "textarea",
        "hidden",
        "scale",
        "rating",
    )
internal val numberTypes = listOf("number") + scaleTypes
internal val listTypes = listOf("multiselect")
internal val randomizedTypes = listOf("radio", "multiselect", "select")
internal val textTypes = setOf("text", "phone", "password", "textarea", "autocomplete", "hidden")

private const val MAX_EMAIL_LENGTH = 254
private const val DATE = "\\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\\d|3[01])"
private const val HOURS = "(?:[01]\\d|2[0-3]):[0-5]\\d"

private val emailPattern = JsRegex.unchecked("^[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+$")
private val dateOnly = JsRegex.unchecked("^$DATE$")
private val dateTime = JsRegex.unchecked("^${DATE}T$HOURS(?::[0-5]\\d(?:\\.\\d{1,3})?)?(?:Z|[+-]$HOURS)?$")
private val timePattern = JsRegex.unchecked("^$HOURS(?::[0-5]\\d)?$")

private val monthDays = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

private fun isCalendarDate(value: String): Boolean {
    val year = value.substring(0, 4).toInt()
    val month = value.substring(5, 7).toInt()
    val day = value.substring(8, 10).toInt()
    val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    return day <= if (month == 2 && leap) 29 else monthDays[month - 1]
}

internal enum class ValueFormat(
    val key: MessageKey,
    val max: Int,
) {
    EMAIL(MessageKey.EMAIL, MAX_EMAIL_LENGTH),
    DATE(MessageKey.DATE, MAX_STRING_LENGTH),
    DATETIME(MessageKey.DATETIME, MAX_STRING_LENGTH),
    TIME(MessageKey.TIME, MAX_STRING_LENGTH),
    ;

    fun matches(value: String): Boolean =
        when (this) {
            EMAIL -> emailPattern.test(value) == true
            DATE -> dateOnly.test(value) == true && isCalendarDate(value)
            DATETIME -> dateTime.test(value) == true && isCalendarDate(value)
            TIME -> timePattern.test(value) == true
        }

    companion object {
        fun of(type: String): ValueFormat? = entries.firstOrNull { it.key.rawValue == type }
    }
}

internal fun matchesFormat(
    type: String,
    value: String,
): Boolean = ValueFormat.of(type)?.matches(value) == true
