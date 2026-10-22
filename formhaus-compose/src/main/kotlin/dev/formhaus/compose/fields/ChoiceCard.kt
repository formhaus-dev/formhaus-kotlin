package dev.formhaus.compose.fields

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.formhaus.compose.components.IndicatorShape
import dev.formhaus.compose.components.OptionCardBox
import dev.formhaus.compose.components.OptionImage
import dev.formhaus.compose.components.OptionRow
import dev.formhaus.compose.components.ZoomButton
import dev.formhaus.compose.model.formhausTestId
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.core.model.FieldOption
import dev.formhaus.core.renderer.safeImage

@Composable
internal fun ChoiceCard(
    option: FieldOption,
    shape: IndicatorShape,
    selected: Boolean,
    picture: Boolean,
    enabled: Boolean,
    zoomLabel: String,
    testId: String?,
    choose: () -> Unit,
    zoom: () -> Unit,
) {
    val tokens = LocalFormhausTokens.current
    val image = option.safeImage.takeIf { picture }
    val fill = if (picture) Modifier.fillMaxHeight() else Modifier
    Box(fill) {
        OptionCardBox(
            selected,
            choose,
            fill.formhausTestId(testId),
            invalid = false,
            enabled = enabled,
            shape = shape,
            top = if (picture) tokens.spacing.s3 else tokens.spacing.s5,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.s3)) {
                if (image != null) OptionImage(image)
                OptionRow(shape, option.label ?: option.value.orEmpty(), selected)
            }
        }
        if (image != null) {
            val inset = (tokens.controls.hitTarget - tokens.controls.iconButton) / 2
            ZoomButton(
                zoomLabel,
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = inset - tokens.spacing.s6 - tokens.spacing.s3, y = tokens.spacing.s3 * 2 - inset),
                action = zoom,
            )
        }
    }
}
