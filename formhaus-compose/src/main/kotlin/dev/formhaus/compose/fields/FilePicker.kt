package dev.formhaus.compose.fields

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.formhaus.compose.form.FieldContext
import kotlinx.coroutines.launch
import java.io.File

internal const val ANY_FILE = "*" + "/" + "*"

@Stable
internal class FilePicker {
    var active by mutableStateOf(false)
    var launch: () -> Unit = {}

    fun open() {
        active = true
        launch()
    }
}

@Composable
internal fun rememberFilePicker(
    context: FieldContext,
    receive: (List<File>) -> Unit,
): FilePicker {
    val android = LocalContext.current
    val types = acceptedTypes(context.field.accept?.stringValue)
    val photos = types.all { it.startsWith("image/") }
    val picker = remember { FilePicker() }
    val received: (List<Uri>) -> Unit = { uris ->
        picker.active = false
        if (uris.isNotEmpty()) {
            context.model.scope.launch {
                val copies =
                    uris.mapNotNull { uri ->
                        UploadStore.copy(
                            android.cacheDir,
                            context.model.uploadOwner,
                            { android.displayName(uri) },
                        ) { android.contentResolver.openInputStream(uri) }
                    }
                if (copies.isNotEmpty()) receive(copies)
            }
        }
    }
    val one: (Uri?) -> Unit = { received(listOfNotNull(it)) }
    val multiple = context.field.isFileList
    val documents =
        if (multiple) {
            rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), received)
        } else {
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), one)
        }
    val media =
        if (multiple) {
            rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(), received)
        } else {
            rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), one)
        }
    picker.launch = {
        if (photos) {
            media.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            documents.launch(types.toTypedArray())
        }
    }
    return picker
}

private fun Context.displayName(uri: Uri): String {
    val named =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    return named ?: uri.lastPathSegment ?: "file"
}

internal fun referenceName(value: String): String {
    if (!value.contains("://") || value.startsWith("content://", ignoreCase = true)) return value
    return Uri.parse(value).lastPathSegment?.takeIf { it.isNotEmpty() } ?: value
}

internal fun acceptedTypes(accept: String?): List<String> {
    val types =
        accept
            .orEmpty()
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .mapNotNull { entry ->
                if (entry.startsWith(".")) MimeTypeMap.getSingleton().getMimeTypeFromExtension(entry.drop(1)) else entry
            }
    return types.ifEmpty { listOf(ANY_FILE) }
}
