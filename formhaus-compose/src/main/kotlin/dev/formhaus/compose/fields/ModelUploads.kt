package dev.formhaus.compose.fields

import dev.formhaus.compose.model.FormModel
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.renderer.fileItems
import dev.formhaus.core.renderer.value
import java.io.File

public fun FormModel.pickedFile(key: String): File? = engine.value(key)?.stringValue?.let { UploadStore.owned(it, uploadOwner) }

public fun FormModel.pickedFiles(key: String): List<File> = ownedFiles(engine.value(key)).map { it.file }

internal class PickedFile(
    val index: Int,
    val value: JsonValue,
    val file: File,
)

internal fun FormModel.ownedFiles(value: JsonValue?): List<PickedFile> =
    fileItems(value).withIndex().mapNotNull { (index, item) ->
        item.stringValue?.let { UploadStore.owned(it, uploadOwner) }?.let { PickedFile(index, item, it) }
    }

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
    val dead = engine.values.mapNotNull { (key, value) -> value.withoutDeadUploads()?.let { key to it } }
    for ((key, value) in dead) engine.setValue(key, value)
    val files = HashSet<String>()
    for (value in engine.values.values) value.collectFiles(files)
    for (file in files) UploadStore.adopt(file, uploadOwner)
}

private fun JsonValue.withoutDeadUploads(): JsonValue? =
    when {
        isDeadUpload() -> JsonValue.Null
        this is JsonValue.Array && items.any { it.isDeadUpload() } -> JsonValue.Array(items.filterNot { it.isDeadUpload() })
        else -> null
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
