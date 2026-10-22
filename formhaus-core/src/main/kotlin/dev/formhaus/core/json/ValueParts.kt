package dev.formhaus.core.json

internal typealias ValueParts = List<Pair<String, Any?>>

internal inline fun <reified T : Any> T.equalParts(
    other: Any?,
    parts: T.() -> ValueParts,
): Boolean = this === other || (other is T && parts() == other.parts())

internal fun ValueParts.partsHash(): Int = map { it.second }.hashCode()

internal fun ValueParts.describe(name: String): String = joinToString(prefix = "$name(", postfix = ")") { (key, value) -> "$key=$value" }
