package dev.formhaus.core.json

import dev.formhaus.core.renderer.RendererApi

@RendererApi
public typealias ValueParts = List<Pair<String, Any?>>

@RendererApi
public inline fun <reified T : Any> T.equalParts(
    other: Any?,
    parts: T.() -> ValueParts,
): Boolean = this === other || (other is T && parts() == other.parts())

@RendererApi
public fun ValueParts.partsHash(): Int = map { it.second }.hashCode()

@RendererApi
public fun ValueParts.describe(name: String): String = joinToString(prefix = "$name(", postfix = ")") { (key, value) -> "$key=$value" }
