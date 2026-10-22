package dev.formhaus.compose.containers

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

internal enum class ActionsLayout { STACKED, ROW }

internal val LocalActionsLayout: ProvidableCompositionLocal<ActionsLayout?> = staticCompositionLocalOf { null }
