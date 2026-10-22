package dev.formhaus.core.engine

import dev.formhaus.core.validation.TemplateToken
import dev.formhaus.core.validation.forEachToken

internal val recallSlots = listOf("title", "description", "label", "placeholder", "helperText", "itemLabel")

private data class RawToken(val raw: String, val token: TemplateToken)

private fun tokens(text: String): List<RawToken> {
    val found = ArrayList<RawToken>()
    forEachToken(text) { start, end, token ->
        found.add(RawToken(text.substring(start, end), token))
        true
    }
    return found
}

internal fun templateTokens(text: String): List<String> = tokens(text).mapNotNull { (raw, token) ->
    when (token) {
        TemplateToken.Escape -> null
        is TemplateToken.Param -> token.name
        is TemplateToken.Recall -> token.key?.let { "{{$it}}" } ?: raw
    }
}

internal fun recallKeys(text: String): List<String> = tokens(text).mapNotNull { (it.token as? TemplateToken.Recall)?.key }

internal fun reservedTokens(text: String): List<String> =
    tokens(text).mapNotNull { if (it.token is TemplateToken.Recall && it.token.key == null) it.raw else null }
