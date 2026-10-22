package dev.formhaus.compose.fields

import dev.formhaus.compose.model.FormModel
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
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
    reconcileUploads()
}

internal fun FormModel.reconcileUploads() {
    val dead = engine.values.mapNotNull { (key, value) -> key.takeIf { value.isDeadUpload() } }
    for (key in dead) engine.setValue(key, JsonValue.Null)
    val files = HashSet<String>()
    for (value in engine.values.values) value.collectFiles(files)
    for (file in files) UploadStore.adopt(file, uploadOwner)
}

private fun JsonValue.isDeadUpload(): Boolean = this is JsonValue.String && UploadStore.isUpload(value) && !UploadStore.exists(value)

private fun JsonValue.collectFiles(files: MutableSet<String>) {
    when (this) {
        is JsonValue.String -> if (value.startsWith("file:")) files += value
        is JsonValue.Array -> items.forEach { it.collectFiles(files) }
        is JsonValue.Object -> members.values.forEach { it.collectFiles(files) }
        else -> Unit
    }
}
