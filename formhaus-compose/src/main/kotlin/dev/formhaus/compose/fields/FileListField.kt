package dev.formhaus.compose.fields

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import dev.formhaus.compose.components.DescribedButton
import dev.formhaus.compose.components.FormButtonVariant
import dev.formhaus.compose.components.OnFormhausFocus
import dev.formhaus.compose.components.PickerInput
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.model.requestFocus
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.renderer.fileListLimit
import dev.formhaus.core.renderer.maxItemsMessage
import dev.formhaus.core.validation.fileItems
import java.io.File

@Composable
internal fun FileListField(context: FieldContext) {
    val tokens = LocalFormhausTokens.current
    val room = context.fileRoom
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberFilePicker(context, room) { files, dropped -> notice = context.addFiles(files, dropped) }
    val files = context.model.fileEntries(context.value)
    val choose = context.placeholder ?: context.text("chooseFile")
    PickerInput(
        context,
        picker.active,
        false,
        JsonValue.Null,
        listOfNotNull(
            context.text("fileCount", stringMapOf("count" to JsonValue.Number(files.size.toDouble()))),
            context.model.engine
                .maxItemsMessage(context.field)
                .takeIf { room <= 0 },
        ).joinToString(". "),
        picker::open,
        enabled = room > 0,
        below = {
            if (files.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = tokens.spacing.s3)) {
                    for ((position, file) in files.withIndex()) {
                        FileRow(context, file, position, files.size) { notice = null }
                    }
                }
            }
            notice?.let { Text(it, Modifier.padding(top = tokens.spacing.s1), style = tokens.textStyle(TextRole.helper)) }
        },
    ) {
        FilePickerLabel(choose, true, tokens.colors.control)
    }
}

@Composable
private fun FileRow(
    context: FieldContext,
    file: FileEntry,
    position: Int,
    count: Int,
    removed: () -> Unit,
) {
    val tokens = LocalFormhausTokens.current
    val requester = remember { FocusRequester() }
    OnFormhausFocus(context.model.field(context.removeKey(position)), context.model, context.isLocked) { requester.requestFocus() }
    val remove = context.text("removeItem")
    Row(
        Modifier.fillMaxWidth().heightIn(min = tokens.controls.hitTarget),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            file.name,
            Modifier.weight(1f),
            color = if (file.file == null) tokens.colors.textMuted else tokens.colors.text,
            maxLines = if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) LARGE_NAME_LINES else NAME_LINES,
            overflow = TextOverflow.MiddleEllipsis,
            style = tokens.textStyle(TextRole.body, colored = false),
        )
        DescribedButton(
            remove,
            FormButtonVariant.TEXT,
            {
                context.removeFile(file, position, count)
                removed()
            },
            Modifier.focusRequester(requester),
            enabled = !context.isLocked,
            busy = false,
            compact = true,
            icon = null,
            description =
                context.text(
                    "removeItemNamed",
                    stringMapOf("label" to JsonValue.String(remove), "item" to JsonValue.String(isolated(file.name))),
                ),
        )
    }
}

private const val NAME_LINES = 2
private const val LARGE_NAME_LINES = 4
private const val LARGE_FONT_SCALE = 1.3f

private val FieldContext.fileRoom: Int
    get() = (fileListLimit(field) - fileItems(state.value).size).coerceAtLeast(0)

private fun FieldContext.removeKey(position: Int): String = "$key#remove$position"

internal fun FieldContext.addFiles(
    files: List<File>,
    dropped: Int,
): String? {
    if (files.isNotEmpty()) setValue(JsonValue.Array(fileItems(state.value) + files.map { JsonValue.String(fileValue(it)) }))
    val added = files.takeIf { it.isNotEmpty() }?.let { label("itemAdded", it.joinToString(", ") { file -> isolated(file.name) }) }
    val limit = if (dropped > 0) model.engine.maxItemsMessage(field) else null
    model.alerts.announce(listOfNotNull(added, limit).joinToString(" "))
    if (files.isNotEmpty() && fileRoom <= 0) model.requestFocus(removeKey(model.fileEntries(state.value).size - 1))
    return limit
}

private fun FieldContext.removeFile(
    file: FileEntry,
    position: Int,
    count: Int,
) {
    val items = fileItems(state.value)
    if (items.getOrNull(file.index) != file.value) return
    setValue(JsonValue.Array(items.filterIndexed { index, _ -> index != file.index }))
    if (file.file != null && file.value !in fileItems(state.value)) UploadStore.discard(fileValue(file.file), model.uploadOwner)
    model.alerts.announce(label("itemRemoved", isolated(file.name)))
    model.requestFocus(
        when {
            position < count - 1 -> removeKey(position)
            position > 0 -> removeKey(position - 1)
            else -> key
        },
    )
}

private fun FieldContext.label(
    name: String,
    item: String,
): String = text(name, stringMapOf("item" to JsonValue.String(item)))

private fun isolated(name: String): String = "\u2068$name\u2069"
