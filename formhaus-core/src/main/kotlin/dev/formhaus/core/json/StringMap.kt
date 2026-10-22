package dev.formhaus.core.json

public typealias JsonObject = StringMap<JsonValue>
public typealias MutableJsonObject = MutableStringMap<JsonValue>
public typealias FieldErrors = StringMap<String>

internal fun arrayIndex(key: String): Long? {
    val length = key.length
    if (length == 0 || length > 10 || (key[0] == '0' && length > 1)) return null
    var number = 0L
    for (digit in key) {
        if (digit !in '0'..'9') return null
        number = number * 10 + (digit - '0')
    }
    return if (number < MAX_ARRAY_INDEX) number else null
}

private const val MAX_ARRAY_INDEX = 4_294_967_295L

public open class StringMap<V : Any> internal constructor(
    private val insertionOrder: Boolean,
) : Iterable<Pair<String, V>> {
    private val order = ArrayList<String>()
    private val storage = HashMap<String, V>()
    private var indexCount = 0

    public val keys: List<String> get() = order.toList()
    public val size: Int get() = order.size

    public fun isEmpty(): Boolean = order.isEmpty()

    public operator fun get(key: String): V? = storage[key]

    public operator fun contains(key: String): Boolean = storage.containsKey(key)

    public val values: List<V> get() = order.map { storage.getValue(it) }

    override fun iterator(): Iterator<Pair<String, V>> = order.asSequence().map { it to storage.getValue(it) }.iterator()

    public fun toMutableStringMap(): MutableStringMap<V> = MutableStringMap<V>(insertionOrder).also { it.copyFrom(this) }

    internal fun merging(other: StringMap<V>): MutableStringMap<V> = toMutableStringMap().also { it.merge(other) }

    internal fun write(
        key: String,
        value: V,
    ) {
        if (storage.put(key, value) == null) insertKey(key)
    }

    internal fun delete(key: String): V? {
        val value = storage.remove(key) ?: return null
        order.remove(key)
        if (!insertionOrder && arrayIndex(key) != null) indexCount -= 1
        return value
    }

    internal fun appendParsed(
        key: String,
        value: V,
    ) {
        if (storage.put(key, value) == null) order.add(key)
    }

    internal fun establishKeyOrder() {
        if (insertionOrder) return
        val indexed = ArrayList<Pair<Long, String>>()
        val named = ArrayList<String>()
        for (key in order) {
            val index = arrayIndex(key)
            if (index != null) indexed.add(index to key) else named.add(key)
        }
        if (indexed.isEmpty()) return
        indexed.sortBy { it.first }
        order.clear()
        indexed.mapTo(order) { it.second }
        order.addAll(named)
        indexCount = indexed.size
    }

    internal fun copyFrom(other: StringMap<V>) {
        order.addAll(other.order)
        storage.putAll(other.storage)
        indexCount = other.indexCount
    }

    private fun insertKey(key: String) {
        val index = if (insertionOrder) null else arrayIndex(key)
        if (index == null) {
            order.add(key)
            return
        }
        var low = 0
        var high = indexCount
        while (low < high) {
            val middle = (low + high) / 2
            val current = arrayIndex(order[middle])
            if (current != null && current < index) low = middle + 1 else high = middle
        }
        order.add(low, key)
        indexCount += 1
    }

    override fun equals(other: Any?): Boolean = other is StringMap<*> && storage == other.storage

    override fun hashCode(): Int = storage.hashCode()

    override fun toString(): String = order.joinToString(", ", "{", "}") { "$it=${storage[it]}" }
}
