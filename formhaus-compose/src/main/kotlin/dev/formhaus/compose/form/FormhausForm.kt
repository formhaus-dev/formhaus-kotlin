package dev.formhaus.compose.form

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.formhaus.compose.containers.FormActionsSlot
import dev.formhaus.compose.containers.FormErrorSummary
import dev.formhaus.compose.containers.FormProgress
import dev.formhaus.compose.containers.FormTopLevelErrors
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.FormRendererOptions
import dev.formhaus.compose.model.SnapshotChangeFn
import dev.formhaus.compose.model.formhausTestId
import dev.formhaus.compose.model.testId
import dev.formhaus.compose.system.SystemForm
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.ResolveFormhausTokens
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.engine.FormSnapshot
import dev.formhaus.core.engine.SubmitHandler
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.renderer.TestIdTarget

@Composable
public fun FormhausForm(
    definition: FormDefinition,
    modifier: Modifier = Modifier,
    initialValues: JsonObject = MutableJsonObject(),
    engineOptions: FormEngineOptions = FormEngineOptions(),
    renderer: FormRendererOptions = FormRendererOptions(),
    restoring: FormSnapshot? = null,
    onSnapshotChange: SnapshotChangeFn? = null,
    onSubmit: SubmitHandler,
) {
    val model = rememberOwnedModel(definition, initialValues, engineOptions, renderer, restoring, onSnapshotChange, onSubmit)
    FormhausForm(model, modifier)
}

@Composable
public fun FormhausForm(
    model: FormModel,
    modifier: Modifier = Modifier,
) {
    key(model) {
        ResolveFormhausTokens(LocalFormComponents.current.system) { FormScroller(model, modifier) }
    }
}

@Composable
private fun FormScroller(
    model: FormModel,
    modifier: Modifier,
) {
    val tokens = LocalFormhausTokens.current
    val scroll = rememberScrollState()
    val top = remember { TopRequests() }
    LaunchedEffect(top.issued) {
        if (top.issued == 0) return@LaunchedEffect
        withFrameNanos {}
        if (tokens.reduceMotion) scroll.scrollTo(0) else scroll.animateScrollTo(0, tween(tokens.motion.progress))
    }
    Column(
        modifier
            .fillMaxSize()
            .background(tokens.colors.background)
            .verticalScroll(scroll)
            .formhausTestId(model.testId(TestIdTarget.Kind.FORM)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompositionLocalProvider(LocalTopRequests provides top) {
            FormhausFormContent(
                model,
                Modifier
                    .widthIn(max = tokens.controls.maxWidth)
                    .padding(tokens.spacing.s10),
            )
        }
    }
}

@Composable
public fun FormhausFormContent(
    model: FormModel,
    modifier: Modifier = Modifier,
) {
    val components = LocalFormComponents.current
    val anchors = remember(model) { ScrollAnchors() }
    LaunchedEffect(model) { model.start() }
    ResolveFormhausTokens(components.system) {
        FormLocale(model.definition.lang) {
            CompositionLocalProvider(LocalScrollAnchors provides anchors) {
                if (components.system) {
                    SystemForm(model, modifier)
                } else {
                    FormStack(model, modifier)
                }
                StepScroller(model, anchors)
                InvalidFeedback(model)
                Announcer(model)
            }
        }
    }
}

@Composable
private fun FormStack(
    model: FormModel,
    modifier: Modifier,
) {
    val tokens = LocalFormhausTokens.current
    val behavior = LocalFormBehavior.current
    Column(
        modifier.fillMaxWidth().formhausScrollAnchor(FORM_TOP_ANCHOR),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.gap),
    ) {
        FormProgress(model)
        FormFields(model)
        FormTopLevelErrors(model)
        if (behavior.errorSummary) FormErrorSummary(model)
        FormActionsSlot(model)
    }
}
