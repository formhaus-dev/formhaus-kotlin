package dev.formhaus.compose.system

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.components.FormhausIcons
import dev.formhaus.compose.containers.GroupNode
import dev.formhaus.compose.containers.summaryFocus
import dev.formhaus.compose.form.ActionsContext
import dev.formhaus.compose.form.FormhausField
import dev.formhaus.compose.form.LocalFormComponents
import dev.formhaus.compose.form.SUMMARY_ANCHOR
import dev.formhaus.compose.form.SectionContext
import dev.formhaus.compose.form.formhausScrollAnchor
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.focus
import dev.formhaus.compose.model.formhausTestId
import dev.formhaus.compose.model.testId
import dev.formhaus.compose.model.texts
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormNode
import dev.formhaus.core.model.FormSection
import dev.formhaus.core.renderer.TestIdTarget
import dev.formhaus.core.renderer.nodeId

@Composable
internal fun SystemNodes(
    nodes: List<FormNode>,
    model: FormModel,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        for (node in nodes) {
            key(node.nodeId) {
                when (node) {
                    is FormField -> if (node.isGroup) GroupNode(node, model) else FormhausField(node, model)
                    is FormSection -> SystemSection(node, model)
                }
            }
        }
    }
}

@Composable
private fun SystemSection(
    section: FormSection,
    model: FormModel,
) {
    val texts = model.texts(section)
    val description = texts.description?.takeIf { it.isNotEmpty() }
    val fields = section.fields.filter { it.type != "hidden" }
    val custom = LocalFormComponents.current.slots.section
    if (custom != null) return custom(SectionContext(section, texts.title.orEmpty(), description) { SystemNodes(fields, model) })
    Column(Modifier.fillMaxWidth().formhausScrollAnchor(section.key), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        texts.title?.takeIf { it.isNotEmpty() }?.let {
            Text(
                it,
                Modifier.semantics { heading() },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        description?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
        SystemNodes(fields, model)
    }
}

@Composable
internal fun SystemStepHeader(model: FormModel) {
    val structure = model.structure
    if (!structure.isMultiStep) return
    val step = structure.currentStep
    val texts = step?.let { model.texts(it) }
    val progress = structure.progress
    val title =
        texts?.title?.takeIf { it.isNotEmpty() }
            ?: model.label("stepTitle", stringMapOf("index" to JsonValue.Number(structure.stepIndex + 1.0)))
    val counter =
        model.label(
            "stepOf",
            stringMapOf("current" to JsonValue.Number(progress.current.toDouble()), "total" to JsonValue.Number(progress.total.toDouble())),
        )
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(counter, color = muted, style = MaterialTheme.typography.labelLarge)
        Text(
            title,
            Modifier.semantics { heading() },
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
        )
        texts?.description?.takeIf { it.isNotEmpty() }?.let { Text(it, color = muted, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
internal fun SystemErrorSummary(model: FormModel) {
    val groups = model.errorGroups
    if (groups.isEmpty()) return
    val error = LocalFormhausTokens.current.colors.error
    val title = model.label("errorSummary")
    LaunchedEffect(Unit) { model.announce(title) }
    OutlinedCard(
        Modifier
            .fillMaxWidth()
            .formhausTestId(model.testId(TestIdTarget.Kind.SUMMARY))
            .formhausScrollAnchor(SUMMARY_ANCHOR),
        border = BorderStroke(1.dp, error),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(title, Modifier.summaryFocus(model).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
            for (group in groups) {
                for (entry in group.entries) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { model.focus(entry.key) },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(FormhausIcons.alert, null, Modifier.size(20.dp), tint = error)
                        Column(Modifier.weight(1f)) {
                            Text(entry.text, color = error, style = MaterialTheme.typography.bodyLarge)
                            if (groups.size > 1) {
                                Text(
                                    group.title,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SystemActions(context: ActionsContext) {
    val state = context.state
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.showPrimary) {
            Button(
                context::performPrimaryAction,
                Modifier.fillMaxWidth().formhausTestId(context.primaryTestId),
                enabled = !context.busy && !state.primaryDisabled,
            ) {
                if (context.busy && !context.model.loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(state.primaryLabel)
                }
            }
        }
        if (state.showBack) {
            OutlinedButton(context::back, Modifier.fillMaxWidth().formhausTestId(context.testIds.back), enabled = !context.busy) {
                Text(state.backLabel)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            state.cancelAction?.let { cancel ->
                TextButton(context::cancel, Modifier.formhausTestId(context.testIds.cancel), enabled = !context.busy) {
                    Text(cancel.label.orEmpty())
                }
            }
            if (state.showSkip) TextButton(context::skip, enabled = !context.busy) { Text(state.skipLabel) }
        }
    }
}
