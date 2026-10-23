package dev.formhaus.core.engine

import dev.formhaus.core.json.FieldErrors
import dev.formhaus.core.support.engine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

object League {
    const val DEFINITION = """{"formatVersion":"1.1","id":"league","title":"League","submit":{"label":"Send"},"steps":[
        {"id":"account","title":"Account","fields":[
          {"key":"email","type":"email","label":"Email","validation":{"required":true}},
          {"key":"plan","type":"text","label":"Plan"},
          {"key":"company","type":"text","label":"Company","show":[{"field":"plan","eq":"team"}]}]},
        {"id":"roster","title":"Roster","skip":{"label":"Skip"},"fields":[
          {"key":"players","type":"group","label":"Players","fields":[
            {"key":"name","type":"text","label":"Name"},
            {"key":"role","type":"text","label":"Role","show":[{"field":"name","notEmpty":true}]}]}]},
        {"id":"notes","title":"Notes","fields":[{"key":"notes","type":"textarea","label":"Notes"}]}]}"""

    const val VALUES = """{"email":"ada@example.com","plan":"solo","players":[{"name":"Ada","role":"Lead"},{"name":""}],"notes":"Hi"}"""

    fun engine(
        options: FormEngineOptions,
        values: String = VALUES,
    ): FormEngine = engine(DEFINITION, values, options)

    suspend fun onNotes(options: FormEngineOptions): FormEngine =
        engine(options).also {
            it.next()
            it.next()
        }

    fun once(
        result: FieldErrors,
        whenSubmit: (Boolean) -> Boolean = { true },
    ): ValidateFn {
        var used = false
        return { _, context ->
            if (used || !whenSubmit(context.submit)) {
                null
            } else {
                used = true
                result
            }
        }
    }
}

fun <T> CoroutineScope.started(block: suspend CoroutineScope.() -> T): Deferred<T> =
    async(start = CoroutineStart.UNDISPATCHED, block = block)

fun pending(): CompletableDeferred<FieldErrors?> = CompletableDeferred()
