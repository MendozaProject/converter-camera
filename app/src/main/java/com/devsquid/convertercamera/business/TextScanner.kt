package com.devsquid.convertercamera.business

import android.graphics.RectF
import androidx.camera.core.ImageProxy
import java.math.BigDecimal

interface TextScanner {

    /**
     * Recognizes prices in the given [imageProxy] and returns them converted.
     * Implementations are responsible for closing the [imageProxy].
     */
    suspend fun recognizePrices(imageProxy: ImageProxy): List<ScannedTextData>

    fun close()

    data class ScannedTextData(
        val id: String,
        val rect: RectF,
        val originalValue: BigDecimal,
        val originalText: String,
        val replacementText: String,
    )
}
