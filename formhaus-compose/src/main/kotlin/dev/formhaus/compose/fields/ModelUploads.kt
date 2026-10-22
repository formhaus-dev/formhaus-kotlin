package dev.formhaus.compose.fields

import dev.formhaus.compose.model.FormModel
import dev.formhaus.core.engine.FormSnapshot
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.renderer.value
import java.io.File

public fun FormModel.pickedFile(key: String): File? = engine.value(key)?.stringValue?.let { UploadStore.owned(it, uploadOwner) }

internal fun FormModel.pruneUploads(values: JsonObject) {
    val files = HashSet<String>()
    for (value in values.values) value.collectFiles(files)
    UploadStore.prune(uploadOwner, files)
}

internal fun FormModel.releaseUploads() {
    UploadStore.release(uploadOwner)
}

internal fun FormModel.attachUploads(cache: File) {
    UploadStore.attach(cache)
    if (restored) adoptUploads()
}

internal fun FormModel.adoptUploads() {
    val files = HashSet<String>()
    for (value in engine.values.values) value.collectFiles(files)
    for (file in files) UploadStore.adopt(file, uploadOwner)
}

internal fun FormSnapshot.withoutDeadUploads(): FormSnapshot = withValues(values.withoutDeadUploads())

private fun JsonObject.withoutDeadUploads(): JsonObject =
    MutableJsonObject().also { copy -> for ((key, value) in this) copy[key] = value.withoutDeadUploads() }

private fun JsonValue.withoutDeadUploads(): JsonValue =
    when (this) {
        is JsonValue.String -> if (UploadStore.isUpload(value) && !UploadStore.exists(value)) JsonValue.Null else this
        is JsonValue.Array -> JsonValue.Array(items.map { it.withoutDeadUploads() })
        is JsonValue.Object -> JsonValue.Object(members.withoutDeadUploads())
        else -> this
    }

private fun JsonValue.collectFiles(files: MutableSet<String>) {
    when (this) {
        is JsonValue.String -> if (value.startsWith("file:")) files += value
        is JsonValue.Array -> items.forEach { it.collectFiles(files) }
        is JsonValue.Object -> members.values.forEach { it.collectFiles(files) }
        else -> Unit
    }
}
