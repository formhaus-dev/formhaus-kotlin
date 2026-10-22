package dev.formhaus.core.regex

internal class JsRegex private constructor(
    private val pattern: CompiledPattern?,
) {
    fun test(
        text: String,
        call: CallBudget? = CallBudget.current.value,
    ): Boolean? = pattern?.test(text, call)

    companion object {
        fun compile(source: String): JsRegex? = PatternCache.shared.pattern(source)?.let(::JsRegex)

        fun unchecked(source: String): JsRegex = JsRegex(PatternCache.shared.pattern(source))
    }
}
