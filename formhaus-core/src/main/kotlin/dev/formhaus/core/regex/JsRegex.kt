package dev.formhaus.core.regex

internal class JsRegex private constructor(private val pattern: CompiledPattern?) {
    fun test(text: String): Boolean? = pattern?.test(text)

    companion object {
        fun compile(source: String): JsRegex? = PatternCache.shared.pattern(source)?.let(::JsRegex)

        fun unchecked(source: String): JsRegex = JsRegex(PatternCache.shared.pattern(source))
    }
}
