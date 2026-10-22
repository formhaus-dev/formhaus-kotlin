package dev.formhaus.compose.form

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import dev.formhaus.core.json.ValueParts
import dev.formhaus.core.json.describe
import dev.formhaus.core.json.equalParts
import dev.formhaus.core.json.partsHash

public enum class BackPlacement { AUTOMATIC, BOTTOM, APP_BAR }

public enum class StepScroll { SCREEN_TOP, FORM_TOP }

public enum class TitleDisplayMode { AUTOMATIC, INLINE, LARGE, OFF }

@Suppress("LongParameterList")
public class FormBehavior(
    public val errorSummary: Boolean = true,
    public val confirmRemove: Boolean = true,
    public val stepErrorMarks: Boolean = false,
    public val backPlacement: BackPlacement = BackPlacement.AUTOMATIC,
    public val stepScroll: StepScroll = StepScroll.SCREEN_TOP,
    public val inlineChoiceLimit: Int = 6,
    public val titleDisplayMode: TitleDisplayMode = TitleDisplayMode.AUTOMATIC,
) {
    public fun copy(
        errorSummary: Boolean = this.errorSummary,
        confirmRemove: Boolean = this.confirmRemove,
        stepErrorMarks: Boolean = this.stepErrorMarks,
        backPlacement: BackPlacement = this.backPlacement,
        stepScroll: StepScroll = this.stepScroll,
        inlineChoiceLimit: Int = this.inlineChoiceLimit,
        titleDisplayMode: TitleDisplayMode = this.titleDisplayMode,
    ): FormBehavior =
        FormBehavior(errorSummary, confirmRemove, stepErrorMarks, backPlacement, stepScroll, inlineChoiceLimit, titleDisplayMode)

    override fun equals(other: Any?): Boolean = equalParts(other, FormBehavior::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("FormBehavior")

    private fun parts(): ValueParts =
        listOf(
            "errorSummary" to errorSummary,
            "confirmRemove" to confirmRemove,
            "stepErrorMarks" to stepErrorMarks,
            "backPlacement" to backPlacement,
            "stepScroll" to stepScroll,
            "inlineChoiceLimit" to inlineChoiceLimit,
            "titleDisplayMode" to titleDisplayMode,
        )
}

public val LocalFormBehavior: ProvidableCompositionLocal<FormBehavior> = staticCompositionLocalOf { FormBehavior() }
