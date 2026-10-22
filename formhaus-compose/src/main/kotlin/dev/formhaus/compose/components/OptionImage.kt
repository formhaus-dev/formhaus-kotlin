package dev.formhaus.compose.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.core.model.FieldOption
import dev.formhaus.core.renderer.safeImage

private const val PICTURE_RATIO = 4f / 3f

@Composable
public fun FormhausOptionImage(
    option: FieldOption,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val source = option.safeImage.orEmpty()
    val image = OptionImages.shown(source)
    Box(modifier.clearAndSetSemantics {}) {
        if (image != null) Image(image, null, Modifier.fillMaxWidth(), contentScale = contentScale)
    }
}

@Composable
internal fun OptionImage(
    source: String,
    modifier: Modifier = Modifier,
    fit: Boolean = false,
) {
    val tokens = LocalFormhausTokens.current
    val image = OptionImages.shown(source)
    val frame =
        modifier
            .fillMaxWidth()
            .clip(tokens.innerShape)
            .background(tokens.colors.backgroundDisabled)
            .clearAndSetSemantics {}
    when {
        image == null -> Box(frame.aspectRatio(PICTURE_RATIO))
        fit -> Image(image, null, frame.aspectRatio(image.width.toFloat() / image.height), contentScale = ContentScale.Fit)
        else -> Image(image, null, frame.aspectRatio(PICTURE_RATIO), contentScale = ContentScale.Crop)
    }
}
