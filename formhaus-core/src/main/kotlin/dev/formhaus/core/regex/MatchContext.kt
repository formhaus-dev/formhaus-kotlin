package dev.formhaus.core.regex

internal class MatchContext(val text: CharArray, private val scanners: List<AutomatonCache?>, private val allowance: Int) {
    var spent: Int = 0
        private set
    private var budget = StepBudget(0)
    private val tables = arrayOfNulls<BooleanArray>(scanners.size)

    fun limit(steps: Int) {
        budget = StepBudget(minOf(steps, allowance - spent))
    }

    fun spend(steps: Int) {
        spent += steps
        budget.spend(steps)
    }

    fun holds(index: Int, position: Int): Boolean {
        tables[index]?.let { return it[position] }
        val scanner = checkNotNull(scanners[index]) { "look $index runs inline" }
        val table = scanner.acceptance(text, this)
        tables[index] = table
        return table[position]
    }
}
