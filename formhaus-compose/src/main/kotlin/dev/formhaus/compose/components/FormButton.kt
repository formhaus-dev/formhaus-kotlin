package dev.formhaus.compose.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.theme.FormhausTokens
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle
import dev.formhaus.core.model.FormAction

@Composable
public fun FormButton(
    label: String,
    variant: FormButtonVariant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    compact: Boolean = false,
    icon: ImageVector? = null,
) {
    DescribedButton(label, variant, onClick, modifier, enabled, busy, compact, icon, null)
}

@Composable
internal fun DescribedButton(
    label: String,
    variant: FormButtonVariant,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    busy: Boolean,
    compact: Boolean,
    icon: ImageVector?,
    description: String?,
) {
    val tokens = LocalFormhausTokens.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val look = ButtonLook(tokens, variant, enabled, busy, compact, (hovered || pressed) && enabled)
    val foreground by animateColorAsState(look.foreground, tokens.quick(), label = "foreground")
    val labelAlpha by animateFloatAsState(if (busy) 0f else 1f, tokens.quick(), label = "busy")
    Box(
        modifier
            .scale(if (pressed && !tokens.reduceMotion) 0.98f else 1f)
            .alpha(if (enabled || variant == FormButtonVariant.PRIMARY) 1f else 0.5f)
            .heightIn(min = if (compact) tokens.controls.hitTarget else tokens.controls.height)
            .surface(tokens.controlShape, look.fill, look.border)
            .clickable(source, null, enabled && !busy, role = Role.Button, onClick = onClick)
            .padding(horizontal = look.padding)
            .described(description),
        contentAlignment = Alignment.Center,
    ) {
        val role = if (variant == FormButtonVariant.TEXT || compact) TextRole.textButton else TextRole.button
        Row(
            Modifier.alpha(labelAlpha).hidden(description != null),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, null, Modifier.size(tokens.typography.body.value.dp), tint = foreground)
            Text(label, color = foreground, maxLines = 1, overflow = TextOverflow.Ellipsis, style = tokens.textStyle(role, colored = false))
        }
        if (busy) CircularProgressIndicator(Modifier.padding(2.dp).heightIn(max = tokens.controls.indicator), foreground, 2.dp)
    }
}

private fun Modifier.described(description: String?): Modifier =
    if (description == null) this else semantics { contentDescription = description }

private fun Modifier.hidden(hide: Boolean): Modifier = if (hide) clearAndSetSemantics {} else this

private class ButtonLook(
    tokens: FormhausTokens,
    variant: FormButtonVariant,
    enabled: Boolean,
    busy: Boolean,
    compact: Boolean,
    active: Boolean,
) {
    private val colors = tokens.colors
    private val live = enabled || busy

    val padding =
        when {
            variant == FormButtonVariant.TEXT || (variant == FormButtonVariant.DANGER && compact) -> tokens.spacing.s4
            compact -> tokens.spacing.s7
            else -> tokens.spacing.s9
        }

    val foreground: Color =
        when (variant) {
            FormButtonVariant.PRIMARY -> if (live) colors.onPrimary else colors.primaryOnSoft
            FormButtonVariant.SECONDARY -> colors.text
            FormButtonVariant.TEXT -> if (active) colors.text else colors.textMuted
            FormButtonVariant.DANGER -> if (active) colors.errorHover else colors.error
        }

    val fill: Color =
        when (variant) {
            FormButtonVariant.PRIMARY -> {
                if (!live) {
                    colors.primaryDisabled
                } else if (active) {
                    colors.primaryHover
                } else {
                    colors.primary
                }
            }

            FormButtonVariant.SECONDARY -> {
                if (active) colors.backgroundHover else colors.background
            }

            FormButtonVariant.TEXT -> {
                if (active) colors.backgroundHover else Color.Transparent
            }

            FormButtonVariant.DANGER -> {
                if (active) colors.errorSoft else Color.Transparent
            }
        }

    val border: Color =
        when (variant) {
            FormButtonVariant.PRIMARY, FormButtonVariant.TEXT -> Color.Transparent
            FormButtonVariant.SECONDARY -> if (active) colors.borderHover else colors.secondaryBorder
            FormButtonVariant.DANGER -> if (compact) Color.Transparent else colors.error
        }
}

public enum class FormButtonVariant(
    public val rawValue: String,
) {
    PRIMARY("primary"),
    SECONDARY("secondary"),
    TEXT("text"),
    DANGER("danger"),
    ;

    public companion object {
        public fun of(
            action: FormAction?,
            fallback: FormButtonVariant,
        ): FormButtonVariant = entries.firstOrNull { it.rawValue == action?.variant } ?: fallback
    }
}
