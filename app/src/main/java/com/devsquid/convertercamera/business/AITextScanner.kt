package com.devsquid.convertercamera.business

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageProxy
import com.devsquid.convertercamera.business.TextScanner.ScannedTextData
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateTypedContentRequest
import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide
import java.util.UUID

/**
 * [TextScanner] backed by Gemini Nano via AICore (ML Kit GenAI Prompt API).
 *
 * The model is asked to locate every number in the image that looks like a price tag and to
 * return its text plus a bounding box normalized to a 0..1000 grid. Boxes are mapped back to
 * the pixel space of the upright (rotation-corrected) image, matching [MLKitTextScanner].
 */
class AITextScanner(
    private val generativeModel: GenerativeModel,
    private val textParser: TextParser,
    private val priceConverter: PriceConverter,
) : TextScanner {

    override suspend fun recognizePrices(imageProxy: ImageProxy): List<ScannedTextData> {
        val bitmap = imageProxy.use { it.toUprightBitmap() }

        ensureModelAvailable()

        val request = generateTypedContentRequest(
            generateContentRequest = GenerateContentRequest.Builder(
                ImagePart(bitmap),
                TextPart(PROMPT),
            ).apply {
                temperature = 0f
                topK = 1
            }.build(),
            outputClass = DetectedPriceList::class,
        )

        val detected = try {
            val candidate = generativeModel.generateContent(request).candidates.firstOrNull()
            candidate?.response ?: run {
                Log.w(TAG, "No structured response, finishReason=${candidate?.finishReason}")
                return emptyList()
            }
        } catch (e: GenAiException) {
            if (e.errorCode == ERROR_STRUCTURED_OUTPUT_INVALID_VALUE) {
                Log.w(TAG, "Model output violated schema", e)
                return emptyList()
            }
            throw e
        }

        return detected.prices.toScannedTextData(bitmap.width, bitmap.height)
    }

    private suspend fun ensureModelAvailable() {
        val status = generativeModel.checkStatus()
        check(status == FeatureStatus.AVAILABLE) {
            "Gemini Nano is not available on this device (status=$status)"
        }
        check(generativeModel.isStructuredOutputFeatureAvailable()) {
            "Structured output is not supported by the on-device model"
        }
    }

    private fun ImageProxy.toUprightBitmap(): Bitmap {
        val raw = toBitmap()
        val rotation = imageInfo.rotationDegrees
        if (rotation == 0) return raw
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    }

    private fun List<DetectedPrice>.toScannedTextData(
        imageWidth: Int,
        imageHeight: Int,
    ): List<ScannedTextData> {
        val convertFromIsoCode = priceConverter.fromIsoCode()
        val convertToIsoCode = priceConverter.toIsoCode()
        val scaleX = imageWidth / NORMALIZED_MAX
        val scaleY = imageHeight / NORMALIZED_MAX

        return mapNotNull { price ->
            val parsed = textParser.parsePriceText(price.text, convertFromIsoCode)
            val value = parsed.value
            if (parsed.status == TextParser.Status.MALFORMED || value == null) return@mapNotNull null

            val rect = RectF(
                minOf(price.left, price.right) * scaleX,
                minOf(price.top, price.bottom) * scaleY,
                maxOf(price.left, price.right) * scaleX,
                maxOf(price.top, price.bottom) * scaleY,
            )
            if (rect.isEmpty) return@mapNotNull null

            ScannedTextData(
                id = UUID.randomUUID().toString(),
                rect = rect,
                originalValue = value,
                originalText = price.text,
                replacementText = priceConverter.convertPrice(value).format(convertToIsoCode),
            )
        }
    }

    override fun close() {
        generativeModel.close()
    }

    @Generable("Price tags found in the image")
    data class DetectedPriceList(
        @Guide(description = "Every price found in the image. Empty if none.", maxItems = 50)
        val prices: List<DetectedPrice>,
    )

    @Generable("A single price and its bounding box, coordinates normalized to 0-1000")
    data class DetectedPrice(
        @Guide(description = "The price exactly as printed, digits and separators only, e.g. 1,299.99")
        val text: String,
        @Guide(description = "Left edge x", minimum = 0.0, maximum = 1000.0)
        val left: Int,
        @Guide(description = "Top edge y", minimum = 0.0, maximum = 1000.0)
        val top: Int,
        @Guide(description = "Right edge x", minimum = 0.0, maximum = 1000.0)
        val right: Int,
        @Guide(description = "Bottom edge y", minimum = 0.0, maximum = 1000.0)
        val bottom: Int,
    )

    companion object {
        private const val TAG = "AITextScanner"
        private const val NORMALIZED_MAX = 1000f

        // Documented value of GenAiException STRUCTURED_OUTPUT_INVALID_VALUE (model output broke schema).
        private const val ERROR_STRUCTURED_OUTPUT_INVALID_VALUE = -105

        private val PROMPT = """
            ## Task
            Find every number in the image that is likely a price (price tags, menus, shelf labels, receipts).
            ## Rules
            - Ignore dates, phone numbers, quantities, weights, product codes and barcodes.
            - text: the number only, without currency symbols or words. Example: "€ 12,50" -> "12,50".
            - Box: tight box around the number. Coordinates normalized 0-1000; (0,0) is top-left, (1000,1000) is bottom-right.
        """.trimIndent()
    }
}
