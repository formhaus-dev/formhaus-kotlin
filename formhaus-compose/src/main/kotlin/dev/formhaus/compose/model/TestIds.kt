package dev.formhaus.compose.model

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.formhaus.core.json.ValueParts
import dev.formhaus.core.json.describe
import dev.formhaus.core.json.equalParts
import dev.formhaus.core.json.partsHash
import dev.formhaus.core.renderer.TestIdTarget

public sealed interface TestIds {
    public data object Off : TestIds

    public data object Automatic : TestIds

    public class Custom(
        public val name: (TestIdTarget) -> String?,
    ) : TestIds
}

public class ActionTestIds internal constructor(
    public val submit: String?,
    public val next: String?,
    public val back: String?,
    public val cancel: String?,
) {
    override fun equals(other: Any?): Boolean = equalParts(other, ActionTestIds::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("ActionTestIds")

    private fun parts(): ValueParts = listOf("submit" to submit, "next" to next, "back" to back, "cancel" to cancel)
}

internal fun FormModel.testId(
    kind: TestIdTarget.Kind,
    key: String? = null,
    value: String? = null,
): String? {
    val target = TestIdTarget(kind, engine.definition.id, key, value)
    return when (val ids = options.testIds) {
        TestIds.Off -> null
        TestIds.Automatic -> target.defaultId
        is TestIds.Custom -> ids.name(target)
    }
}

internal val FormModel.actionTestIds: ActionTestIds
    get() =
        ActionTestIds(
            submit = testId(TestIdTarget.Kind.SUBMIT),
            next = testId(TestIdTarget.Kind.NEXT),
            back = testId(TestIdTarget.Kind.BACK),
            cancel = testId(TestIdTarget.Kind.CANCEL),
        )

internal fun Modifier.formhausTestId(id: String?): Modifier = if (id == null) this else testTag(id)
