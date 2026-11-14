package com.devsquid.convertercamera.ui

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.devsquid.convertercamera.ui.camera.OverlayText
import com.devsquid.convertercamera.ui.theme.Dimens
import java.math.BigDecimal
import kotlin.math.roundToInt

/** Minimum zoom: the entire image fits inside the viewer. */
private const val MIN_SCALE_FIT = 1f

/** Smallest font size for price overlay text (before the viewer's zoom is applied). */
private val MIN_OVERLAY_FONT_SIZE = 4.sp

private const val HIDDEN_OVERLAY_ALPHA = 0.30f

@Composable
fun ZoomableImageViewer(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    showOverlay: Boolean = true,
    overlays: List<OverlayText> = emptyList(),
    maxScale: Float = 5f,
    onPriceTapped: (OverlayText) -> Unit = {},
    onPriceLongTapped: (OverlayText) -> Unit = {}
) {
    // scale is relative to the fitted image: 1f = whole image visible (fit), cropScaleFactor =
    // image fills the container (crop). Defaults to fill; can be zoomed out down to fit.
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var initialScaleApplied by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()
        val containerAspect = containerWidthPx / containerHeightPx
        val bitmapAspect = bitmap.width.toFloat() / bitmap.height.toFloat()

        val fitScale = if (bitmapAspect > containerAspect) {
            containerWidthPx / bitmap.width.toFloat()
        } else {
            containerHeightPx / bitmap.height.toFloat()
        }

        val cropScaleFactor = if (bitmapAspect > containerAspect) {
            containerHeightPx / (bitmap.height * fitScale)
        } else {
            containerWidthPx / (bitmap.width * fitScale)
        }

        val displayedWidth = bitmap.width * fitScale
        val displayedHeight = bitmap.height * fitScale
        val letterboxX = (containerWidthPx - displayedWidth) / 2f
        val letterboxY = (containerHeightPx - displayedHeight) / 2f

        LaunchedEffect(cropScaleFactor) {
            if (!initialScaleApplied) {
                scale = cropScaleFactor
                initialScaleApplied = true
            }
        }

        Box(modifier = Modifier.fillMaxSize().pointerInput(cropScaleFactor, maxScale) {
            detectTransformGestures { centroid, pan, gestureZoom, _ ->
                val oldScale = scale
                val newScale =
                    (scale * gestureZoom).coerceIn(MIN_SCALE_FIT, maxScale * cropScaleFactor)

                if (newScale != oldScale) {
                    val zoomFactor = newScale / oldScale
                    offset = (offset + (centroid - Offset(
                        containerWidthPx / 2f,
                        containerHeightPx / 2f
                    )) / oldScale) * zoomFactor -
                            (centroid - Offset(
                                containerWidthPx / 2f,
                                containerHeightPx / 2f
                            )) / newScale
                }

                offset -= pan / newScale
                scale = newScale

                // Keep the image edges from being dragged inside the container; when the image is
                // smaller than the container on an axis it stays centered on that axis.
                val rangeX =
                    ((displayedWidth * scale) - containerWidthPx).coerceAtLeast(0f) / (2 * scale)
                val rangeY =
                    ((displayedHeight * scale) - containerHeightPx).coerceAtLeast(0f) / (2 * scale)

                offset = Offset(
                    offset.x.coerceIn(-rangeX, rangeX),
                    offset.y.coerceIn(-rangeY, rangeY)
                )
            }
        }.pointerInput(cropScaleFactor) {
            detectTapGestures(
                // Reset to the default fill-screen zoom.
                onDoubleTap = {
                    scale = cropScaleFactor
                    offset = Offset.Zero
                }
            )
        }) {

            Box(
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = -offset.x * scale
                    translationY = -offset.y * scale
                    transformOrigin = TransformOrigin.Center
                }
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                if (showOverlay && overlays.isNotEmpty()) {
                    ConvertedPriceOverlay(
                        fitScale,
                        letterboxX,
                        letterboxY,
                        overlays,
                        onPriceTapped,
                        onPriceLongTapped
                    )
                }
            }
        }
    }
}

@Composable
fun ConvertedPriceOverlay(
    fitScale: Float,
    letterboxX: Float,
    letterboxY: Float,
    overlays: List<OverlayText>,
    onPriceTapped: (OverlayText) -> Unit,
    onPriceLongTapped: (OverlayText) -> Unit
) {
    // Hidden overlays stay in the layout (drawn invisible) so they can be long-pressed to show again.
    overlays.forEach { overlay ->
        val widthPx = overlay.rectInBitmapCoords.width() * fitScale
        val heightPx = overlay.rectInBitmapCoords.height() * fitScale

        if (widthPx > 0 && heightPx > 0) {
            OverlayTextBox(
                data = overlay,
                right = letterboxX + overlay.rectInBitmapCoords.right * fitScale,
                top = letterboxY + overlay.rectInBitmapCoords.top * fitScale,
                widthPx = widthPx,
                heightPx = heightPx,
                onPriceTapped = onPriceTapped,
                onPriceLongTapped = onPriceLongTapped
            )
        }
    }
}

