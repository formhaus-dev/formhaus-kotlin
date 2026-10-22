package dev.formhaus.compose.form

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

public enum class BackPlacement { AUTOMATIC, BOTTOM }

public enum class StepScroll { SCREEN_TOP, FORM_TOP }

public enum class TitleDisplayMode { AUTOMATIC, INLINE, LARGE, OFF }

public data class FormBehavior(
    val errorSummary: Boolean = true,
    val confirmRemove: Boolean = true,
    val stepErrorMarks: Boolean = false,
    val backPlacement: BackPlacement = BackPlacement.AUTOMATIC,
    val stepScroll: StepScroll = StepScroll.SCREEN_TOP,
    val inlineChoiceLimit: Int = 6,
    val titleDisplayMode: TitleDisplayMode = TitleDisplayMode.AUTOMATIC,
)

public val LocalFormBehavior: ProvidableCompositionLocal<FormBehavior> = staticCompositionLocalOf { FormBehavior() }
