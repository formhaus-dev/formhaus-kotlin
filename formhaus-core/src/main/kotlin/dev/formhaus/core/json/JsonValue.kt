package dev.formhaus.core.json

public sealed class JsonValue {
    public data object Null : JsonValue()

    public data class Bool(
        public val value: Boolean,
    ) : JsonValue()

    public data class Number(
        public val value: Double,
    ) : JsonValue() {
        public constructor(value: Int) : this(value.toDouble())
    }

    public data class String(
        public val value: kotlin.String,
    ) : JsonValue()

    public data class Array(
        public val items: List<JsonValue>,
    ) : JsonValue()

    public data class Object(
        public val members: JsonObject,
    ) : JsonValue()

    public val stringValue: kotlin.String? get() = (this as? String)?.value
    public val numberValue: Double? get() = (this as? Number)?.value
    public val boolValue: Boolean? get() = (this as? Bool)?.value
    public val arrayValue: List<JsonValue>? get() = (this as? Array)?.items
    public val objectValue: JsonObject? get() = (this as? Object)?.members
    public val isNull: Boolean get() = this === Null

    public operator fun get(key: kotlin.String): JsonValue? = objectValue?.get(key)

    internal val isTruthy: Boolean
        get() =
            when (this) {
                Null -> false
                is Bool -> value
                is Number -> value != 0.0 && !value.isNaN()
                is String -> value.isNotEmpty()
                is Array, is Object -> true
            }

    internal val typeName: kotlin.String
        get() =
            when (this) {
                Null, is Array, is Object -> "object"
                is Bool -> "boolean"
                is Number -> "number"
                is String -> "string"
            }

    internal val isInteger: Boolean get() = numberValue?.isInteger == true

    internal val isFiniteNumber: Boolean get() = numberValue?.isFinite() == true

    final override fun toString(): kotlin.String = jsonStringify(this)

    public companion object {
        public const val MAX_NESTING_DEPTH: Int = 512

        public fun parse(text: kotlin.String): JsonValue = parse(utf8Bytes(text))

        public fun parse(bytes: ByteArray): JsonValue = JsonParser(bytes).parseDocument()
    }
}

internal val Double.isInteger: Boolean get() = isFinite() && kotlin.math.truncate(this) == this

internal fun strictEquals(
    lhs: JsonValue?,
    rhs: JsonValue?,
): Boolean =
    when {
        lhs == null || rhs == null -> lhs == null && rhs == null
        lhs is JsonValue.Number && rhs is JsonValue.Number -> lhs.value == rhs.value
        lhs is JsonValue.Array || lhs is JsonValue.Object -> false
        else -> lhs == rhs
    }

internal fun sameValueZero(
    lhs: JsonValue?,
    rhs: JsonValue?,
): Boolean = strictEquals(lhs, rhs)
