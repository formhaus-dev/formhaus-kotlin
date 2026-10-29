package dev.formhaus.compose.fields

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
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
    room: Int,
    receive: (List<File>, Int) -> Unit,
): FilePicker {
    val android = LocalContext.current
    val types = acceptedTypes(context.field.accept?.stringValue)
    val photos = types.all { it.startsWith("image/") }
    val picker = remember { FilePicker() }
    val received: (List<Uri>) -> Unit = { uris ->
        picker.active = false
        val kept = uris.take(room)
        if (uris.isNotEmpty()) {
            context.model.scope.launch {
                val copies =
                    kept.mapNotNull { uri ->
                        UploadStore.copy(
                            android.cacheDir,
                            context.model.uploadOwner,
                            { android.displayName(uri) },
                        ) { android.contentResolver.openInputStream(uri) }
                    }
                if (copies.isNotEmpty() || kept.size < uris.size) receive(copies, uris.size - kept.size)
            }
        }
    }
    val one: (Uri?) -> Unit = { received(listOfNotNull(it)) }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), one)
    val media = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), one)
    val many = if (context.field.isFileList) rememberManyPicker(room, received) else null
    picker.launch = {
        when {
            photos && many != null && many.mediaLimit >= 2 -> many.media.launch(IMAGES)
            photos -> media.launch(IMAGES)
            many != null && room >= 2 -> many.documents.launch(types.toTypedArray())
            else -> documents.launch(types.toTypedArray())
        }
    }
    return picker
}

private val IMAGES = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

private class ManyPicker(
    val documents: ActivityResultLauncher<Array<String>>,
    val media: ActivityResultLauncher<PickVisualMediaRequest>,
    val mediaLimit: Int,
)

@Composable
private fun rememberManyPicker(
    room: Int,
    received: (List<Uri>) -> Unit,
): ManyPicker {
    val limit = minOf(room, pickImagesLimit())
    val contract = remember(limit) { ActivityResultContracts.PickMultipleVisualMedia(maxOf(limit, 2)) }
    return ManyPicker(
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), received),
        rememberLauncherForActivityResult(contract, received),
        limit,
    )
}

private fun pickImagesLimit(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 2) {
        MediaStore.getPickImagesMaxLimit()
    } else {
        Int.MAX_VALUE
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
