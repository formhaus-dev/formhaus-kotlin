package dev.formhaus.compose.platform

import android.content.Context
import android.content.pm.ApplicationInfo

internal val Context.isDebuggable: Boolean
    get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
