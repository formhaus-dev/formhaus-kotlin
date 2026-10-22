package dev.formhaus.core.i18n

import dev.formhaus.core.model.FormDefinition

@ConsistentCopyVisibility
public data class TextSlot internal constructor(
    public val address: String,
    public val key: String?,
    public val text: String,
    public val params: List<String>,
    public val hash: String,
)

public sealed interface TranslationPrefix {
    public data object DefinitionId : TranslationPrefix

    public data class Custom(public val text: String) : TranslationPrefix

    public data object None : TranslationPrefix
}

internal fun translationKey(prefix: String?, explicit: String?, address: List<String>): String? {
    if (explicit != null) return explicit
    if (prefix == null) return null
    return (if (prefix.isEmpty()) address else listOf(prefix) + address).joinToString(".")
}

private const val FNV_OFFSET = 0x811c9dc5.toInt()
private const val FNV_PRIME = 0x01000193

internal fun fnv1a(text: String): Int {
    var hash = FNV_OFFSET
    text.codePoints().forEach { point -> hash = (hash xor point) * FNV_PRIME }
    return hash
}

private fun textHash(key: String?, text: String): String = Integer.toHexString(fnv1a("${key.orEmpty()}\u0000$text")).padStart(8, '0')

private fun describeSlot(slot: Slot, prefix: String?): TextSlot {
    val key = translationKey(prefix, slot.key, slot.address)
    val text = slot.source ?: slot.fallback
    val params = if (slot.kind == SlotKind.RULE) slot.params.keys else placeholders(text)
    return TextSlot(slot.address.joinToString("."), key, text, params, textHash(key, text))
}

public fun textSlots(definition: FormDefinition, prefix: TranslationPrefix = TranslationPrefix.DefinitionId): List<TextSlot> {
    val resolved = when (prefix) {
        TranslationPrefix.DefinitionId -> definition.id
        is TranslationPrefix.Custom -> prefix.text
        TranslationPrefix.None -> null
    }
    return definitionSlots(definition).filter { it.template }.map { describeSlot(it, resolved) }
}
