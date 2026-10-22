package dev.formhaus.core.regex

import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

internal object BudgetExceeded : Exception(null, null, false, false)

internal class ScopedLocal<T>(
    initial: T,
) {
    private val local = ThreadLocal.withInitial { initial }

    val value: T get() = local.get()

    fun <R> withValue(
        value: T,
        body: () -> R,
    ): R {
        val saved = local.get()
        local.set(value)
        try {
            return body()
        } finally {
            local.set(saved)
        }
    }

    fun contextElement(value: T): ThreadContextElement<T> = local.asContextElement(value)
}

internal class StepBudget(
    limit: Int,
) {
    var remaining: Int = limit
        private set

    fun spend(steps: Int) {
        remaining -= steps
        if (remaining < 0) throw BudgetExceeded
    }

    companion object {
        val backtrackingLimit: ScopedLocal<Int> = ScopedLocal(10_000_000)

        fun linear(
            units: Int,
            instructions: Int,
        ): Int = minOf(8L * (units + 2) * (instructions + 2), backtrackingLimit.value.toLong()).toInt()
    }
}

internal class CallBudget(
    limit: Int = LIMIT,
) {
    private val left = AtomicLong(limit.toLong())
    private val patternTests = AtomicInteger(patternTestLimit.value)

    val remaining: Int get() = left.get().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

    fun charge(steps: Int) {
        left.addAndGet(-steps.toLong())
    }

    fun admitsPatternTest(): Boolean = patternTests.decrementAndGet() >= 0

    companion object {
        const val LIMIT: Int = 20_000_000
        val patternTestLimit: ScopedLocal<Int> = ScopedLocal(10_000)
        val current: ScopedLocal<CallBudget?> = ScopedLocal(null)

        fun <Value> run(body: () -> Value): Value =
            if (current.value ==
                null
            ) {
                current.withValue(CallBudget(), body)
            } else {
                body()
            }

        suspend fun <Value> runSuspending(body: suspend () -> Value): Value {
            if (current.value != null) return body()
            var finished: Result<Value>? = null
            return try {
                withContext(current.contextElement(CallBudget())) { body().also { finished = Result.success(it) } }
            } catch (error: CancellationException) {
                (finished ?: throw error).getOrThrow()
            }
        }
    }
}
