package dev.formhaus.compose.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.form.formhausScrollAnchor
import dev.formhaus.compose.model.formhausTestId
import dev.formhaus.compose.theme.FormhausTokens
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle

@Composable
public fun FieldShell(
    context: FieldContext,
    modifier: Modifier = Modifier,
    variant: FieldShellVariant = FieldShellVariant.STANDARD,
    content: @Composable () -> Unit,
) {
    val tokens = LocalFormhausTokens.current
    val inline = variant == FieldShellVariant.INLINE || variant == FieldShellVariant.ROW
    val inset = if (variant == FieldShellVariant.INLINE) tokens.controls.indicator + tokens.spacing.s5 else 0.dp
    val helper = context.helperText
    val error = context.error?.takeIf { it.isNotEmpty() }
    Column(
        modifier
            .fillMaxWidth()
            .alpha(if (inline && context.isLocked) 0.6f else 1f)
            .semantics { if (error != null) error(error) }
            .formhausTestId(context.testId)
            .formhausScrollAnchor(context)
            .measured("${context.key}/field"),
    ) {
        if (!inline) FieldHeader(context, variant, helper)
        Row(Modifier.trimmedToText(inline && (helper != null || error != null)), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f, fill = false).measured("${context.key}/control")) { content() }
            if (variant == FieldShellVariant.INLINE) {
                Spacer(Modifier.padding(start = tokens.spacing.s3))
                LoadingIndicator(context.loading)
            }
        }
        if (inline && helper != null) {
            Spacer(Modifier.height(tokens.spacing.s3 - tokens.spacing.s1))
            Text(helper, Modifier.padding(start = inset), style = tokens.textStyle(TextRole.helper))
        }
        if (error != null) {
            Spacer(Modifier.height(tokens.spacing.s3))
            FieldError(error, Modifier.padding(start = inset))
        }
    }
}

@Composable
internal fun FieldHeader(
    context: FieldContext,
    variant: FieldShellVariant,
    helper: String?,
) {
    val tokens = LocalFormhausTokens.current
    if (context.label.isNotEmpty()) {
        Row(Modifier.measured("${context.key}/label"), verticalAlignment = Alignment.CenterVertically) {
            FieldLabel(context, heading = variant == FieldShellVariant.GROUP)
            if (variant == FieldShellVariant.GROUP) {
                Spacer(Modifier.padding(start = tokens.spacing.s3))
                LoadingIndicator(context.loading)
            }
        }
        Spacer(Modifier.height(if (helper != null) tokens.spacing.s3 - tokens.spacing.s1 else tokens.spacing.s3))
    }
    if (helper != null) {
        Text(helper, style = tokens.textStyle(TextRole.helper))
        Spacer(Modifier.height(tokens.spacing.s3))
    }
}

@Composable
public fun FieldLabel(
    context: FieldContext,
    modifier: Modifier = Modifier,
    heading: Boolean = false,
) {
    FieldLabel(context.label, context.isRequired, context.text("required"), modifier, heading)
}

@Composable
public fun FieldLabel(
    text: String,
    required: Boolean,
    requiredLabel: String,
    modifier: Modifier = Modifier,
    heading: Boolean = false,
) {
    val tokens = LocalFormhausTokens.current
    Text(
        tokens.marked(text, required),
        modifier.semantics {
            contentDescription = if (required) "$text, $requiredLabel" else text
            if (heading) heading()
        },
        style = tokens.textStyle(TextRole.label),
    )
}

@Composable
public fun FieldError(
    text: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFormhausTokens.current
    Text(text, modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = tokens.textStyle(TextRole.error))
}

internal fun FormhausTokens.marked(
    text: String,
    required: Boolean,
): AnnotatedString =
    buildAnnotatedString {
        append(text)
        if (required) withStyle(SpanStyle(color = colors.error)) { append(" *") }
    }

private val TextBottom = HorizontalAlignmentLine(::maxOf)

internal fun Modifier.textBottom(): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height, mapOf(TextBottom to placeable.height)) { placeable.place(0, 0) }
    }

private fun Modifier.trimmedToText(trim: Boolean): Modifier =
    if (!trim) {
        this
    } else {
        layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val bottom = placeable[TextBottom]
            val height = if (bottom == AlignmentLine.Unspecified) placeable.height else minOf(placeable.height, bottom)
            layout(placeable.width, height) { placeable.place(0, 0) }
        }
    }

public enum class FieldShellVariant { STANDARD, INLINE, ROW, GROUP }
