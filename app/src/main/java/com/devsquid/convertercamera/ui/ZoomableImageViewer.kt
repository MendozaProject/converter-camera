package com.devsquid.convertercamera.ui

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import com.devsquid.convertercamera.ui.camera.OverlayText
import com.devsquid.convertercamera.ui.theme.Dimens
import kotlin.math.roundToInt

@Composable
fun ZoomableImageViewer(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    overlays: List<OverlayText> = emptyList(),
    maxScale: Float = 5f,
    onPriceTapped: (OverlayText) -> Unit = {},
    onPriceLongTapped: (OverlayText) -> Unit = {}
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

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
            if (scale == 1f) scale = cropScaleFactor
        }

        Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectTransformGestures { centroid, pan, gestureZoom, _ ->
                val oldScale = scale
                val newScale =
                    (scale * gestureZoom).coerceIn(cropScaleFactor, maxScale * cropScaleFactor)

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

                // Constraints to prevent showing white space around the image
                val rangeX =
                    ((displayedWidth * scale) - containerWidthPx).coerceAtLeast(0f) / (2 * scale)
                val rangeY =
                    ((displayedHeight * scale) - containerHeightPx).coerceAtLeast(0f) / (2 * scale)

                offset = Offset(
                    offset.x.coerceIn(-rangeX, rangeX),
                    offset.y.coerceIn(-rangeY, rangeY)
                )
            }
        }.pointerInput(Unit) {
            detectTapGestures(
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

                overlays.forEach { overlay ->
                    if (!overlay.hidden) {
                        val left = letterboxX + overlay.rectInBitmapCoords.left * fitScale
                        val top = letterboxY + overlay.rectInBitmapCoords.top * fitScale
                        val widthPx = overlay.rectInBitmapCoords.width() * fitScale
                        val heightPx = overlay.rectInBitmapCoords.height() * fitScale

                        if (widthPx > 0 && heightPx > 0) {
                            OverlayTextBox(
                                data = overlay,
                                left = left,
                                top = top,
                                widthPx = widthPx,
                                heightPx = heightPx,
                                onPriceTapped = onPriceTapped,
                                onPriceLongTapped = onPriceLongTapped,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OverlayTextBox(
    data: OverlayText,
    left: Float,
    top: Float,
    widthPx: Float,
    heightPx: Float,
    onPriceTapped: (OverlayText) -> Unit,
    onPriceLongTapped: (OverlayText) -> Unit,
) {
    val baseStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val targetFontSize = with(density) { (heightPx * 0.8f).toSp() }
    val textLayoutResult = textMeasurer.measure(
        text = data.replacementText,
        style = baseStyle.copy(fontSize = targetFontSize)
    )
    val scaleFactor =
        if (textLayoutResult.size.width > 0)
                (widthPx / textLayoutResult.size.width).coerceAtMost(1f)
        else 1f

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .height(
                height = with(density) { heightPx.toDp() + Dimens.spacingMedium }
            )
            .combinedClickable(
                onClick = { onPriceTapped(data) },
                onLongClick = { onPriceLongTapped(data) }
            )
    ) {
        Text(
            text = data.replacementText,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = baseStyle.copy(fontSize = targetFontSize),
            softWrap = false,
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(Dimens.cornerRadiusSmall)
                )
                .border(
                    width = Dimens.borderWidthMedium,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(Dimens.cornerRadiusSmall)
                )
                .padding(horizontal = Dimens.spacingMedium, vertical = Dimens.spacingTiny)
                .graphicsLayer {
                    scaleX = scaleFactor
                    scaleY = scaleFactor
                    transformOrigin = TransformOrigin.Center
                }
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
                originalValue = 0.0
            ),
            left = 50f,
            top = 50f,
            widthPx = 300f,
            heightPx = 100f,
            onPriceTapped = {},
            onPriceLongTapped = {}
        )
    }
}
