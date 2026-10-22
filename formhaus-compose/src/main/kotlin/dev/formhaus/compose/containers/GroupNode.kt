package dev.formhaus.compose.containers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import dev.formhaus.compose.form.ConfirmContext
import dev.formhaus.compose.form.FormhausField
import dev.formhaus.compose.form.GroupContext
import dev.formhaus.compose.form.GroupItemContext
import dev.formhaus.compose.form.LocalFormBehavior
import dev.formhaus.compose.form.LocalFormComponents
import dev.formhaus.compose.form.Slot
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.addItem
import dev.formhaus.compose.model.itemTitle
import dev.formhaus.compose.model.removeItem
import dev.formhaus.compose.model.texts
import dev.formhaus.compose.system.SystemGroup
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.core.engine.GroupItem
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.model.FormField
import dev.formhaus.core.renderer.itemHasContent
import dev.formhaus.core.renderer.itemLimits

@Composable
internal fun GroupNode(
    group: FormField,
    model: FormModel,
) {
    val components = LocalFormComponents.current
    val confirmRemove = LocalFormBehavior.current.confirmRemove
    var confirming by remember(group.key) { mutableStateOf<String?>(null) }
    val state = model.group(group.key)
    val actions = GroupActions(group, model, confirmRemove) { confirming = it }
    val context = actions.context(state.items, state.hasContent)
    val custom = components.slots.group
    when {
        custom != null -> custom(context)
        components.system -> SystemGroup(context, model.field(group.key))
        else -> DefaultGroup(context, model.field(group.key))
    }
    val pending = state.items.indexOfFirst { it.id == confirming }
    if (pending >= 0) RemoveConfirmation(actions.confirmContext(pending), components.slots.confirm)
}

private class GroupActions(
    private val group: FormField,
    private val model: FormModel,
    private val confirmRemove: Boolean,
    private val setConfirming: (String?) -> Unit,
) {
    private val removeLabel: String = group.removeLabel ?: model.label("removeItem")

    fun context(
        items: List<GroupItem>,
        hasContent: List<Boolean>,
    ): GroupContext {
        val limits = model.engine.itemLimits(group)
        val texts = model.texts(group)
        val entries =
            items.mapIndexed { index, item ->
                itemContext(
                    item,
                    index,
                    items.size > limits.min,
                    hasContent.getOrNull(index) == true,
                )
            }
        return GroupContext(
            group,
            texts.label.orEmpty(),
            texts.helperText,
            model.field(group.key).error,
            entries,
            items.size < limits.max,
            group.addLabel ?: model.label("addItem"),
            model.disabled,
            { model.addItem(group.key) },
            model,
        )
    }

    fun confirmContext(index: Int): ConfirmContext {
        val item = stringMapOf<JsonValue>("item" to JsonValue.String(title(index)))
        return ConfirmContext(
            model.label("removeItemPrompt", item),
            model.label("removeItemBody", item),
            removeName(index),
            model.label("cancel"),
            { drop(index) },
            { keep(index) },
        )
    }

    private fun itemContext(
        item: GroupItem,
        index: Int,
        canRemove: Boolean,
        hasContent: Boolean,
    ): GroupItemContext =
        GroupItemContext(
            item.id,
            index,
            title(index),
            canRemove,
            hasContent,
            removeLabel,
            removeName(index),
            model.disabled,
            { GroupItemFields(group, item, index, model) },
            { ask(index) },
            { drop(index) },
            model.field(removeKey(index)),
            model,
            item.fields,
        )

    private fun title(index: Int): String = model.itemTitle(index, group.key)

    private fun removeName(index: Int): String =
        model.label(
            "removeItemNamed",
            stringMapOf("label" to JsonValue.String(removeLabel), "item" to JsonValue.String(title(index))),
        )

    private fun removeKey(index: Int): String = "${group.key}#remove$index"

    private fun keep(index: Int) {
        setConfirming(null)
        model.requestFocus(removeKey(index))
    }

    private fun drop(index: Int) {
        setConfirming(null)
        model.removeItem(index, group.key)
    }

    private fun ask(index: Int) {
        val items = model.group(group.key).items
        if (!confirmRemove || !model.engine.itemHasContent(group.key, index) || index !in items.indices) return drop(index)
        setConfirming(items[index].id)
    }
}

@Composable
private fun GroupItemFields(
    group: FormField,
    item: GroupItem,
    index: Int,
    model: FormModel,
) {
    val tokens = LocalFormhausTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.gap)) {
        for (field in item.fields) {
            if (field.type != "hidden") FormhausField(field, model, "${group.key}.$index.${field.key}")
        }
    }
}

@Composable
private fun RemoveConfirmation(
    context: ConfirmContext,
    custom: Slot<ConfirmContext>?,
) {
    if (custom != null) {
        Dialog(onDismissRequest = context.cancel) { custom(context) }
        return
    }
    val tokens = LocalFormhausTokens.current
    AlertDialog(
        onDismissRequest = context.cancel,
        confirmButton = { TextButton(context.confirm) { Text(context.confirmLabel, color = tokens.colors.error) } },
        dismissButton = { TextButton(context.cancel) { Text(context.cancelLabel) } },
        title = { Text(context.title) },
        text = { Text(context.message) },
    )
}
