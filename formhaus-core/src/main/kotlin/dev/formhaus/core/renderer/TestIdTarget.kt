package dev.formhaus.core.renderer

import dev.formhaus.core.json.ValueParts
import dev.formhaus.core.json.describe
import dev.formhaus.core.json.equalParts
import dev.formhaus.core.json.partsHash

public class TestIdTarget(
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

    public fun copy(
        kind: Kind = this.kind,
        definitionId: String = this.definitionId,
        key: String? = this.key,
        value: String? = this.value,
    ): TestIdTarget = TestIdTarget(kind, definitionId, key, value)

    override fun equals(other: Any?): Boolean = equalParts(other, TestIdTarget::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("TestIdTarget")

    private fun parts(): ValueParts = listOf("kind" to kind, "definitionId" to definitionId, "key" to key, "value" to value)
}
