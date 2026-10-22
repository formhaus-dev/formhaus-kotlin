package dev.formhaus.compose.fields

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import dev.formhaus.core.model.FormField

internal enum class InputKind {
    TEXT,
    EMAIL,
    PHONE,
    NUMBER,
    DECIMAL,
    URL,
    SEARCH,
    PASSWORD,
    ;

    val isLiteral: Boolean get() = this == EMAIL || this == URL || this == PASSWORD || this == PHONE

    val disablesCorrection: Boolean get() = isLiteral || this == NUMBER || this == DECIMAL

    val keyboardType: KeyboardType
        get() =
            when (this) {
                EMAIL -> KeyboardType.Email
                PHONE -> KeyboardType.Phone
                NUMBER -> KeyboardType.Number
                DECIMAL -> KeyboardType.Decimal
                URL -> KeyboardType.Uri
                PASSWORD -> KeyboardType.Password
                TEXT, SEARCH -> KeyboardType.Text
            }

    val contentType: ContentType?
        get() =
            when (this) {
                EMAIL -> ContentType.EmailAddress
                PHONE -> ContentType.PhoneNumber
                PASSWORD -> ContentType.Password
                else -> null
            }

    fun keyboardOptions(lastStep: Boolean): KeyboardOptions =
        KeyboardOptions(
            capitalization = if (isLiteral) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            autoCorrectEnabled = !disablesCorrection,
            keyboardType = keyboardType,
            imeAction =
                when {
                    this == SEARCH -> ImeAction.Search
                    lastStep -> ImeAction.Done
                    else -> ImeAction.Next
                },
        )

    companion object {
        fun of(field: FormField): InputKind =
            when (field.type) {
                "email" -> EMAIL
                "phone" -> PHONE
                "password" -> PASSWORD
                "number" -> if (field.inputMode?.stringValue == "numeric") NUMBER else DECIMAL
                else -> mode(field.inputMode?.stringValue)
            }

        private fun mode(mode: String?): InputKind =
            when (mode) {
                "email" -> EMAIL
                "tel" -> PHONE
                "numeric" -> NUMBER
                "decimal" -> DECIMAL
                "url" -> URL
                "search" -> SEARCH
                else -> TEXT
            }
    }
}
