package dev.formhaus.demo.kiln

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.form.ActionsContext
import dev.formhaus.compose.form.ProgressContext
import dev.formhaus.compose.form.SectionContext
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.demo.DemoIcons

private const val SECTION_TINT = 0.6f

@Composable
internal fun KilnStepHeader(context: ProgressContext) {
    val colors = LocalFormhausTokens.current.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (step in 1..maxOf(1, context.total)) {
                Box(
                    Modifier.weight(1f).height(6.dp).background(
                        if (step <=
                            context.current
                        ) {
                            colors.primary
                        } else {
                            colors.border
                        },
                        CircleShape,
                    ),
                )
            }
        }
        Text(context.counterLabel.uppercase(), Modifier.padding(top = 6.dp), color = colors.primary, style = KilnType.eyebrow)
        context.stepTitle?.let { Text(it, Modifier.semantics { heading() }, color = colors.text, style = KilnType.title) }
        context.stepDescription?.let { Text(it, color = colors.textMuted, style = KilnType.body) }
    }
}

@Composable
internal fun KilnSection(context: SectionContext) {
    val colors = LocalFormhausTokens.current.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.backgroundDisabled.copy(alpha = SECTION_TINT), RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(context.title, Modifier.semantics { heading() }, color = colors.text, style = KilnType.heading)
            context.description?.let { Text(it, color = colors.textMuted, style = KilnType.note) }
        }
        context.content()
    }
}

@Composable
internal fun KilnActions(context: ActionsContext) {
    val colors = LocalFormhausTokens.current.colors
    val state = context.state
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.showBack) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, colors.border, CircleShape)
                    .clickable(enabled = !context.busy, onClickLabel = state.backLabel, role = Role.Button) { context.back() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(DemoIcons.arrowBack, state.backLabel, Modifier.size(22.dp), tint = colors.text)
            }
        }
        if (state.showPrimary) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(CircleShape)
                    .background(colors.primary)
                    .clickable(enabled = !context.busy && !state.primaryDisabled, role = Role.Button) { context.performPrimaryAction() },
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(state.primaryLabel, color = colors.onPrimary, style = KilnType.body.copy(fontWeight = FontWeight.SemiBold))
                if (context.busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = colors.onPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(DemoIcons.arrowForward, null, Modifier.size(20.dp), tint = colors.onPrimary)
                }
            }
        }
    }
}
