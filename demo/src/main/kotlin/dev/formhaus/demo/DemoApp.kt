package dev.formhaus.demo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun DemoApp() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        var selected by rememberSaveable { mutableStateOf<String?>(null) }
        val sample = samples.firstOrNull { it.id == selected }
        BackHandler(enabled = sample != null) { selected = null }
        if (sample == null) {
            Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) { SampleList { selected = it.id } }
            }
        } else {
            SampleScreen(sample) { selected = null }
        }
    }
}

@Composable
private fun SampleList(onOpen: (Sample) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(samples, key = { it.id }) { sample ->
            ListItem(
                headlineContent = { Text(sample.title) },
                supportingContent = { Text(sample.summary) },
                modifier = Modifier.testTag("sample.${sample.id}").clickable { onOpen(sample) },
            )
            HorizontalDivider()
        }
    }
}
