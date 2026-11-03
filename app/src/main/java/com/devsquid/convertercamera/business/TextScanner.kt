package com.devsquid.convertercamera.business

import android.graphics.RectF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.math.BigDecimal
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TextScanner(
    private val recognizer: TextRecognizer,
    private val textParser: TextParser,
    private val textConverter: TextConverter
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
                        textParser.parseAmount(element.text).let { parsed ->
                            if (parsed.status != TextParser.Status.MALFORMED && parsed.value != null) {
                                result.add(
                                    ScannedTextData(
                                        id = UUID.randomUUID().toString(),
                                        rect = RectF(line.boundingBox),
                                        originalValue = parsed.value,
                                        originalText = element.text,
                                        replacementText = textConverter.getConvertedText(parsed.value)
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

    companion object {
        private const val TAG = "TextScanner"
    }

    data class ScannedTextData(
        val id: String,
        val rect: RectF,
        val originalValue: BigDecimal,
        val originalText: String,
        val replacementText: String,
    )
}