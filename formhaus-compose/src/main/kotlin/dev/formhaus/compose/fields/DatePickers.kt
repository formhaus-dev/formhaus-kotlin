package dev.formhaus.compose.fields

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import dev.formhaus.compose.components.FormButton
import dev.formhaus.compose.components.FormButtonVariant
import dev.formhaus.compose.components.controlShape
import dev.formhaus.compose.theme.LocalFormhausTokens
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

@Composable
internal fun DatePickerPanel(
    kind: DateKind,
    initial: LocalDateTime,
    done: String,
    dismiss: () -> Unit,
    pick: (LocalDateTime) -> Unit,
) {
    var day by remember { mutableStateOf<LocalDate?>(if (kind == DateKind.TIME) initial.toLocalDate() else null) }
    val chosen = day
    if (chosen == null) {
        DayPicker(initial.toLocalDate(), done, dismiss) { picked ->
            if (kind == DateKind.DATE) pick(picked.atStartOfDay()) else day = picked
        }
    } else {
        TimePickerPanel(initial, done, dismiss) { hour, minute -> pick(chosen.atTime(hour, minute)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPicker(
    initial: LocalDate,
    done: String,
    dismiss: () -> Unit,
    pick: (LocalDate) -> Unit,
) {
    val tokens = LocalFormhausTokens.current
    val start = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = start)
    val selected = state.selectedDateMillis
    val day = selected?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
    LaunchedEffect(selected) { if (selected != null && selected != start && day != null) pick(day) }
    DatePickerDialog(
        dismiss,
        { FormButton(done, FormButtonVariant.PRIMARY, { pick(day ?: initial) }, compact = true) },
        colors = DatePickerDefaults.colors(containerColor = tokens.colors.background),
    ) {
        DatePicker(state, colors = DatePickerDefaults.colors(containerColor = tokens.colors.background))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerPanel(
    initial: LocalDateTime,
    done: String,
    dismiss: () -> Unit,
    pick: (Int, Int) -> Unit,
) {
    val tokens = LocalFormhausTokens.current
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    Dialog(dismiss) {
        Column(
            Modifier
                .background(tokens.colors.background, tokens.controlShape)
                .padding(tokens.spacing.s7),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.s5),
            horizontalAlignment = Alignment.End,
        ) {
            TimePicker(state)
            FormButton(done, FormButtonVariant.PRIMARY, { pick(state.hour, state.minute) }, compact = true)
        }
    }
}
