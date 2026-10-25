package dev.formhaus.compose.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.containers.GroupNode
import dev.formhaus.compose.containers.MatrixRows
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.RevealLayout
import dev.formhaus.compose.model.texts
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormNode
import dev.formhaus.core.model.FormSection
import dev.formhaus.core.renderer.nodeId

@Composable
public fun FormFields(
    model: FormModel,
    modifier: Modifier = Modifier,
) {
    val structure = model.structure
    key(structure.revision) { NodeStack(structure.nodes.filter { it.type != "hidden" }, model, modifier) }
}

@Composable
internal fun FormNodeView(
    node: FormNode,
    model: FormModel,
) {
    when (node) {
        is FormField -> if (node.isGroup) GroupNode(node, model) else FormhausField(node, model)
        is FormSection -> SectionNode(node, model)
    }
}

@Composable
internal fun NodeStack(
    nodes: List<FormNode>,
    model: FormModel,
    modifier: Modifier = Modifier,
) {
    val layout = rememberRevealLayout(nodes, model)
    NodeColumn(layout.nodes, layout, model, LocalFormhausTokens.current.spacing.gap, modifier)
}

@Composable
internal fun NodeColumn(
    nodes: List<FormNode>,
    layout: RevealLayout,
    model: FormModel,
    gap: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        nodes.forEachIndexed { index, node -> key(node.nodeId) { SpacedNode(node, index, model, layout) } }
    }
}

@Composable
internal fun SpacedNode(
    node: FormNode,
    index: Int,
    model: FormModel,
    layout: RevealLayout,
) {
    val tokens = LocalFormhausTokens.current
    val container = node is FormSection || (node as? FormField)?.isGroup == true
    Box(
        Modifier.padding(
            top = if (container && index > 0) tokens.spacing.s5 else 0.dp,
            bottom = if (container) tokens.spacing.s5 else 0.dp,
        ),
    ) {
        ProvideReveals(node, layout, { nested -> NodeColumn(nested, layout, model, tokens.spacing.s10) }) { FormNodeView(node, model) }
    }
}

@Composable
private fun SectionNode(
    section: FormSection,
    model: FormModel,
) {
    val tokens = LocalFormhausTokens.current
    val texts = model.texts(section)
    val description = texts.description?.takeIf { it.isNotEmpty() }
    val custom = LocalFormComponents.current.slots.section
    if (custom != null) {
        custom(SectionContext(section, texts.title.orEmpty(), description) { SectionFields(section, model) })
        return
    }
    Column(Modifier.fillMaxWidth().formhausScrollAnchor(section.key)) {
        Text(texts.title.orEmpty(), Modifier.semantics { heading() }, style = tokens.textStyle(TextRole.heading))
        if (description != null) {
            Spacer(Modifier.height(tokens.spacing.s1))
            Text(description, style = tokens.textStyle(TextRole.helper))
        }
        Spacer(Modifier.height(if (description == null) tokens.spacing.s7 else tokens.spacing.s9))
        SectionFields(section, model)
    }
}

@Composable
internal fun SectionFields(
    section: FormSection,
    model: FormModel,
) {
    val fields = section.fields.filter { it.type != "hidden" }
    if (fields.isEmpty()) return
    if (section.isMatrix) MatrixRows(fields, model) else NodeStack(fields, model)
}
