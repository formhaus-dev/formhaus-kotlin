package dev.formhaus.compose.fields

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import dev.formhaus.compose.components.FormhausIcons
import dev.formhaus.compose.components.PickerInput
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle
import dev.formhaus.core.json.JsonValue
import java.io.File

@Composable
public fun FileField(context: FieldContext) {
    if (context.field.isFileList) {
        FileListField(context)
        return
    }
    val colors = LocalFormhausTokens.current.colors
    val picker = rememberFilePicker(context, 1) { files, _ -> files.firstOrNull()?.let(context::replaceFile) }
    PickerInput(context, picker.active, context.text.isNotEmpty(), JsonValue.Null, context.fileValueText, picker::open) {
        FilePickerLabel(
            context.fileName ?: context.referenceName ?: context.placeholder ?: context.text("chooseFile"),
            context.reference == null,
            when {
                context.fileName != null -> colors.text
                context.reference == null -> colors.control
                else -> colors.textMuted
            },
        )
    }
}

@Composable
internal fun FilePickerLabel(
    text: String,
    icon: Boolean,
    color: Color,
) {
    val tokens = LocalFormhausTokens.current
    Row(
        Modifier.heightIn(min = tokens.controls.hitTarget),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) Icon(FormhausIcons.paperclip, null, Modifier.size(tokens.controls.indicator), tint = tokens.colors.textMuted)
        Text(
            text,
            Modifier.weight(1f),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            style = tokens.textStyle(TextRole.body, colored = false),
        )
    }
}

internal val FieldContext.fileName: String? get() = UploadStore.owned(text, model.uploadOwner)?.name

internal val FieldContext.reference: String? get() = if (text.isEmpty() || fileName != null) null else text

internal val FieldContext.referenceName: String? get() = reference?.let(::referenceName)

internal val FieldContext.fileValueText: String get() = fileName ?: referenceName ?: text("chooseFile")

internal fun FieldContext.replaceFile(file: File?) {
    val previous = text.takeIf { fileName != null }
    setValue(file?.let { JsonValue.String(fileValue(it)) } ?: JsonValue.Null)
    if (previous != null && state.value?.stringValue != previous) UploadStore.discard(previous, model.uploadOwner)
}
