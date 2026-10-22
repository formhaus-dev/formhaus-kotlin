package dev.formhaus.compose.form

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import dev.formhaus.compose.containers.FormActionsSlot
import dev.formhaus.compose.containers.FormErrorSummary
import dev.formhaus.compose.containers.FormProgress
import dev.formhaus.compose.containers.FormTopLevelErrors
import dev.formhaus.compose.fields.ChoiceError
import dev.formhaus.compose.fields.ChoiceHeader
import dev.formhaus.compose.fields.ChoicePlan
import dev.formhaus.compose.fields.ChoiceRow
import dev.formhaus.compose.fields.choicePlan
import dev.formhaus.compose.fields.choiceRowGap
import dev.formhaus.compose.fields.mayNeedRows
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.formhausTestId
import dev.formhaus.compose.model.testId
import dev.formhaus.compose.theme.FormhausTokens
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormNode
import dev.formhaus.core.model.FormSection
import dev.formhaus.core.renderer.TestIdTarget
import dev.formhaus.core.renderer.nodeId

private const val LAZY_NODES = 24

internal fun FormDefinition.rendersLazily(components: FormComponents): Boolean {
    val pages = steps?.map { it.fields } ?: listOf(fields.orEmpty())
    return pages.any { nodes -> nodes.size > LAZY_NODES || nodes.any { (it as? FormField)?.mayNeedRows(components) == true } }
}

private class ItemIndex {
    private val keys = HashMap<String, Int>()

    fun clear() = keys.clear()

    operator fun set(
        key: String,
        index: Int,
    ) {
        keys[key] = index
    }

    fun find(key: String): Int? = keys[key] ?: keys.entries.firstOrNull { key.startsWith("${it.key}.") }?.value
}

@Composable
internal fun LazyFormScroller(
    model: FormModel,
    modifier: Modifier,
) {
    val list = rememberLazyListState()
    val top = rememberTopRequests { reduceMotion -> if (reduceMotion) list.scrollToItem(0) else list.animateScrollToItem(0) }
    CompositionLocalProvider(LocalTopRequests provides top) {
        FormFrame(model) { LazyFormBody(model, list, modifier) }
    }
}

@Composable
private fun LazyFormBody(
    model: FormModel,
    list: LazyListState,
    modifier: Modifier,
) {
    val tokens = LocalFormhausTokens.current
    val behavior = LocalFormBehavior.current
    val components = LocalFormComponents.current
    val anchors = LocalScrollAnchors.current
    val index = remember { ItemIndex() }
    DisposableEffect(anchors, list) {
        anchors?.revealer = AnchorRevealer { key -> index.find(key)?.let { list.scrollToItem(it) } != null }
        onDispose { anchors?.revealer = null }
    }
    BoxWithConstraints(modifier.fillMaxSize().background(tokens.colors.background)) {
        val width = min(maxWidth, tokens.controls.maxWidth) - tokens.spacing.s10 * 2
        LazyColumn(
            Modifier.fillMaxSize().formhausTestId(model.testId(TestIdTarget.Kind.FORM)),
            state = list,
            contentPadding = PaddingValues(tokens.spacing.s10),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { FormItems(this, index, width, tokens).add(model, behavior, components) }
    }
}

private class FormItems(
    private val scope: LazyListScope,
    private val index: ItemIndex,
    private val width: Dp,
    private val tokens: FormhausTokens,
) {
    private var count = 0

    fun add(
        model: FormModel,
        behavior: FormBehavior,
        components: FormComponents,
    ) {
        index.clear()
        index[FORM_TOP_ANCHOR] = 0
        val structure = model.structure
        if (structure.isMultiStep) item("progress", emptyList()) { FormProgress(model) }
        structure.nodes.filter { it.type != "hidden" }.forEachIndexed { position, node ->
            val key = "${structure.revision}:${node.nodeId}"
            val field = node as? FormField
            val plan = field?.let { choicePlan(it, model, components, tokens, width) }
            if (field != null && plan != null) {
                choice(key, field, model, plan)
            } else {
                item(key, node.anchorKeys) { SpacedNode(node, position, model) }
            }
        }
        if (model.topLevelErrors.isNotEmpty() || model.actionError != null) item("errors", emptyList()) { FormTopLevelErrors(model) }
        if (behavior.errorSummary && model.errorGroups.isNotEmpty()) item("summary", listOf(SUMMARY_ANCHOR)) { FormErrorSummary(model) }
        item("actions", emptyList()) { FormActionsSlot(model) }
    }

    private fun choice(
        key: String,
        field: FormField,
        model: FormModel,
        plan: ChoicePlan,
    ) {
        item("$key/head", listOf(field.key)) { ChoiceHeader(field, model) }
        val gap = tokens.choiceRowGap(plan)
        val first = count
        count += plan.rows
        scope.items(plan.rows, { "$key/row/$it" }) { row ->
            Spaced(if (row == 0) 0.dp else gap, first + row) { ChoiceRow(field, model, plan, row) }
        }
        if (!model.field(field.key).error.isNullOrEmpty()) {
            val position = count++
            scope.item("$key/error") { Spaced(0.dp, position) { ChoiceError(field, model) } }
        }
    }

    private fun item(
        key: String,
        anchors: List<String>,
        content: @Composable () -> Unit,
    ) {
        val position = count++
        anchors.forEach { index[it] = position }
        scope.item(key) { Spaced(tokens.spacing.gap, position, content) }
    }

    @Composable
    private fun Spaced(
        gap: Dp,
        position: Int,
        content: @Composable () -> Unit,
    ) {
        val top = if (position == 0) Modifier.formhausScrollAnchor(FORM_TOP_ANCHOR) else Modifier.padding(top = gap)
        Box(Modifier.width(width).then(top)) { content() }
    }
}

private val FormNode.anchorKeys: List<String>
    get() =
        when (this) {
            is FormField -> listOf(key)
            is FormSection -> listOf(key) + fields.filterIsInstance<FormField>().map { it.key }
        }
