package com.devsquid.convertercamera.business

import android.graphics.RectF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.devsquid.convertercamera.database.SessionRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TextScanner(
    private val recognizer: TextRecognizer,
    private val sessionRepository: SessionRepository
) {
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
                        val decimalDelimiter = sessionRepository.getDecimalDelimiter()
                        // Keep digits and the specified decimal delimiter
                        val regex = Regex("[^\\d${Regex.escape(decimalDelimiter.toString())}]")
                        val text = element.text.replace(regex, "").trim()
                        if (text.isNotEmpty()) {
                            (text.split(" ").filter { it.isNotEmpty() && it != "0" })
                                .forEach { numberText ->
                                    val normalized = text.replace(decimalDelimiter, '.')
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
        }
        return result
    }

    fun close() {
        recognizer.close()
    }

    fun getConvertedText(amount: Double): String {
        // Normalize to use '.' as decimal separator for parsing

        val convertedAmount = sessionRepository.convertCurrency(amount)
        val valueText = if (convertedAmount <= 0.0) "nul" else "%.2f".format(convertedAmount)

        return "${sessionRepository.getToCurrencySymbol()}${valueText}"
    }

    data class ScannedTextData(
        val id: String,
        val rect: RectF,
        val originalValue: Double,
        val originalText: String,
        val replacementText: String,
    )
}
