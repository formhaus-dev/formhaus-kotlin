package dev.formhaus.core.regex

internal class PatternAnalysis(parsed: ParsedPattern) {
    class LookInfo(val body: RegexNode, val look: Look, val pure: Boolean)

    val captureCount: Int = parsed.captureCount
    val automaton: Boolean
    val looks: List<LookInfo>

    init {
        val referenced = HashSet<Int>()
        val bodies = ArrayList<RegexNode.Lookaround>()
        parsed.root.contains { node ->
            if (node is RegexNode.Backreference) referenced.addAll(node.groups)
            if (node is RegexNode.Lookaround) bodies.add(node)
            false
        }
        automaton = referenced.isEmpty()
        looks = bodies.map { node ->
            val captures = node.body.captureRange
            val impure = node.body.contains { it is RegexNode.Backreference }
            LookInfo(node.body, node.look, pure = !impure && referenced.none { captures?.contains(it) == true })
        }
    }
}

internal class RegexCompiler private constructor(val analysis: PatternAnalysis, val automaton: Boolean) {
    val code: ArrayList<Instruction> = ArrayList()
    var registers: Int = analysis.captureCount + 1
    var dependentEnd: Int = 0
    var inMain: Boolean = true
    var clamped: Boolean = false
    private val sets = ArrayList<CharSet>()
    private val setIndex = HashMap<CharSet, Int>()
    private val references = ArrayList<Backreference>()
    private val inline = arrayOfNulls<Int>(analysis.looks.size)
    private val pending = ArrayList<Int>()
    private val queued = HashSet<Int>()

    val oversized: Boolean get() = code.size > INSTRUCTION_LIMIT

    companion object {
        const val INSTRUCTION_LIMIT: Int = 1 shl 18

        fun compile(parsed: ParsedPattern, analysis: PatternAnalysis, backward: Boolean): RegexProgram {
            val compiler = RegexCompiler(analysis, analysis.automaton)
            compiler.emit(parsed.root, backward)
            val mainEnd = compiler.code.size
            compiler.code.add(Instruction.Match)
            compiler.inMain = false
            compiler.emitInlineLooks()
            var size = compiler.code.size
            var clamped = compiler.clamped
            val scans = analysis.looks.map { info ->
                if (!info.pure || size > INSTRUCTION_LIMIT) return@map null
                val scan = scanner(info, analysis)
                size += scan.size
                clamped = clamped || scan.clamped
                scan
            }
            compiler.clamped = clamped
            return compiler.program(mainEnd, scans, size)
        }

        private fun scanner(info: PatternAnalysis.LookInfo, analysis: PatternAnalysis): RegexProgram {
            val compiler = RegexCompiler(analysis, automaton = true)
            compiler.inMain = false
            compiler.emit(info.body, !info.look.behind)
            val end = compiler.code.size
            compiler.code.add(Instruction.Match)
            return compiler.program(end, emptyList(), compiler.code.size)
        }
    }

    private fun program(mainEnd: Int, scans: List<RegexProgram?>, size: Int): RegexProgram = RegexProgram(
        instructions = code, sets = sets, looks = analysis.looks.map { LookBody(it.look, inline[it.look.index]) },
        scans = scans, size = size, clamped = clamped, references = references, captureCount = analysis.captureCount,
        registerCount = registers, mainEnd = mainEnd, memoFrom = dependentEnd, automaton = automaton,
    )

    fun newRegister(): Int = registers++

    fun emit(node: RegexNode, backward: Boolean) {
        when (node) {
            is RegexNode.Unit -> code.add(Instruction.Unit(node.unit, backward))
            is RegexNode.Set -> code.add(Instruction.Set(intern(node.set), backward))
            is RegexNode.Sequence -> for (child in if (backward) node.nodes.asReversed() else node.nodes) emit(child, backward)
            is RegexNode.Alternation -> emitAlternation(node.branches, backward)
            is RegexNode.Group -> {
                val recorded = if (automaton) null else node.capture
                if (recorded != null) code.add(Instruction.State(StateChange.GroupStart(recorded)))
                emit(node.body, backward)
                if (recorded != null) code.add(Instruction.State(StateChange.GroupEnd(recorded)))
            }
            is RegexNode.Quantified -> emitQuantified(node.body, node.quantifier, backward)
            is RegexNode.Assertion -> code.add(Instruction.Assertion(node.assertion))
            is RegexNode.Lookaround -> emitLook(node.look)
            is RegexNode.Backreference -> {
                references.add(Backreference(node.groups, backward, node.ignoreCase))
                code.add(Instruction.Backreference(references.size - 1))
                markDependent()
            }
        }
    }

    fun markDependent() {
        if (inMain) dependentEnd = code.size
    }

    fun intern(set: CharSet): Int {
        if (sets.isNotEmpty() && sets.last() == set) return sets.size - 1
        return setIndex.getOrPut(set) {
            sets.add(set)
            sets.size - 1
        }
    }

    private fun emitAlternation(branches: List<RegexNode>, backward: Boolean) {
        val exits = ArrayList<Int>()
        for (branch in branches.dropLast(1)) {
            val split = code.size
            code.add(Instruction.Succeed)
            emit(branch, backward)
            exits.add(code.size)
            code.add(Instruction.Succeed)
            code[split] = Instruction.Split(split + 1, code.size)
        }
        branches.lastOrNull()?.let { emit(it, backward) }
        for (exit in exits) code[exit] = Instruction.Jump(code.size)
    }

    private fun emitLook(look: Look) {
        code.add(Instruction.Look(look.index))
        if (analysis.looks[look.index].pure) return
        if (queued.add(look.index)) pending.add(look.index)
        markDependent()
    }

    private fun emitInlineLooks() {
        var next = 0
        while (next < pending.size) {
            val info = analysis.looks[pending[next]]
            inline[info.look.index] = code.size
            emit(info.body, info.look.behind)
            code.add(Instruction.Succeed)
            next++
        }
    }
}
