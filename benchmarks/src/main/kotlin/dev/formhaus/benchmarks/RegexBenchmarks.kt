package dev.formhaus.benchmarks

import dev.formhaus.core.model.FormField
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.Warmup
import java.util.regex.Pattern

const val COMPILE_VARIANTS = 300

fun compileVariant(
    pattern: String,
    index: Int,
): String = "$pattern(?:z$index){0}"

abstract class TypicalRegexState : JmhDefaults() {
    @Param("email", "phone", "postcode", "iban", "password", "quoted")
    lateinit var case: String
    protected lateinit var item: RegexCase
    private var next = 0

    protected abstract fun prepare(variants: List<String>)

    @Setup
    fun setUp() {
        item = RegexCases.typical(case)
        prepare(List(COMPILE_VARIANTS) { compileVariant(item.pattern, it) })
    }

    protected fun nextIndex(): Int {
        next = (next + 1) % COMPILE_VARIANTS
        return next
    }
}

open class FormhausRegexBenchmark : TypicalRegexState() {
    private lateinit var variants: List<FormField>
    private lateinit var cached: FormField

    override fun prepare(variants: List<String>) {
        this.variants = variants.map(::patternField)
        cached = patternField(item.pattern)
    }

    @Benchmark
    fun compile(): String? = validatePattern(variants[nextIndex()], item.short)

    @Benchmark
    fun matchShort(): String? = validatePattern(cached, item.short)

    @Benchmark
    fun matchLong(): String? = validatePattern(cached, item.long)
}

open class JavaRegexBenchmark : TypicalRegexState() {
    private lateinit var variants: List<String>
    private lateinit var cached: Pattern

    override fun prepare(variants: List<String>) {
        this.variants = variants
        cached = Pattern.compile(item.pattern)
    }

    @Benchmark
    fun compile(): Boolean = Pattern.compile(variants[nextIndex()]).matcher(item.short).find()

    @Benchmark
    fun matchShort(): Boolean = cached.matcher(item.short).find()

    @Benchmark
    fun matchLong(): Boolean = cached.matcher(item.long).find()
}

@Warmup(iterations = 3, time = 1)
open class PathologicalRegexBenchmark : JmhDefaults() {
    @Param("nested-plus", "alternation", "double-plus", "captures", "lookarounds", "dfa-states", "counted-repeat", "dfa-reset")
    lateinit var case: String
    private lateinit var item: RegexCase
    private lateinit var cached: FormField

    @Setup
    fun setUp() {
        item = RegexCases.pathological(case)
        cached = patternField(item.pattern)
    }

    @Benchmark
    fun match(): String? = validatePattern(cached, item.long)
}
