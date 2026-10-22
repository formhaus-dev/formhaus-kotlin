package dev.formhaus.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

internal val LocalMeasuredFrames: ProvidableCompositionLocal<MeasuredFrames?> = staticCompositionLocalOf { null }

@Composable
internal fun Modifier.measured(name: String): Modifier {
    val log = LocalMeasuredFrames.current ?: return this
    return onGloballyPositioned { log.frames[name] = it.boundsInRoot() }
}

internal class MeasuredFrames {
    val frames: MutableMap<String, Rect> = mutableMapOf()
}
