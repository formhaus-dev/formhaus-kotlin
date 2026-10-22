package dev.formhaus.core.renderer

public data class TestIdTarget(
    public val kind: Kind,
    public val definitionId: String,
    public val key: String? = null,
    public val value: String? = null,
) {
    public enum class Kind(
        public val rawValue: String,
    ) {
        FORM("form"),
        FIELD("field"),
        OPTION("option"),
        SUBMIT("submit"),
        NEXT("next"),
        BACK("back"),
        CANCEL("cancel"),
        SUMMARY("summary"),
    }

    public val defaultId: String get() = "$definitionId.${key ?: kind.rawValue}" + (value?.let { ".$it" } ?: "")
}
