package dev.formhaus.compose.model

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.formhaus.core.renderer.TestIdTarget

public sealed interface TestIds {
    public data object Off : TestIds

    public data object Automatic : TestIds

    public class Custom(
        public val name: (TestIdTarget) -> String?,
    ) : TestIds
}

public data class ActionTestIds(
    val submit: String?,
    val next: String?,
    val back: String?,
    val cancel: String?,
)

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
