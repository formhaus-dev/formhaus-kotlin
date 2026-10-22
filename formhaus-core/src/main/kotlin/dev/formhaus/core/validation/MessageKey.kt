package dev.formhaus.core.validation

@JvmInline
public value class MessageKey(public val rawValue: String) {
    override fun toString(): String = rawValue

    public companion object {
        public val REQUIRED: MessageKey = MessageKey("required")
        public val MIN_LENGTH: MessageKey = MessageKey("minLength")
        public val MAX_LENGTH: MessageKey = MessageKey("maxLength")
        public val MIN_ITEMS: MessageKey = MessageKey("minItems")
        public val MAX_ITEMS: MessageKey = MessageKey("maxItems")
        public val MIN: MessageKey = MessageKey("min")
        public val MAX: MessageKey = MessageKey("max")
        public val PATTERN: MessageKey = MessageKey("pattern")
        public val MATCH_FIELD: MessageKey = MessageKey("matchField")
        public val EMAIL: MessageKey = MessageKey("email")
        public val NUMBER: MessageKey = MessageKey("number")
        public val DATE: MessageKey = MessageKey("date")
        public val DATETIME: MessageKey = MessageKey("datetime")
        public val TIME: MessageKey = MessageKey("time")
        public val OPTION: MessageKey = MessageKey("option")
        public val OPTIONS: MessageKey = MessageKey("options")
        public val EXCLUSIVE: MessageKey = MessageKey("exclusive")
        public val BOOLEAN: MessageKey = MessageKey("boolean")
        public val STRING: MessageKey = MessageKey("string")
        public val FILE: MessageKey = MessageKey("file")
        public val VALUE: MessageKey = MessageKey("value")
        public val HIDDEN: MessageKey = MessageKey("hidden")
    }
}
