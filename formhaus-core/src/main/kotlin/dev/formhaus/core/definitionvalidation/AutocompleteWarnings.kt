package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.json.jsCollapsed
import dev.formhaus.core.json.jsTrimmed
import dev.formhaus.core.json.jsonStringify
import dev.formhaus.core.validation.builtInTypes

private val autocompleteTypes =
    listOf("text", "email", "phone", "password", "textarea", "number", "date", "select", "autocomplete", "radio")
private val contactTypes = setOf("home", "work", "mobile", "fax", "pager")
private val contactNames =
    setOf(
        "tel",
        "tel-country-code",
        "tel-national",
        "tel-area-code",
        "tel-local",
        "tel-local-prefix",
        "tel-local-suffix",
        "tel-extension",
        "email",
        "impp",
    )
private val fieldNames =
    setOf(
        "name",
        "honorific-prefix",
        "given-name",
        "additional-name",
        "family-name",
        "honorific-suffix",
        "nickname",
        "username",
        "new-password",
        "current-password",
        "one-time-code",
        "organization-title",
        "organization",
        "street-address",
        "address-line1",
        "address-line2",
        "address-line3",
        "address-level1",
        "address-level2",
        "address-level3",
        "address-level4",
        "country",
        "country-name",
        "postal-code",
        "cc-name",
        "cc-given-name",
        "cc-additional-name",
        "cc-family-name",
        "cc-number",
        "cc-exp",
        "cc-exp-month",
        "cc-exp-year",
        "cc-csc",
        "cc-type",
        "transaction-currency",
        "transaction-amount",
        "language",
        "bday",
        "bday-day",
        "bday-month",
        "bday-year",
        "sex",
        "url",
        "photo",
    )
private const val SECTION_PREFIX = "section-"

private fun isSectionName(word: String): Boolean =
    word.length > SECTION_PREFIX.length && word.startsWith(SECTION_PREFIX) &&
        word.substring(SECTION_PREFIX.length).all { it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-' }

private fun isAutofillToken(value: String): Boolean {
    val words =
        ArrayDeque(
            value.jsTrimmed
                .lowercase()
                .jsCollapsed
                .split(' '),
        )
    if (words.size == 1 && (words.first() == "on" || words.first() == "off")) return true
    if (words.firstOrNull()?.let(::isSectionName) == true) words.removeAt(0)
    if (words.firstOrNull() == "shipping" || words.firstOrNull() == "billing") words.removeAt(0)
    if (words.size > 1 && words.last() == "webauthn") words.removeAt(words.lastIndex)
    return when (words.size) {
        1 -> words[0] in fieldNames || words[0] in contactNames
        2 -> words[0] in contactTypes && words[1] in contactNames
        else -> false
    }
}

internal fun autocompleteWarnings(entry: FieldEntry): List<DefinitionIssue> {
    val field = entry.field
    val autocomplete = field.autocomplete ?: return emptyList()
    val path = "${entry.path}/autocomplete"
    if (field.type in builtInTypes && field.type !in autocompleteTypes) {
        val hint = "Use it on ${autocompleteTypes.joinToString(", ")} fields, or remove it"
        return listOf(
            warning(
                IssueCode.AUTOCOMPLETE_NOT_APPLICABLE,
                path,
                "\"autocomplete\" does not apply to ${field.type} fields",
                hint,
            ),
        )
    }
    if (autocomplete.stringValue?.let(::isAutofillToken) == true) return emptyList()
    val hint = "Use a token such as \"given-name\", \"email\" or \"street-address\", or \"off\""
    return listOf(
        warning(
            IssueCode.UNKNOWN_AUTOCOMPLETE_TOKEN,
            path,
            "${jsonStringify(autocomplete)} is not an HTML autofill token",
            hint,
        ),
    )
}
