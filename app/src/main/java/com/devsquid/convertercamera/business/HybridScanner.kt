package com.devsquid.convertercamera.business

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.core.graphics.scale
import com.devsquid.convertercamera.business.MLKitTextScanner.PriceCandidate
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
import java.math.BigDecimal
import java.text.Normalizer
import java.util.UUID
import kotlin.math.roundToInt

/**
 * [TextScanner] that combines ML Kit and Gemini Nano:
 *
 * 1. ML Kit text recognition finds every number, its box and the line it sits on
 *    ([MLKitTextScanner.detectCandidates]). ML Kit is fast and its boxes are precise.
 * 2. Gemini Nano gets the image plus the numbered list of candidates and decides, for each one,
 *    whether it is a price and what its correct value is (e.g. "￥74" misread as "474" -> 74).
 *    The model only classifies and corrects; it never has to locate text, which it is weak at.
 *
 * Boxes always come from ML Kit. A correction is accepted only if its digits appear in the OCR
 * text, so the model can drop a misread symbol but can't make up a number. If Gemini Nano is
 * unavailable or fails, the result falls back to ML Kit's own heuristics.
 */
class HybridScanner(
    private val mlKitScanner: MLKitTextScanner,
    private val generativeModel: GenerativeModel,
    private val textParser: TextParser,
    private val priceConverter: PriceConverter,
) : TextScanner {

    /** Set once AICore confirmed the model and structured output are usable. */
    @Volatile
    private var modelReady = false

    @Volatile
    private var warmedUp = false

    /** Loads Gemini Nano ahead of the first scan. Safe to call repeatedly; failures are logged. */
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
        return try {
            recognizePrices(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    override suspend fun recognizePrices(bitmap: Bitmap): List<ScannedTextData> {
        val candidates = mlKitScanner.detectCandidates(bitmap)
        if (candidates.isEmpty()) return emptyList()

        val isoCode = priceConverter.fromIsoCode()
        // Candidates beyond the cap keep ML Kit's verdict; the cap bounds prompt and output size.
        val verdicts = review(bitmap, candidates.take(MAX_CANDIDATES), isoCode)
        return candidates.mapIndexed { index, candidate ->
            candidate.toScannedTextData(verdicts?.get(index), isoCode)
        }
    }

    /** Asks Gemini Nano about each candidate. Returns verdicts by index, or null on failure. */
    private suspend fun review(
        bitmap: Bitmap,
        candidates: List<PriceCandidate>,
        isoCode: String,
    ): Map<Int, CandidateVerdict>? {
        val modelInput = bitmap.scaledToMaxSide(MAX_INPUT_SIDE_PX)
        return try {
            ensureModelAvailable()
            val request = generateTypedContentRequest(
                generateContentRequest = GenerateContentRequest.Builder(
                    ImagePart(modelInput),
                    TextPart(buildPrompt(candidates, isoCode)),
                ).apply {
                    temperature = 0f
                    topK = 1
                    maxOutputTokens = MAX_OUTPUT_TOKENS
                }.build(),
                outputClass = CandidateReview::class,
            )
            val candidate = generativeModel.generateContent(request).candidates.firstOrNull()
            val review = candidate?.response ?: run {
                Log.w(TAG, "No structured response, finishReason=${candidate?.finishReason}")
                return null
            }
            review.r
                .filter { it.i in candidates.indices }
                .associateBy { it.i }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Gemini Nano review failed; using ML Kit heuristics", e)
            // Re-verify availability next time in case AICore state changed (e.g. model evicted).
            modelReady = false
            null
        } finally {
            if (modelInput !== bitmap) modelInput.recycle()
        }
    }

    private fun PriceCandidate.toScannedTextData(verdict: CandidateVerdict?, isoCode: String): ScannedTextData {
        val corrected = verdict?.let { correction(it.v, isoCode) }
        val finalValue = corrected?.second ?: value
        return ScannedTextData(
            id = UUID.randomUUID().toString(),
            rect = rect,
            originalValue = finalValue,
            originalText = corrected?.first ?: elementText,
            replacementText = priceConverter.convertPrice(finalValue).format(priceConverter.toIsoCode()),
            likelyNotPrice = verdict?.let { !it.p } ?: likelyNotPrice,
        )
    }

    /**
     * Applies the model's correction, returning the corrected OCR text and its value, or null if
     * there is no usable correction.
     *
     * Only the model's digits are used, to decide which OCR digits to keep. The text that gets
     * parsed is cut from the OCR text itself, so separators are always the ones that were
     * printed: the model can't turn "1,001" into "1.001" or make up a number. Dropping a
     * misread symbol ("474)" -> "74") still works.
     */
    private fun PriceCandidate.correction(modelText: String, isoCode: String): Pair<String, BigDecimal>? {
        val digits = Normalizer.normalize(modelText, Normalizer.Form.NFKC).filter { it in '0'..'9' }
        val slice = ocrSlice(elementText, digits) ?: return null
        val parsed = textParser.parsePriceText(slice, isoCode)
        val newValue = parsed.value ?: return null
        if (parsed.status == TextParser.Status.MALFORMED || newValue.compareTo(value) == 0) return null
        return slice to newValue
    }

    private fun buildPrompt(candidates: List<PriceCandidate>, isoCode: String): String {
        val list = candidates.withIndex().joinToString("\n") { (i, c) ->
            "$i: \"${c.elementText.sanitized()}\" in line \"${c.lineText.sanitized()}\""
        }
        val currency = if (isoCode.isNotEmpty()) " Prices are in $isoCode." else ""
        return """
            ## Task
            Text recognition (OCR) found the numbers listed below in the image.$currency
            For each number, decide whether it is a price and give its correct value.
            ## Rules
            - i: the number's index from the list. Return exactly one entry per number.
            - p: true if it is a price. false for percentages, quantities, weights and other units, dates, times, phone numbers, IDs, codes, ratings and table or page numbers.
            - v: the price exactly as listed, with digits and separators only. Keep every "," and "." as it is; never swap them. OCR sometimes reads a currency symbol as a digit (for example "￥74" read as "474"); check the image and drop such digits. Otherwise copy the number unchanged.
            ## Numbers
            $list
        """.trimIndent()
    }

    /** Keeps OCR text from breaking the prompt's quoting or line structure. */
    private fun String.sanitized() = replace('"', '\'').replace('\n', ' ').take(MAX_LINE_CHARS)

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

    private fun Bitmap.scaledToMaxSide(maxSide: Int): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= maxSide) return this
        val ratio = maxSide.toFloat() / longest
        return scale(
            (width * ratio).roundToInt().coerceAtLeast(1),
            (height * ratio).roundToInt().coerceAtLeast(1),
        )
    }

    private fun ImageProxy.toUprightBitmap(): Bitmap {
        val raw = toBitmap()
        val rotation = imageInfo.rotationDegrees
        if (rotation == 0) return raw
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also {
            if (it !== raw) raw.recycle()
        }
    }

    override fun close() {
        mlKitScanner.close()
        generativeModel.close()
    }

    // Short property names keep the generated JSON (and latency) small; @Guide carries meaning.
    @Generable("Review of the numbers found by OCR")
    data class CandidateReview(
        @Guide(description = "One entry per listed number", maxItems = MAX_CANDIDATES)
        val r: List<CandidateVerdict>,
    )

    @Generable("Verdict for one number found by OCR")
    data class CandidateVerdict(
        @Guide(description = "Index of the number in the list", minimum = 0.0, maximum = MAX_CANDIDATE_INDEX)
        val i: Int,
        @Guide(description = "true if the number is a price")
        val p: Boolean,
        @Guide(description = "Corrected price, digits and separators only, e.g. 74 or 1,200")
        val v: String,
    )

    companion object {
        private const val TAG = "HybridScanner"

        /** Max candidates sent to the model per image. */
        const val MAX_CANDIDATES = 30
        private const val MAX_CANDIDATE_INDEX = 29.0

        /** ~12 tokens per verdict with short keys, so [MAX_CANDIDATES] fits with headroom. */
        private const val MAX_OUTPUT_TOKENS = 512

        /** Longest side of the bitmap sent to Gemini Nano. */
        private const val MAX_INPUT_SIDE_PX = 1024

        /** Truncates very long OCR lines in the prompt. */
        private const val MAX_LINE_CHARS = 80

        /**
         * Returns the part of the cleaned [ocrText] that covers [digits], keeping the OCR's own
         * separators, e.g. ("41,001", "1001") -> "1,001" and ("474)", "74") -> "74".
         * Returns null if [digits] isn't a contiguous run of the OCR digits.
         * A match at the end is preferred, then at the start, since misread currency symbols sit
         * before (￥) or after (円) the number.
         */
        internal fun ocrSlice(ocrText: String, digits: String): String? {
            if (digits.isEmpty()) return null
            val text = MLKitTextScanner.cleanPriceText(ocrText)
            val positions = text.indices.filter { text[it] in '0'..'9' }
            val ocrDigits = positions.joinToString("") { text[it].toString() }
            val start = when {
                ocrDigits.endsWith(digits) -> ocrDigits.length - digits.length
                ocrDigits.startsWith(digits) -> 0
                else -> ocrDigits.indexOf(digits).takeIf { it >= 0 } ?: return null
            }
            return text.substring(positions[start], positions[start + digits.length - 1] + 1)
        }
    }
}
