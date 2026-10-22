package dev.formhaus.compose.form

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect

internal const val FORM_TOP_ANCHOR = "formhaus.top"
internal const val SUMMARY_ANCHOR = "formhaus.summary"
private const val SCREEN_REACH = 100_000f
private const val REVEAL_FRAMES = 3

internal fun interface AnchorRevealer {
    suspend fun reveal(key: String): Boolean
}

internal class ScrollAnchors {
    private val requesters = mutableMapOf<String, BringIntoViewRequester>()
    var revealer: AnchorRevealer? = null

    fun register(
        key: String,
        requester: BringIntoViewRequester,
    ) {
        requesters[key] = requester
    }

    fun unregister(
        key: String,
        requester: BringIntoViewRequester,
    ) {
        if (requesters[key] === requester) requesters.remove(key)
    }

    suspend fun scroll(target: ScrollTarget) {
        when (target) {
            ScrollTarget.Summary -> requester(SUMMARY_ANCHOR)?.bringIntoView()
            is ScrollTarget.Field -> requester(target.key)?.bringIntoView()
            ScrollTarget.FormTop -> requester(FORM_TOP_ANCHOR)?.bringIntoView(Rect(0f, 0f, 1f, 1f))
            ScrollTarget.ScreenTop -> requester(FORM_TOP_ANCHOR)?.bringIntoView(Rect(0f, -SCREEN_REACH, 1f, 1 - SCREEN_REACH))
        }
    }

    private suspend fun requester(key: String): BringIntoViewRequester? {
        requesters[key]?.let { return it }
        if (revealer?.reveal(key) != true) return null
        repeat(REVEAL_FRAMES) {
            withFrameNanos {}
            requesters[key]?.let { return it }
        }
        return null
    }
}

internal class TopRequests {
    var issued: Int by mutableIntStateOf(0)
        private set

    fun request() {
        issued += 1
    }
}

internal val LocalScrollAnchors: ProvidableCompositionLocal<ScrollAnchors?> = staticCompositionLocalOf { null }

internal val LocalTopRequests: ProvidableCompositionLocal<TopRequests?> = staticCompositionLocalOf { null }

@Composable
public fun Modifier.formhausScrollAnchor(context: FieldContext): Modifier = formhausScrollAnchor(context.key)

@Composable
internal fun Modifier.formhausScrollAnchor(key: String): Modifier {
    val anchors = LocalScrollAnchors.current ?: return this
    val requester = remember { BringIntoViewRequester() }
    DisposableEffect(anchors, key) {
        anchors.register(key, requester)
        onDispose { anchors.unregister(key, requester) }
    }
    return bringIntoViewRequester(requester)
}
