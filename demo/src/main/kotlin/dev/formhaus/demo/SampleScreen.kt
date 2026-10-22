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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.formhaus.compose.form.FormBehavior
import dev.formhaus.compose.form.FormhausForm
import dev.formhaus.compose.form.LocalFormBehavior
import dev.formhaus.compose.form.LocalFormComponents
import dev.formhaus.compose.form.TitleDisplayMode
import dev.formhaus.compose.model.FormRendererOptions
import dev.formhaus.compose.model.TestIds
import dev.formhaus.compose.theme.FormhausTheme
import dev.formhaus.compose.theme.palette
import dev.formhaus.core.engine.SubmitResult
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.toJsonString
import kotlinx.coroutines.delay

@Composable
fun SampleScreen(
    sample: Sample,
    onBack: () -> Unit,
) {
    var chosen by rememberSaveable(sample.id) { mutableStateOf(sample.look.name) }
    val look = (Look.all + sample.look).first { it.name == chosen }
    SampleScaffold(sample.definition.title ?: sample.title, look, onBack, { chosen = it.name }) { SampleForm(sample) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleScaffold(
    title: String,
    look: Look,
    onBack: () -> Unit,
    onLook: (Look) -> Unit,
    insets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable () -> Unit,
) {
    val palette = look.theme.palette(isSystemInDarkTheme(), increasedContrast = false)
    val material = look === Look.material
    val background = if (material) MaterialTheme.colorScheme.surface else palette.background
    val foreground = if (material) MaterialTheme.colorScheme.onSurface else palette.text
    FormhausTheme(look.theme) {
        CompositionLocalProvider(
            LocalFormComponents provides look.components,
            LocalFormBehavior provides FormBehavior(titleDisplayMode = TitleDisplayMode.OFF),
        ) {
            Scaffold(
                containerColor = background,
                contentWindowInsets = insets,
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = { IconButton(onBack) { Icon(DemoIcons.arrowBack, "Back") } },
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

@Composable
private fun SampleForm(sample: Sample) {
    var sent by remember(sample.id) { mutableStateOf<JsonObject?>(null) }
    val values = sent
    if (values != null) {
        Text(values.toJsonString())
        return
    }
    FormhausForm(
        definition = sample.definition,
        engineOptions = sample.engineOptions,
        renderer = FormRendererOptions(labels = sample.labels, testIds = TestIds.Automatic),
    ) { submitted, _ ->
        delay(SUBMIT_DELAY)
        sent = submitted
        SubmitResult.Ok
    }
}

private const val SUBMIT_DELAY = 600L
