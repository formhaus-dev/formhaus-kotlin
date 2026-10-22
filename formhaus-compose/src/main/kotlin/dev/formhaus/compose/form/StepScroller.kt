package dev.formhaus.compose.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import dev.formhaus.compose.model.FocusRequest
import dev.formhaus.compose.model.FormModel
import dev.formhaus.core.renderer.nodeId

internal data class ScrollTrigger(
    val step: Int?,
    val focus: FocusRequest?,
) {
    constructor(model: FormModel) : this(model.structure.currentStep?.nodeId, model.focusRequest)
}

internal sealed interface ScrollTarget {
    data object Summary : ScrollTarget

    data class Field(
        val key: String,
    ) : ScrollTarget

    data object FormTop : ScrollTarget

    data object ScreenTop : ScrollTarget

    companion object {
        fun focus(
            request: FocusRequest,
            summary: Boolean,
        ): ScrollTarget? {
            val key = request.key ?: return null
            return if (request.summary && summary) Summary else Field(key)
        }

        fun resolve(
            old: ScrollTrigger,
            new: ScrollTrigger,
            mode: StepScroll,
            summary: Boolean = true,
        ): ScrollTarget? {
            val focus = new.focus
            if (focus != old.focus && focus != null) focus(focus, summary)?.let { return it }
            if (new.step == old.step) return null
            return when (mode) {
                StepScroll.SCREEN_TOP -> ScreenTop
                StepScroll.FORM_TOP -> FormTop
            }
        }
    }
}

private class LastTrigger(
    var value: ScrollTrigger,
)

@Composable
internal fun StepScroller(
    model: FormModel,
    anchors: ScrollAnchors,
) {
    val behavior = LocalFormBehavior.current
    val top = LocalTopRequests.current
    val trigger = ScrollTrigger(model)
    val last = remember(model) { LastTrigger(trigger) }
    LaunchedEffect(trigger) {
        val old = last.value
        last.value = trigger
        val target = ScrollTarget.resolve(old, trigger, behavior.stepScroll, behavior.errorSummary) ?: return@LaunchedEffect
        if (target == ScrollTarget.ScreenTop && top != null) {
            top.request()
            return@LaunchedEffect
        }
        withFrameNanos {}
        anchors.scroll(target)
    }
}
