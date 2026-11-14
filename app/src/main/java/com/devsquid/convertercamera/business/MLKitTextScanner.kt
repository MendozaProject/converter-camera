package com.devsquid.convertercamera.business

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import com.devsquid.convertercamera.business.TextScanner.ScannedTextData
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.text.Normalizer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import androidx.core.graphics.scale

/**
 * Price scanner backed by on-device ML Kit text recognition.
 *
 * The Latin recognizer has no glyphs for CJK currency markers such as 円/元/원 and tends to read
 * them as a similar-looking digit or letter (e.g. "1000円" -> "10007"). To avoid that, the
 * recognizer is chosen from the source currency's script ([OcrScript.forCurrency]); every
 * non-Latin model also recognizes Latin text. Any non-ASCII prefix or suffix left on a price
 * is then removed by [cleanPriceText] before parsing.
 */
class MLKitTextScanner(
    private val recognizerFactory: (OcrScript) -> TextRecognizer,
    private val textParser: TextParser,
    private val priceConverter: PriceConverter
) : TextScanner {

    /** Recognizers are created lazily per script and reused; all are closed in [close]. */
    private val recognizers = ConcurrentHashMap<OcrScript, TextRecognizer>()

    private fun recognizerFor(isoCode: String): TextRecognizer =
        recognizers.computeIfAbsent(OcrScript.forCurrency(isoCode), recognizerFactory)

    /** A number found by OCR, with its context, before deciding whether it is a price. */
    data class PriceCandidate(
        /** Line box in the source bitmap's pixel space. */
        val rect: RectF,
        /** The OCR element the number was parsed from, e.g. "474)". */
        val elementText: String,
        /** The whole OCR line containing the element, for context. */
        val lineText: String,
        val value: BigDecimal,
        /** Result of the [isLikelyNotPrice] heuristics. */
        val likelyNotPrice: Boolean,
    )

    override suspend fun recognizePrices(imageProxy: ImageProxy): List<ScannedTextData> {
        // Copy the frame out and release the camera buffer as early as possible.
        val (bitmap, rotationDegrees) = imageProxy.use { it.toBitmap() to it.imageInfo.rotationDegrees }
        return detectCandidates(bitmap, rotationDegrees, recycleSource = true).map { it.toScannedTextData() }
    }

    override suspend fun recognizePrices(bitmap: Bitmap): List<ScannedTextData> =
        detectCandidates(bitmap).map { it.toScannedTextData() }

    /**
     * Runs OCR on an upright [bitmap] and returns every number that parses, including ones the
     * heuristics flag as [PriceCandidate.likelyNotPrice]. The caller keeps ownership of [bitmap].
     */
    suspend fun detectCandidates(bitmap: Bitmap): List<PriceCandidate> =
        detectCandidates(bitmap, rotationDegrees = 0, recycleSource = false)

    private fun PriceCandidate.toScannedTextData(): ScannedTextData = ScannedTextData(
        id = UUID.randomUUID().toString(),
        rect = rect,
        originalValue = value,
        originalText = elementText,
        replacementText = priceConverter.convertPrice(value).format(priceConverter.toIsoCode()),
        likelyNotPrice = likelyNotPrice
    )

    private suspend fun detectCandidates(
        bitmap: Bitmap,
        rotationDegrees: Int,
        recycleSource: Boolean
    ): List<PriceCandidate> {
        // Downscale before the per-pixel enhancement and OCR; both scale with pixel count.
        // Boxes are scaled back up by the same factor so they match the full-size image.
        val (enhanced, scaleBack) = withContext(Dispatchers.Default) {
            val scaled = bitmap.downscaleForOcr()
            val factor = bitmap.width.toFloat() / scaled.width
            val enhanced = scaled.enhanceForOcr()
            if (scaled !== bitmap) scaled.recycle()
            enhanced to factor
        }
        if (recycleSource) bitmap.recycle()

        // Bounding boxes returned by ML Kit are relative to the rotation-corrected image,
        // same as with InputImage.fromMediaImage, so downstream mapping is unchanged.
        val inputImage = InputImage.fromBitmap(enhanced, rotationDegrees)
        val result = recognizerFor(priceConverter.fromIsoCode()).recognizeSuspend(inputImage)
        return result.extractCandidates(scaleBack)
    }

    /** Returns this bitmap scaled so its longer side is at most [MAX_OCR_DIMENSION], or itself. */
    private fun Bitmap.downscaleForOcr(): Bitmap {
        val longSide = maxOf(width, height)
        if (longSide <= MAX_OCR_DIMENSION) return this
        val ratio = MAX_OCR_DIMENSION.toFloat() / longSide
        return this.scale(
            (width * ratio).toInt().coerceAtLeast(1),
            (height * ratio).toInt().coerceAtLeast(1)
        )
    }

    /**
     * Prepares a camera frame for text recognition:
     * 1. Converts to grayscale (luma) – color carries no information for OCR and adds noise.
     * 2. Applies a linear contrast stretch around mid-grey so text separates from the background.
     * 3. Sharpens with a 3x3 Laplacian kernel to crisp up glyph edges blurred by focus/motion.
     */
    private fun Bitmap.enhanceForOcr(): Bitmap {
        val w = width
        val h = height
        val pixels = IntArray(w * h)
        getPixels(pixels, 0, w, 0, 0, w, h)

        // Pass 1: grayscale + contrast.
        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            // Rec. 601 luma in fixed point.
            val luma = (r * 299 + g * 587 + b * 114) / 1000
            gray[i] = clamp(((luma - 128) * CONTRAST + 128).toInt())
        }

        // Pass 2: sharpen. Kernel = identity + amount * Laplacian:
        //  [ 0, -a,  0 ]
        //  [-a, 1+4a, -a]
        //  [ 0, -a,  0 ]
        // Border pixels are kept as-is.
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val i = row + x
                val v = if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    gray[i]
                } else {
                    val center = gray[i]
                    val neighbours = gray[i - 1] + gray[i + 1] + gray[i - w] + gray[i + w]
                    clamp((center + SHARPEN_AMOUNT * (4 * center - neighbours)).toInt())
                }
                pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        }

        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun clamp(v: Int): Int = if (v < 0) 0 else if (v > 255) 255 else v

    private suspend fun TextRecognizer.recognizeSuspend(inputImage: InputImage): Text =
        suspendCancellableCoroutine { cont ->
            process(inputImage)
                .addOnSuccessListener { text -> if (cont.isActive) cont.resume(text) }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        }

    /** @param scaleBack factor mapping ML Kit's (downscaled) coordinates to the source image. */
    private fun Text.extractCandidates(scaleBack: Float): List<PriceCandidate> {
        val convertFromIsoCode = priceConverter.fromIsoCode()
        val result = mutableListOf<PriceCandidate>()

        this.textBlocks.forEach { block ->
            block.lines.forEach { line ->
                line.elements.forEachIndexed { index, element ->
                    if (element.confidence > 0.6f) {
                        textParser.parsePriceText(cleanPriceText(element.text), convertFromIsoCode).let { parsed ->
                            if (parsed.status != TextParser.Status.MALFORMED && parsed.value != null) {
                                result.add(
                                    PriceCandidate(
                                        rect = RectF(line.boundingBox).apply {
                                            left *= scaleBack
                                            top *= scaleBack
                                            right *= scaleBack
                                            bottom *= scaleBack
                                        },
                                        elementText = element.text,
                                        lineText = line.text,
                                        value = parsed.value,
                                        likelyNotPrice = isLikelyNotPrice(
                                            raw = element.text,
                                            previous = line.elements.getOrNull(index - 1)?.text,
                                            next = line.elements.getOrNull(index + 1)?.text
                                        )
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

    override fun close() {
        recognizers.values.forEach { it.close() }
        recognizers.clear()
    }

    companion object {
        private const val TAG = "MLKitTextScanner"

        /** Contrast multiplier around mid-grey (1.0 = unchanged). */
        private const val CONTRAST = 1.5f

        /** Strength of the Laplacian sharpening (0 = none). Too high amplifies sensor noise. */
        private const val SHARPEN_AMOUNT = 0.6f

        /**
         * Longest side, in pixels, of the image passed to ML Kit. Full camera frames (~4000px) are
         * far more than text recognition needs; 2048 keeps small price digits well above ML Kit's
         * recommended minimum character height while cutting pixel count roughly 4x.
         */
        private const val MAX_OCR_DIMENSION = 2048

        /**
         * Prefix/suffix characters to drop: everything except ASCII letters, ASCII digits and the
         * decimal/grouping separators. Removes 円, 元, 원, ¥, €, $, (税込) and similar, but keeps
         * ASCII letters so non-prices like "A4" stay malformed, and keeps separators so ".99"
         * stays malformed instead of turning into 99.
         */
        private val leadingAffix = Regex("""^[^0-9A-Za-z.,·]+""")
        private val trailingAffix = Regex("""[^0-9A-Za-z.,·]+$""")

        /**
         * NFKC-normalizes the text so full-width digits and separators (e.g. "１，０００") become ASCII,
         * then strips the currency prefix or suffix.
         */
        internal fun cleanPriceText(raw: String): String =
            Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .trim()
                .replace(leadingAffix, "")
                .replace(trailingAffix, "")

        /**
         * Heuristics for a number that parsed fine but is probably not a price. [raw] is the
         * element's text; [previous] and [next] are the neighbouring elements on the same line,
         * since ML Kit splits on whitespace ("20 %" arrives as two elements).
         *
         * Flags:
         * - percentages: "5.3%", "20 %", "8‰"
         * - a single decimal place: "5.3", "4,5" (ratings, measurements), unless a currency
         *   marker is attached ("$5.5", "5.5元")
         * - four or more decimal places: "0.1234" (coordinates, measurements)
         * - a unit or counter after the number: "500 g", "3 pcs", "25 °", "3個", "10分", "2개"
         * - a reference/quantity marker before it: "#12", "×2", "x 2", "Tel 0312", "Table 5", "第3"
         * - a leading zero on a whole number: "007", "0123" (IDs, codes)
         * - eight or more digits with no separators: barcodes, phone and order numbers
         *
         * Attached ASCII units ("500g", "2x") never get here: [cleanPriceText] keeps ASCII letters,
         * so the parser already rejects them.
         */
        internal fun isLikelyNotPrice(raw: String, previous: String?, next: String?): Boolean {
            val text = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim()
            val prefix = leadingAffix.find(text)?.value.orEmpty()
            val suffix = trailingAffix.find(text)?.value.orEmpty()
            val core = text.removePrefix(prefix).removeSuffix(suffix)
                .filterNot { it.isWhitespace() || it == '\'' || it == '’' }
            val prevToken = previous?.toToken()
            val nextToken = next?.toToken()

            val hasCurrencyMarker = (prefix + suffix).any { it.isCurrencyMarker() } ||
                prevToken?.isCurrencyToken() == true || nextToken?.isCurrencyToken() == true

            val isPercent = suffix.firstOrNull() in PERCENT_CHARS ||
                nextToken?.firstOrNull() in PERCENT_CHARS

            val hasUnitAfter = suffix.firstOrNull()?.let { it in COUNTER_CHARS || it == '°' } == true ||
                nextToken != null && (nextToken in UNIT_WORDS || nextToken.firstOrNull() in COUNTER_CHARS)

            val hasReferenceBefore = prefix.any { it in REFERENCE_PREFIX_CHARS } ||
                prevToken != null && (prevToken in REFERENCE_WORDS || prevToken.lastOrNull() == '#')

            return isPercent ||
                hasUnitAfter ||
                hasReferenceBefore ||
                (!hasCurrencyMarker && singleDecimal.containsMatchIn(core)) ||
                manyDecimals.containsMatchIn(core) ||
                leadingZero.containsMatchIn(core) ||
                (core.length >= LONG_DIGIT_RUN && core.all { it in '0'..'9' })
        }

        /** Last separator followed by exactly one digit: "5.3", "4,5", "1.234,5". */
        private val singleDecimal = Regex("""[.,·]\d$""")
        private val manyDecimals = Regex("""[.,·]\d{4,}$""")
        /** Whole numbers starting with 0 ("007"); "0.50" is not matched. */
        private val leadingZero = Regex("""^0\d""")
        private const val LONG_DIGIT_RUN = 8

        private val PERCENT_CHARS = setOf('%', '‰')

        /** CJK currency markers that are not Unicode currency symbols (¥, ₩, $ etc. are). */
        private val CJK_CURRENCY_CHARS = setOf('円', '元', '圆', '塊', '块', '원')

        private fun Char.isCurrencyMarker() =
            this in CJK_CURRENCY_CHARS || Character.getType(this) == Character.CURRENCY_SYMBOL.toInt()

        private fun String.isCurrencyToken() = isNotEmpty() && all { it.isCurrencyMarker() }

        /** Lowercased NFKC token with surrounding brackets and trailing punctuation removed. */
        private fun String.toToken(): String =
            Normalizer.normalize(this, Normalizer.Form.NFKC).trim().lowercase()
                .trimStart('(', '[')
                .trimEnd('.', ':', ';', ',', ')', ']')

        /** Units that may follow a number as a separate word (lowercase). */
        private val UNIT_WORDS = setOf(
            // weight / volume / length
            "g", "kg", "mg", "lb", "lbs", "oz", "ml", "cl", "dl", "l", "ltr", "gal",
            "mm", "cm", "m", "km", "in", "ft", "yd", "mi", "sqm", "sqft",
            // energy / electrical / data
            "cal", "kcal", "kj", "w", "kw", "kwh", "v", "mah", "hz", "ghz", "kb", "mb", "gb", "tb", "mp",
            // speed / time / temperature
            "mph", "kmh", "km/h", "rpm", "fps", "s", "sec", "min", "mins", "h", "hr", "hrs",
            "day", "days", "yr", "yrs", "°", "°c", "°f",
            // counts
            "x", "pc", "pcs", "pk", "ct", "pt", "pts", "ppm",
        )

        /** Japanese/Chinese/Korean counters and units that follow a number ("3個", "10分", "2개"). */
        private val COUNTER_CHARS = setOf(
            // Japanese / Chinese counters
            '個', '个', '本', '枚', '杯', '名', '人', '点', '件', '回', '次', '皿', '貫', '品', '玉', '冊', '台',
            '匹', '頭', '羽', '足', '着', '組', '箱', '袋', '缶', '瓶', '粒', '切', '歳', '才', '位', '份',
            // time, order, ratio, temperature, weight
            '時', '分', '秒', '日', '月', '年', '週', '号', '番', '階', '楼', '度', '倍', '割', '斤', '克',
            // Korean counters and units
            '개', '명', '병', '잔', '인', '분', '시', '년', '월', '일', '회', '층', '번',
        )

        /** Characters before a number that mark it as a reference or quantity ("#12", "×2", "第3"). */
        private val REFERENCE_PREFIX_CHARS = setOf('#', '×', '第')

        /** Words before a number that mark it as a reference or quantity (lowercase). */
        private val REFERENCE_WORDS = setOf(
            "#", "x", "×", "no", "nr", "tel", "phone", "fax", "qty", "table", "page", "p", "vol",
            "room", "rm", "gate", "seat", "order", "id", "ref", "invoice", "receipt", "item",
            "数量", "電話", "番号", "卓", "席", "第", "訂單", "수량", "전화",
        )
    }
}
