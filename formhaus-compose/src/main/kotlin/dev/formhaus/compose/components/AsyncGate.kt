package dev.formhaus.compose.components

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal class AsyncGate(
    width: Int,
    private val dispatcher: CoroutineDispatcher,
) {
    private val permits = Semaphore(width)

    suspend fun <T> run(body: suspend () -> T): T = permits.withPermit { withContext(dispatcher) { body() } }
}
