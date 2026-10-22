package dev.formhaus.compose.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

public typealias Slot<Context> = @Composable (Context) -> Unit

@Immutable
internal data class ContainerSlots(
    val section: Slot<SectionContext>? = null,
    val group: Slot<GroupContext>? = null,
    val groupItem: Slot<GroupItemContext>? = null,
    val confirm: Slot<ConfirmContext>? = null,
    val progress: Slot<ProgressContext>? = null,
    val actions: Slot<ActionsContext>? = null,
)

@Immutable
public class FormComponents private constructor(
    internal val fields: Map<String, Slot<FieldContext>>,
    internal val slots: ContainerSlots,
    internal val builtIns: Boolean,
    internal val system: Boolean,
) {
    public fun field(
        type: String,
        content: Slot<FieldContext>,
    ): FormComponents = FormComponents(fields + (type to content), slots, builtIns, system)

    public fun section(content: Slot<SectionContext>): FormComponents = with(slots.copy(section = content))

    public fun group(content: Slot<GroupContext>): FormComponents = with(slots.copy(group = content))

    public fun groupItem(content: Slot<GroupItemContext>): FormComponents = with(slots.copy(groupItem = content))

    public fun confirmDialog(content: Slot<ConfirmContext>): FormComponents = with(slots.copy(confirm = content))

    public fun progress(content: Slot<ProgressContext>): FormComponents = with(slots.copy(progress = content))

    public fun actions(content: Slot<ActionsContext>): FormComponents = with(slots.copy(actions = content))

    private fun with(slots: ContainerSlots): FormComponents = FormComponents(fields, slots, builtIns, system)

    public companion object {
        public val standard: FormComponents = FormComponents(emptyMap(), ContainerSlots(), builtIns = true, system = false)
        public val system: FormComponents = FormComponents(emptyMap(), ContainerSlots(), builtIns = true, system = true)
        public val headless: FormComponents = FormComponents(emptyMap(), ContainerSlots(), builtIns = false, system = false)
    }
}

public val LocalFormComponents: ProvidableCompositionLocal<FormComponents> = staticCompositionLocalOf { FormComponents.standard }
