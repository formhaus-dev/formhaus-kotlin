package dev.formhaus.core.i18n

import dev.formhaus.core.cldr.languageAliases
import dev.formhaus.core.cldr.likelyRegions
import java.util.IllformedLocaleException
import java.util.Locale

private fun canonicalTag(tag: String): String? {
    val subtags = tag.replace('_', '-').split('-')
    val cut = subtags.withIndex().indexOfFirst { (index, subtag) -> index > 0 && subtag.length == 1 }
    val head = if (cut < 0) subtags else subtags.subList(0, cut)
    if (head.first().length < 2) return null
    val locale =
        try {
            Locale.Builder().setLanguageTag(head.joinToString("-")).build()
        } catch (_: IllformedLocaleException) {
            return null
        }
    return if (locale.hasExtensions()) null else dealiased(locale).toLanguageTag()
}

private fun dealiased(locale: Locale): Locale {
    val alias = languageAliases[locale.language].takeIf { it.isNotEmpty() } ?: return locale
    val replacement = Locale.forLanguageTag(alias)
    return Locale
        .Builder()
        .setLocale(locale)
        .setLanguage(replacement.language)
        .setScript(locale.script.ifEmpty { replacement.script })
        .setRegion(locale.country.ifEmpty { replacement.country })
        .build()
}

private fun likelyTag(tag: String): String? {
    val locale = Locale.forLanguageTag(tag)
    if (locale.country.isNotEmpty()) return "${locale.language}-${locale.country}"
    return likelyRegions[locale].takeIf { it.isNotEmpty() }
}

public fun localeChain(tag: String): List<String> {
    val chain = ArrayList<String>()
    canonicalTag(tag)?.let { canonical ->
        val parts = canonical.split('-')
        for (size in parts.size downTo 1) chain.add(parts.subList(0, size).joinToString("-"))
        likelyTag(canonical)?.let { chain.add(maxOf(chain.size - 1, 1), it) }
    }
    return (chain.filter { it != "en" } + "en").distinct()
}
