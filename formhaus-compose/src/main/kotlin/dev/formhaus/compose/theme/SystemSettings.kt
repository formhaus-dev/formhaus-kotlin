package dev.formhaus.compose.theme

import android.app.UiModeManager
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduce by remember(context) { mutableStateOf(context.animationsOff()) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    reduce = context.animationsOff()
                }
            }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduce
}

@Composable
internal fun rememberIncreasedContrast(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
    val context = LocalContext.current
    val modes = remember(context) { context.getSystemService(UiModeManager::class.java) } ?: return false
    var increased by remember(modes) { mutableStateOf(modes.increasedContrast()) }
    DisposableEffect(modes) {
        val listener = UiModeManager.ContrastChangeListener { increased = modes.increasedContrast() }
        modes.addContrastChangeListener(context.mainExecutor, listener)
        onDispose { modes.removeContrastChangeListener(listener) }
    }
    return increased
}

private fun Context.animationsOff(): Boolean = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private fun UiModeManager.increasedContrast(): Boolean = contrast > 0f
