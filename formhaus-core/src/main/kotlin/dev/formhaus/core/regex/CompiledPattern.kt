package dev.formhaus.core.regex

import dev.formhaus.core.validation.MAX_STRING_LENGTH

internal class CompiledPattern(
    source: String,
) {
    val program: RegexProgram
    val prefilter: Prefilter
    val plan: SearchPlan
    val automaton: AutomatonCache?
    val scanners: List<AutomatonCache?>
    val oversized: Boolean
    private val unanchored: Lazy<AutomatonCache>?

    init {
        val parsed = RegexParser.parse(source)
        val analysis = PatternAnalysis(parsed)
        prefilter = Prefilter(parsed.root)
        plan = SearchPlan.of(parsed.root, analysis.automaton, prefilter.anchored)
        program = RegexCompiler.compile(parsed, analysis, plan.backward)
        oversized = program.size > RegexCompiler.INSTRUCTION_LIMIT
        if (oversized) {
            automaton = null
            scanners = emptyList()
            unanchored = null
        } else {
            val pool = MemoryPool.current.value
            automaton = if (program.automaton) AutomatonCache(main(pool)) else null
            unanchored =
                if (plan.backward) {
                    lazy {
                        AutomatonCache(
                            AutomatonPlan(program, false, backward = true, pool = pool),
                        )
                    }
                } else {
                    null
                }
            scanners =
                program.scans.zip(program.looks) { scan, body ->
                    scan?.let {
                        AutomatonCache(
                            AutomatonPlan(it, false, backward = !body.look.behind, scanning = true, pool = pool),
                        )
                    }
                }
        }
    }

    private fun main(pool: MemoryPool): AutomatonPlan =
        if (plan.backward) {
            AutomatonPlan(program, true, backward = true, pool = pool)
        } else {
            AutomatonPlan(program, prefilter.anchored, prefilter.first, pool = pool)
        }

    val bytes: Int get() = program.size * INSTRUCTION_BYTES

    fun release() {
        automaton?.release()
        scanners.forEach { it?.release() }
        unanchored?.takeIf { it.isInitialized() }?.value?.release()
    }

    fun test(text: String): Boolean? {
        val units = text.toCharArray()
        if (!prefilter.admits(units)) return false
        if (oversized || (units.size > MAX_STRING_LENGTH && program.clamped)) return null
        val call = CallBudget.current.value
        val allowance = call?.remaining ?: Int.MAX_VALUE
        if (allowance <= 0) return null
        val context = MatchContext(units, scanners, allowance)
        try {
            return match(units, context)
        } finally {
            call?.charge(context.spent)
        }
    }

    private fun match(
        units: CharArray,
        context: MatchContext,
    ): Boolean? {
        val automaton =
            automaton ?: run {
                context.limit(StepBudget.backtrackingLimit.value)
                return budgeted { Backtracker(program, context).search(prefilter) }
            }
        context.limit(16 * (units.size + 64))
        if (program.size >= PROBE_SIZE) probe(units, context)?.let { return it }
        context.limit(StepBudget.linear(units.size, program.size))
        if (!plan.backward) return budgeted { automaton.matches(units, context) }
        return budgeted { reverse(automaton, units, context) }
    }

    private fun probe(
        units: CharArray,
        context: MatchContext,
    ): Boolean? =
        budgeted {
            val probe = Backtracker(program, context)
            if (!plan.backward) return@budgeted probe.search(prefilter)
            var end = plan.end(units, null)
            while (end != null) {
                if (probe.run(0, end)) return@budgeted true
                end = plan.end(units, end)
            }
            false
        }

    private fun reverse(
        automaton: AutomatonCache,
        units: CharArray,
        context: MatchContext,
    ): Boolean {
        var floor = 0
        var end = plan.end(units, null)
        while (end != null) {
            when (automaton.reverse(units, end, floor, context)) {
                Verdict.MATCH -> {
                    return true
                }

                Verdict.MISS -> {
                    floor = end
                }

                Verdict.UNDECIDED -> {
                    val fallback = unanchored ?: throw BudgetExceeded
                    return fallback.value.reverse(units, units.size, 0, context) == Verdict.MATCH
                }
            }
            end = plan.end(units, end)
        }
        return false
    }

    private companion object {
        const val PROBE_SIZE = 2048
        const val INSTRUCTION_BYTES = 36
    }
}

private inline fun <Value> budgeted(body: () -> Value): Value? =
    try {
        body()
    } catch (_: BudgetExceeded) {
        null
    }
