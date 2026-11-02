package dev.formhaus.core.engine

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.model.FormField
import dev.formhaus.core.visibility.itemsOf

internal class Restore(
    val keys: List<String>,
    val seeds: List<ValueSlot>,
    val memory: List<ValueSlot?>,
)

private class WriteWatch(
    val id: Int,
    val keys: MutableSet<String> = HashSet(),
)

internal class StartValues {
    private var seeds = Values()
    private var stored: JsonObject? = null
    private var remembering = true
    private var writeWatch: WriteWatch? = null
    private val memory = LinkedHashMap<String, ValueSlot>()
    private val itemSeeds = HashMap<Int, JsonObject>()
    private val itemMemory = HashMap<Int, MutableJsonObject>()
    private val itemHidden = HashMap<Int, MutableSet<String>>()
    private var groups: List<FormField> = emptyList()
    var itemEpoch = 0
        private set

    private fun forgetItems() {
        itemSeeds.clear()
        itemMemory.clear()
        itemHidden.clear()
        itemEpoch += 1
    }

    fun seedItems(slot: ValueSlot) {
        itemEpoch += 1
        val ids = slot.ref?.items.orEmpty()
        itemsOf(slot.value).forEachIndexed { index, item ->
            val entry = item.objectValue ?: return@forEachIndexed
            if (index < ids.size) itemSeeds.putIfAbsent(ids[index], entry)
        }
    }

    fun resetStarts(
        values: Values,
        groups: List<FormField>,
    ) {
        seeds = values.copy()
        this.groups = groups
        memory.clear()
        forgetItems()
        for (group in groups) seedItems(values.slot(group.key))
    }

    fun storeValues(values: JsonObject) {
        stored = values
    }

    fun previousValues(): JsonObject = stored ?: seeds.json

    fun loadPayload() {
        remembering = false
        forgetItems()
        for (key in seeds.keys) memory[key] = ValueSlot.EMPTY
    }

    fun remember(
        key: String,
        slot: ValueSlot,
        written: Boolean = false,
    ) {
        if (!remembering) return
        memory[key] = slot
        if (!written) return
        writeWatch?.keys?.add(key)
        val group = groups.firstOrNull { it.key == key } ?: return
        val ids = slot.ref?.items.orEmpty()
        itemsOf(slot.value).forEachIndexed { index, item ->
            val entry = item.objectValue ?: return@forEachIndexed
            val id = ids.getOrNull(index) ?: return@forEachIndexed
            if (id !in itemSeeds) itemSeeds[id] = writtenSeed(group, entry)
        }
    }

    private fun writtenSeed(
        group: FormField,
        entry: JsonObject,
    ): JsonObject {
        val seed = MutableJsonObject()
        for (field in group.items) {
            val value = field.defaultValue
            if (value == null) seed.remove(field.key) else seed[field.key] = value
        }
        for ((name, value) in entry) seed[name] = value
        return seed
    }

    fun watchWrites(): Int = freshToken().also { writeWatch = WriteWatch(it) }

    fun endWatch(id: Int): Set<String> {
        val watch = writeWatch?.takeIf { it.id == id } ?: return emptySet()
        writeWatch = null
        return watch.keys
    }

    fun rememberItem(
        id: Int?,
        key: String,
        value: JsonValue,
    ) {
        if (!remembering || id == null) return
        itemMemory.getOrPut(id) { MutableJsonObject() }[key] = value
    }

    fun dropItem(id: Int?) {
        if (id == null) return
        itemSeeds.remove(id)
        itemMemory.remove(id)
        itemHidden.remove(id)
    }

    fun hideItem(
        id: Int?,
        key: String,
    ) {
        if (id != null) itemHidden.getOrPut(id, ::HashSet).add(key)
    }

    fun revealItem(
        id: Int?,
        key: String,
    ): Boolean = id != null && itemHidden[id]?.remove(key) == true

    fun reseed(
        keys: List<String>,
        values: Values,
    ): Restore {
        val previous = Restore(keys, keys.map(seeds::slot), keys.map(memory::get))
        for (key in keys) {
            memory.remove(key)
            seeds.set(key, values.slot(key))
            seedItems(values.slot(key))
        }
        return previous
    }

    fun restore(
        previous: Restore,
        kept: Set<String>,
    ) {
        previous.keys.forEachIndexed { index, key ->
            seeds.set(key, previous.seeds[index])
            if (key in kept) return@forEachIndexed
            val remembered = previous.memory[index]
            if (remembered != null) memory[key] = remembered else memory.remove(key)
        }
    }

    fun startValue(field: FormField): ValueSlot {
        if (field.type != "hidden") memory[field.key]?.let { return it }
        return seeds.slot(field.key)
    }

    fun itemStartValue(
        id: Int?,
        field: FormField,
    ): JsonValue? {
        if (id == null) return null
        val key = field.key
        if (field.type != "hidden") itemMemory[id]?.let { remembered -> if (key in remembered) return remembered[key] }
        return itemSeeds[id]?.get(key)
    }
}
