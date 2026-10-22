package dev.formhaus.demo

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.formhaus.compose.form.FormhausForm
import dev.formhaus.compose.form.LocalFormComponents
import dev.formhaus.compose.model.FormRendererOptions
import dev.formhaus.compose.model.TestIds
import dev.formhaus.core.engine.SubmitResult
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.toJsonString
import kotlinx.coroutines.delay

@Composable
fun SampleScreen(sample: Sample) {
    var sent by remember(sample.id) { mutableStateOf<JsonObject?>(null) }
    val values = sent
    if (values != null) {
        Text(values.toJsonString())
        return
    }
    CompositionLocalProvider(LocalFormComponents provides sample.components) {
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
}

private const val SUBMIT_DELAY = 600L