@Composable
fun OverlayTextBox(
    data: OverlayText,
    right: Float,
    top: Float,
    widthPx: Float,
    heightPx: Float,
    onPriceTapped: (OverlayText) -> Unit,
    onPriceLongTapped: (OverlayText) -> Unit,
) {
    // Follow the theme's text style (font family etc.). The font size is derived from the box
    // height below, so drop the theme's fixed line height to avoid clipping the scaled text.
    // Values that may include a misread ¥ are drawn red so the user double-checks them.
    val containerColor =
        if (data.possibleMisread) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.secondaryContainer
    val contentColor =
        if (data.possibleMisread) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer
    val baseStyle = MaterialTheme.typography.bodyLarge.copy(
        color = contentColor,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        lineHeight = TextUnit.Unspecified
    )
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val minFontPx = with(density) { MIN_OVERLAY_FONT_SIZE.toPx() }
    // Font size follows the recognized text height, but never below the minimum.
    val fontPx = maxOf(heightPx, minFontPx)
    val targetFontSize = with(density) { fontPx.toSp() }
    val textLayoutResult = textMeasurer.measure(
        text = data.replacementText,
        style = baseStyle.copy(fontSize = targetFontSize)
    )
    val fitWidthScale =
        if (textLayoutResult.size.width > 0)
            (widthPx / textLayoutResult.size.width).coerceAtMost(1f)
        else 1f
    // Shrink to fit the recognized width, but not so far that the text drops below the minimum.
    val scaleFactor = maxOf(fitWidthScale, minFontPx / fontPx)
    val boxHeightPx = maxOf(heightPx, minFontPx)

    val shape = RoundedCornerShape(Dimens.cornerRadiusSmall)

    Text(
        text = data.replacementText,
        // When hidden, the text is transparent so it still sizes the box but isn't drawn.
        color = if (data.hidden) Color.Transparent else contentColor,
        style = baseStyle.copy(fontSize = targetFontSize),
        softWrap = false,
        textAlign = TextAlign.Center,
        modifier = Modifier
            // Anchor the label's right edge to the right edge of the recognized text rect.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.place((right - placeable.width).roundToInt(), top.roundToInt())
                }
            }
            .height(
                height = with(density) { boxHeightPx.toDp() + Dimens.spacingMedium }
            )
            .combinedClickable(
                onClick = { onPriceTapped(data) },
                onLongClick = { onPriceLongTapped(data) }
            )
            .then(
                if (data.hidden) {
                    // Faint marker so a hidden label can be found and long-pressed to show again.
                    Modifier.background(color = Color.White.copy(alpha = HIDDEN_OVERLAY_ALPHA), shape = shape)
                } else {
                    Modifier
                        .background(
                            color = containerColor.copy(alpha = 0.9f),
                            shape = shape
                        )
                }
            )
            .padding(horizontal = Dimens.spacingMedium, vertical = Dimens.spacingTiny)
            .graphicsLayer {
                scaleX = scaleFactor
                scaleY = scaleFactor
                transformOrigin = TransformOrigin.Center
            }
    )

}

@Preview(showBackground = true)
@Composable
fun PreviewOverlayTextBoxPossibleMisread() {
    Box(modifier = Modifier.size(Dimens.previewSize).background(MaterialTheme.colorScheme.surfaceVariant)) {
        OverlayTextBox(
            data = OverlayText(
                id = "1",
                rectInBitmapCoords = RectF(0f, 0f, 100f, 50f),
                originalText = "474",
                replacementText = "$3.10",
                converted = true,
                originalValue = BigDecimal(474),
                possibleMisread = true
            ),
            right = 300f,
            top = 50f,
            widthPx = 300f,
            heightPx = 100f,
            onPriceTapped = {},
            onPriceLongTapped = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewOverlayTextBox() {
    Box(modifier = Modifier.size(Dimens.previewSize).background(MaterialTheme.colorScheme.surfaceVariant)) {
        OverlayTextBox(
            data = OverlayText(
                id = "1",
                rectInBitmapCoords = RectF(0f, 0f, 100f, 50f),
                originalText = "100",
                replacementText = "$1.50",
                converted = true,
                originalValue = BigDecimal.ZERO
            ),
            right = 300f,
            top = 50f,
            widthPx = 300f,
            heightPx = 100f,
            onPriceTapped = {},
            onPriceLongTapped = {}
        )
    }
}
