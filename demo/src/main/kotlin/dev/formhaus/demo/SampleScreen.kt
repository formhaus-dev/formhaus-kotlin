package dev.formhaus.demo

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.formhaus.compose.form.BackPlacement
import dev.formhaus.compose.form.FormBehavior
import dev.formhaus.compose.form.FormhausForm
import dev.formhaus.compose.form.LocalFormBehavior
import dev.formhaus.compose.form.LocalFormComponents
import dev.formhaus.compose.form.TitleDisplayMode
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.FormRendererOptions
import dev.formhaus.compose.model.TestIds
import dev.formhaus.compose.model.back
import dev.formhaus.compose.theme.FormhausTheme
import dev.formhaus.compose.theme.palette
import dev.formhaus.core.engine.SubmitResult
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.toJsonString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SampleScreen(
    sample: Sample,
    onBack: () -> Unit,
) {
    var chosen by rememberSaveable(sample.id) { mutableStateOf(sample.look.name) }
    val look = (Look.all + sample.look).first { it.name == chosen }
    var sent by remember(sample.id) { mutableStateOf<JsonObject?>(null) }
    val model =
        remember(sample.id) {
            FormModel(
                sample.definition,
                engineOptions = sample.engineOptions,
                options = FormRendererOptions(labels = sample.labels, testIds = TestIds.Automatic),
            ) { submitted, _ ->
                delay(SUBMIT_DELAY)
                sent = submitted
                SubmitResult.Ok
            }
        }
    DisposableEffect(model) { onDispose { model.close() } }
    val values = sent
    SampleScaffold(sample.definition.title ?: sample.title, look, model.takeIf { values == null }, onBack, { chosen = it.name }) {
        if (values == null) FormhausForm(model) else Text(values.toJsonString())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleScaffold(
    title: String,
    look: Look,
    model: FormModel?,
    onBack: () -> Unit,
    onLook: (Look) -> Unit,
    insets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable () -> Unit,
) {
    val palette = look.theme.palette(isSystemInDarkTheme(), increasedContrast = false)
    val material = look === Look.material
    val background = if (material) MaterialTheme.colorScheme.surface else palette.background
    val foreground = if (material) MaterialTheme.colorScheme.onSurface else palette.text
    val scope = rememberCoroutineScope()
    val stepBack = model?.takeIf { look.appBarBack && it.actions.showBack }
    FormhausTheme(look.theme) {
        CompositionLocalProvider(
            LocalFormComponents provides look.components,
            LocalFormBehavior provides
                FormBehavior(
                    titleDisplayMode = TitleDisplayMode.OFF,
                    backPlacement = if (look.appBarBack) BackPlacement.APP_BAR else BackPlacement.AUTOMATIC,
                ),
        ) {
            Scaffold(
                containerColor = background,
                contentWindowInsets = insets,
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            IconButton(
                                { if (stepBack == null) onBack() else scope.launch { stepBack.back() } },
                                enabled = stepBack?.disabled != true,
                            ) { Icon(DemoIcons.arrowBack, stepBack?.actions?.backLabel ?: "Back") }
                        },
                        actions = { LookMenu(look, onLook) },
                        windowInsets = insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = background,
                                titleContentColor = foreground,
                                navigationIconContentColor = foreground,
                                actionIconContentColor = foreground,
                            ),
                    )
                },
            ) { padding -> Box(Modifier.padding(padding).fillMaxSize()) { content() } }
        }
    }
}

@Composable
private fun LookMenu(
    look: Look,
    onLook: (Look) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }) { Icon(DemoIcons.moreVert, "Look") }
        DropdownMenu(open, { open = false }) {
            for (option in Look.all) {
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        open = false
                        onLook(option)
                    },
                    trailingIcon = if (option === look) ({ Icon(DemoIcons.check, null) }) else null,
                )
            }
        }
    }
}

private const val SUBMIT_DELAY = 600L
