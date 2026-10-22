package dev.formhaus.core.regex

internal class MatchContext(
    val text: String,
    private val scanners: List<AutomatonCache?>,
    private val allowance: Int,
) {
    var spent: Int = 0
        private set
    private var remaining = 0
    private var tables: Array<BooleanArray?>? = null

    fun limit(steps: Int) {
        remaining = minOf(steps, allowance - spent)
    }

    fun spend(steps: Int) {
        spent += steps
        remaining -= steps
        if (remaining < 0) throw BudgetExceeded
    }

    fun holds(
        index: Int,
        position: Int,
    ): Boolean {
        val known = tables ?: arrayOfNulls<BooleanArray>(scanners.size).also { tables = it }
        known[index]?.let { return it[position] }
        val scanner = checkNotNull(scanners[index]) { "look $index runs inline" }
        val table = scanner.acceptance(text, this)
        known[index] = table
        return table[position]
    }
}
