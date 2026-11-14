package com.devsquid.convertercamera.business

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageProxy
import com.devsquid.convertercamera.business.TextScanner.ScannedTextData
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateTypedContentRequest
import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide
import kotlinx.coroutines.CancellationException
import java.util.UUID
import kotlin.math.roundToInt

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

    /** Set once AICore confirmed the model and structured output are usable; avoids 2 IPC calls per scan. */
    @Volatile
    private var modelReady = false

    @Volatile
    private var warmedUp = false

    /**
     * Loads Gemini Nano into memory ahead of the first scan so the first capture doesn't pay the
     * model load cost. Safe to call repeatedly; failures are logged and ignored.
     */
    suspend fun warmup() {
        if (warmedUp) return
        try {
            ensureModelAvailable()
            generativeModel.warmup()
            warmedUp = true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Gemini Nano warmup failed", e)
        }
    }

    override suspend fun recognizePrices(imageProxy: ImageProxy): List<ScannedTextData> {
        val bitmap = imageProxy.use { it.toUprightBitmap() }
        return recognizePrices(bitmap)
    }

    override suspend fun recognizePrices(bitmap: Bitmap): List<ScannedTextData> {
        ensureModelAvailable()

        // The model downsamples internally anyway; sending a smaller bitmap saves the copy to
        // AICore. Boxes are normalized, so they still map onto the original [bitmap] below.
        val modelInput = bitmap.scaledToMaxSide(MAX_INPUT_SIDE_PX)

        val request = generateTypedContentRequest(
            generateContentRequest = GenerateContentRequest.Builder(
                ImagePart(modelInput),
                TextPart(PROMPT),
            ).apply {
                temperature = 0f
                topK = 1
                maxOutputTokens = MAX_OUTPUT_TOKENS
            }.build(),
            outputClass = DetectedPriceList::class,
        )

        val detected = try {
            val candidate = generativeModel.generateContent(request).candidates.firstOrNull()
            candidate?.response ?: run {
                Log.w(TAG, "No structured response, finishReason=${candidate?.finishReason}")
                return emptyList()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to recognize prices with GenerativeModel structured output", e)
            // Re-verify availability next time in case AICore state changed (e.g. model evicted).
            modelReady = false
            return emptyList()
        } finally {
            if (modelInput !== bitmap) modelInput.recycle()
        }

        return detected.p.toScannedTextData(bitmap.width, bitmap.height)
    }

    private fun Bitmap.scaledToMaxSide(maxSide: Int): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= maxSide) return this
        val scale = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            this,
            (width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    private suspend fun ensureModelAvailable() {
        if (modelReady) return
        val status = generativeModel.checkStatus()
        check(status == FeatureStatus.AVAILABLE) {
            "Gemini Nano is not available on this device (status=$status)"
        }
        check(generativeModel.isStructuredOutputFeatureAvailable()) {
            "Structured output is not supported by the on-device model"
        }
        modelReady = true
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
            val cleanText = price.t.replace(Regex("""[^\d.,·]"""), "").trim()
            val parsed = textParser.parsePriceText(cleanText, convertFromIsoCode)
            val value = parsed.value
            if (parsed.status == TextParser.Status.MALFORMED || value == null) return@mapNotNull null

            val rect = RectF(
                minOf(price.x1, price.x2) * scaleX,
                minOf(price.y1, price.y2) * scaleY,
                maxOf(price.x1, price.x2) * scaleX,
                maxOf(price.y1, price.y2) * scaleY,
            )
            if (rect.isEmpty) return@mapNotNull null

            ScannedTextData(
                id = UUID.randomUUID().toString(),
                rect = rect,
                originalValue = value,
                originalText = cleanText.ifEmpty { price.t },
                replacementText = priceConverter.convertPrice(value).format(convertToIsoCode),
                likelyNotPrice = false,
            )
        }
    }

    override fun close() {
        generativeModel.close()
    }

    // Property names become the JSON keys the model has to generate, token by token, for every
    // price. They are kept to 1-2 characters on purpose; the @Guide descriptions (prompt input,
    // not output) carry the meaning.
    @Generable("Price tags found in the image")
    data class DetectedPriceList(
        @Guide(description = "Every price found in the image. Empty if none.", maxItems = MAX_PRICES)
        val p: List<DetectedPrice>,
    )

    @Generable("A single price and its bounding box, coordinates normalized to 0-1000")
    data class DetectedPrice(
        @Guide(description = "The price value, digits and separators only (no currency symbols or units), e.g. 900 or 1,299.99")
        val t: String,
        @Guide(description = "Top edge y (ymin)", minimum = 0.0, maximum = 1000.0)
        val y1: Int,
        @Guide(description = "Left edge x (xmin)", minimum = 0.0, maximum = 1000.0)
        val x1: Int,
        @Guide(description = "Bottom edge y (ymax)", minimum = 0.0, maximum = 1000.0)
        val y2: Int,
        @Guide(description = "Right edge x (xmax)", minimum = 0.0, maximum = 1000.0)
        val x2: Int,
    )

    companion object {
        private const val TAG = "AITextScanner"
        private const val NORMALIZED_MAX = 1000f

        /** Longest side of the bitmap sent to Gemini Nano. */
        private const val MAX_INPUT_SIDE_PX = 1024

        /** Upper bound on prices per image; keeps worst-case output (and latency) bounded. */
        const val MAX_PRICES = 15

        /** ~30 tokens per price with short keys, so [MAX_PRICES] fits with headroom. */
        private const val MAX_OUTPUT_TOKENS = 512

        private val PROMPT = """
            ## Task
            Find every number in the image that is likely a price (price tags, menus, shelf labels, receipts).
            ## Rules
            - Ignore dates, phone numbers, quantities, weights, product codes and barcodes.
            - t: digits and separators only, without currency symbols or words. Example: "€ 12,50" -> "12,50", "900円" -> "900".
            - y1,x1,y2,x2: tight box around the number. Coordinates normalized 0-1000 where (0,0) is top-left, (1000,1000) is bottom-right.
              - y1: top edge y (ymin)
              - x1: left edge x (xmin)
              - y2: bottom edge y (ymax)
              - x2: right edge x (xmax)
        """.trimIndent()
    }
}
