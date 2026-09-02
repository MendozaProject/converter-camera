package com.devsquid.convertercamera.business

import android.graphics.RectF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.devsquid.convertercamera.database.SessionRepository
import com.devsquid.convertercamera.ui.formatDecimal
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TextScanner(
    private val recognizer: TextRecognizer,
    private val sessionRepository: SessionRepository
) {
    // Regex to find numbers that may have a decimal part (exactly 2 digits after . or ,)
    // It looks for a sequence of digits, optionally followed by (. or ,) and exactly 2 digits.
    // Non-price related periods/commas are ignored by not matching the overall pattern.
    private val priceRegex = Regex("(\\d+([.,]\\d{2})?)")

    @OptIn(ExperimentalGetImage::class)
    suspend fun recognizePrices(imageProxy: ImageProxy): List<ScannedTextData> {
        imageProxy.use {
            val mediaImage = it.image
                ?: throw IllegalStateException("Captured image had no backing data")

            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                it.imageInfo.rotationDegrees
            )
            val result = recognizer.recognizeSuspend(inputImage)
            return result.extractNumbers()
        }
    }

    private suspend fun TextRecognizer.recognizeSuspend(inputImage: InputImage): Text =
        suspendCancellableCoroutine { cont ->
            process(inputImage)
                .addOnSuccessListener { text -> if (cont.isActive) cont.resume(text) }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        }

    private fun Text.extractNumbers(): List<ScannedTextData> {
        val result = mutableListOf<ScannedTextData>()
        this.textBlocks.forEach { block ->
            block.lines.forEach { line ->
                line.elements.forEach { element ->
                    if (element.confidence > 0.6f) {
                        priceRegex.findAll(element.text).forEach { match ->
                            val numberText = match.value
                            val normalized = numberText.replace(',', '.')
                            normalized.toDoubleOrNull()?.let {
                                result.add(
                                    ScannedTextData(
                                        id = UUID.randomUUID().toString(),
                                        rect = RectF(line.boundingBox),
                                        originalValue = it,
                                        originalText = numberText,
                                        replacementText = getConvertedText(it)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        return result
    }

    fun close() {
        recognizer.close()
    }

    fun getConvertedText(amount: Double): String {
        // Normalize to use '.' as decimal separator for parsing

        val convertedAmount = sessionRepository.convertCurrency(amount)
        val data = sessionRepository.getToCurrencyData()

        return if (data != null && convertedAmount > 0)
            formatCurrency(convertedAmount, data.isoCode) else "nul"
    }

    fun formatCurrency(amount: Double, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val currency = Currency.getInstance(currencyCode)
        val format = NumberFormat.getCurrencyInstance(locale)
        format.currency = currency
        format.minimumFractionDigits = currency.defaultFractionDigits
        format.maximumFractionDigits = currency.defaultFractionDigits
        return format.format(amount)
    }

    data class ScannedTextData(
        val id: String,
        val rect: RectF,
        val originalValue: Double,
        val originalText: String,
        val replacementText: String,
    )
}
