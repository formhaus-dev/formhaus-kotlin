package dev.formhaus.compose.containers

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.components.FieldError
import dev.formhaus.compose.components.FormhausIcons
import dev.formhaus.compose.components.controlShape
import dev.formhaus.compose.components.measured
import dev.formhaus.compose.form.SUMMARY_ANCHOR
import dev.formhaus.compose.form.formhausScrollAnchor
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.focus
import dev.formhaus.compose.model.formhausTestId
import dev.formhaus.compose.model.testId
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle
import dev.formhaus.core.renderer.ErrorEntry
import dev.formhaus.core.renderer.TestIdTarget

@Composable
public fun FormErrorSummary(
    model: FormModel,
    modifier: Modifier = Modifier,
) {
    val groups = model.errorGroups
    if (groups.isEmpty()) return
    val tokens = LocalFormhausTokens.current
    val title = model.label("errorSummary")
    LaunchedEffect(Unit) { model.announce(title) }
    Column(
        modifier
            .fillMaxWidth()
            .border(1.dp, tokens.colors.error, tokens.controlShape)
            .padding(horizontal = tokens.spacing.s6, vertical = tokens.spacing.s5)
            .formhausTestId(model.testId(TestIdTarget.Kind.SUMMARY))
            .formhausScrollAnchor(SUMMARY_ANCHOR)
            .measured("summary"),
    ) {
        Text(
            title,
            Modifier
                .summaryFocus(model)
                .semantics { heading() }
                .padding(bottom = tokens.spacing.s1),
            style = tokens.textStyle(TextRole.label),
        )
        groups.forEachIndexed { index, group ->
            if (groups.size > 1) {
                Text(
                    group.title,
                    Modifier
                        .semantics { heading() }
                        .padding(top = if (index > 0) tokens.spacing.s5 else tokens.spacing.s3),
                    style = tokens.textStyle(TextRole.helper),
                )
            }
            group.entries.forEachIndexed { row, entry ->
                if (row > 0) HorizontalDivider(thickness = Dp.Hairline, color = tokens.colors.border)
                SummaryEntry(model, entry)
            }
        }
    }
}

@Composable
private fun SummaryEntry(
    model: FormModel,
    entry: ErrorEntry,
) {
    val tokens = LocalFormhausTokens.current
    val color = tokens.colors.error
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = tokens.controls.hitTarget)
            .clickable(role = Role.Button) { model.focus(entry.key) }
            .padding(vertical = tokens.spacing.s3),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(entry.text, Modifier.weight(1f), color = color, style = tokens.textStyle(TextRole.body, colored = false))
        Icon(FormhausIcons.chevronRight, null, Modifier.size(tokens.typography.small.value.dp), tint = color)
    }
}

@Composable
public fun FormTopLevelErrors(
    model: FormModel,
    modifier: Modifier = Modifier,
) {
    val errors = model.topLevelErrors + listOfNotNull(model.actionError)
    if (errors.isEmpty()) return
    val tokens = LocalFormhausTokens.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(tokens.spacing.s1)) {
        errors.forEach { FieldError(it) }
    }
}

private class HandledRequest(
    var id: Int? = null,
)

@Composable
internal fun Modifier.summaryFocus(model: FormModel): Modifier {
    val requester = remember { FocusRequester() }
    val handled = remember { HandledRequest() }
    val request = model.focusRequest
    LaunchedEffect(request) {
        if (request == null || !request.summary || request.id == handled.id) return@LaunchedEffect
        handled.id = request.id
        withFrameNanos {}
        requester.requestFocus()
    }
    return focusRequester(requester).focusable()
}
