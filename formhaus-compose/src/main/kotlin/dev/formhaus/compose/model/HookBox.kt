package dev.formhaus.compose.model

import dev.formhaus.compose.platform.IcuRecallFormatter
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.engine.ValidateFn
import dev.formhaus.core.validation.MessageSource

internal class HookBox(
    var current: FormEngineOptions,
) {
    private val forwardValidate: ValidateFn = { values, context -> current.onValidate?.invoke(values, context) }

    val onValidate: ValidateFn? get() = forwardValidate.takeIf { current.onValidate != null }

    fun forwarding(
        renderer: FormRendererOptions,
        messages: MessageSource?,
        debuggable: Boolean,
    ): FormEngineOptions {
        val options = current
        return FormEngineOptions(
            onBeforeStepChange = { current.onBeforeStepChange?.invoke(it) },
            onAfterStepChange = { current.onAfterStepChange?.invoke(it) },
            onBeforeSubmit = { current.onBeforeSubmit?.invoke(it) },
            onAfterSubmit = { values, errors -> current.onAfterSubmit?.invoke(values, errors) },
            onStepValidate = { step, values -> current.onStepValidate?.invoke(step, values) },
            onFormEvent = options.onFormEvent?.let { { event -> current.onFormEvent?.invoke(event) } },
            onWarning = options.onWarning,
            validators = options.validators,
            messages = options.messages ?: messages,
            lang = options.lang ?: renderer.lang,
            formatRecall = options.formatRecall ?: renderer.formatRecall,
            recallFormatter = options.recallFormatter ?: IcuRecallFormatter,
            seed = options.seed,
            research = options.research,
            hiddenValues = options.hiddenValues,
            debugChecks = options.debugChecks ?: debuggable,
            locale = options.locale,
            onValidate = onValidate,
        )
    }
}
