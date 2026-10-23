package dev.formhaus.core.cldr

import java.util.Locale

internal class CldrTable<T : Any>(
    encoded: String,
    fallback: String,
    parse: (String) -> T,
) {
    private val fallback = parse(fallback)
    private val values = HashMap<String, T>()

    init {
        for (line in encoded.lineSequence()) {
            val value = parse(line.substringAfter('|'))
            for (tag in line.substringBefore('|').split(' ')) values[tag] = value
        }
    }

    operator fun get(locale: Locale): T = keys(locale).firstNotNullOfOrNull(values::get) ?: fallback

    private fun keys(locale: Locale): List<String> {
        val language = locale.language
        val script = locale.script.takeIf { it.isNotEmpty() }
        val region = locale.country.takeIf { it.isNotEmpty() }
        return listOfNotNull(
            if (script != null && region != null) "$language-$script-$region" else null,
            script?.let { "$language-$it" },
            region?.let { "$language-$it" },
            language,
        )
    }
}

internal val numberingSystems by lazy { CldrTable(NUMBERING_SYSTEMS, FALLBACK_NUMBERING_SYSTEMS) { it } }
internal val calendars by lazy { CldrTable(CALENDARS, FALLBACK_CALENDARS) { it } }
internal val likelyRegions by lazy { CldrTable(LIKELY_REGIONS, FALLBACK_LIKELY_REGIONS) { it } }

internal fun Locale.withDefault(
    key: String,
    type: String,
): Locale =
    if (getUnicodeLocaleType(key) != null) {
        this
    } else {
        Locale
            .Builder()
            .setLocale(this)
            .setUnicodeLocaleKeyword(key, type)
            .build()
    }
