package dev.formhaus.benchmarks

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

private const val CHECK_MASK = 0xFFF

private class CapReached : RuntimeException() {
    override fun fillInStackTrace(): Throwable = this
}

private class DeadlineText(
    private val text: String,
    private val deadline: Long,
) : CharSequence {
    private var reads = 0

    override val length: Int get() = text.length

    override fun get(index: Int): Char {
        reads += 1
        if (reads and CHECK_MASK == 0 && System.nanoTime() > deadline) throw CapReached()
        return text[index]
    }

    override fun subSequence(
        startIndex: Int,
        endIndex: Int,
    ): CharSequence = DeadlineText(text.substring(startIndex, endIndex), deadline)

    override fun toString(): String = text
}

class Sampling(
    val warmups: Int,
    val runs: Int,
    val capSeconds: Long,
)

class Measured(
    val nanos: Double?,
    val text: String,
) {
    constructor(nanos: Double) : this(nanos, formatNanos(nanos))

    constructor(text: String) : this(null, text)
}

fun javaPattern(pattern: String): String = pattern.replace("[^]", "[\\s\\S]").replace("[]", "[^\\s\\S]")

fun cappedJavaRegex(
    case: RegexCase,
    sampling: Sampling,
): Measured {
    val pattern =
        try {
            Pattern.compile(javaPattern(case.pattern))
        } catch (error: PatternSyntaxException) {
            return Measured("compile error: ${error.description.lowercase()}")
        }
    val capNanos = sampling.capSeconds * 1_000_000_000
    val samples = ArrayList<Double>()
    repeat(sampling.warmups + sampling.runs) { run ->
        val start = System.nanoTime()
        try {
            pattern.matcher(DeadlineText(case.long, start + capNanos)).find()
        } catch (_: CapReached) {
            return Measured("> ${sampling.capSeconds} s")
        } catch (_: StackOverflowError) {
            return Measured("stack overflow")
        }
        if (run >= sampling.warmups) samples.add((System.nanoTime() - start).toDouble())
    }
    return Measured(median(samples))
}
