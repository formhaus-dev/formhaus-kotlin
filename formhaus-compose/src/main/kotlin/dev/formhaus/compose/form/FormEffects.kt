package dev.formhaus.compose.form

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.model.FormModel
import java.util.Locale

@Composable
internal fun FormLocale(
    lang: String?,
    content: @Composable () -> Unit,
) {
    if (lang.isNullOrEmpty()) return content()
    val base = LocalConfiguration.current
    val configuration = remember(base, lang) { Configuration(base).apply { setLocale(Locale.forLanguageTag(lang)) } }
    CompositionLocalProvider(LocalConfiguration provides configuration, content = content)
}

@Composable
internal fun InvalidFeedback(model: FormModel) {
    val haptics = LocalHapticFeedback.current
    val attempts = model.invalidAttempts
    LaunchedEffect(attempts) { if (attempts > 0) haptics.performHapticFeedback(HapticFeedbackType.Reject) }
}

@Composable
internal fun Announcer(model: FormModel) {
    val announcement = model.alerts.announcement
    var spoken by remember { mutableStateOf("") }
    LaunchedEffect(announcement?.id) {
        spoken = ""
        withFrameNanos { }
        spoken = announcement?.text.orEmpty()
    }
    Box(
        Modifier.size(1.dp).semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = spoken
        },
    )
}
