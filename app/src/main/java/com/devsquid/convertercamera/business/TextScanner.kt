package com.devsquid.convertercamera.business

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import java.math.BigDecimal

interface TextScanner {

    /**
     * Recognizes prices in the given [imageProxy] and returns them converted.
     * Implementations are responsible for closing the [imageProxy].
     */
    suspend fun recognizePrices(imageProxy: ImageProxy): List<ScannedTextData>

    /**
     * Recognizes prices in an already upright (rotation-corrected) [bitmap]. Returned rects are in
     * the [bitmap]'s pixel space. The caller keeps ownership of the [bitmap]; it is not recycled.
     */
    suspend fun recognizePrices(bitmap: Bitmap): List<ScannedTextData>

    fun close()

    data class ScannedTextData(
        val id: String,
        val rect: RectF,
        val originalValue: BigDecimal,
        val originalText: String,
        val replacementText: String,
        val likelyNotPrice: Boolean
    )
}
