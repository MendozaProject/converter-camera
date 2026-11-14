package com.devsquid.convertercamera.ui.camera


import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devsquid.convertercamera.business.AITextScanner
import com.devsquid.convertercamera.business.FrameVoter
import com.devsquid.convertercamera.business.HybridScanner
import com.devsquid.convertercamera.business.MLKitTextScanner
import com.devsquid.convertercamera.business.PriceConverter
import com.devsquid.convertercamera.business.TextScanner
import com.devsquid.convertercamera.business.TextScanner.ScannedTextData
import com.devsquid.convertercamera.business.format
import com.devsquid.convertercamera.business.isPossibleYenSignMisread
import com.devsquid.convertercamera.database.SessionRepository
import com.devsquid.convertercamera.ui.CurrencyListItemData
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

class CameraViewModel(
    private val sessionRepository: SessionRepository,
    private val mlKitTextScanner: MLKitTextScanner,
    private val aiTextScanner: HybridScanner,
    private val generativeModel: GenerativeModel,
    private val priceConverter: PriceConverter,
    private val camera: Camera
): ViewModel() {

    private val _currencyConverterState = MutableStateFlow(CurrencyConverterState())
    val currencyConverterState = _currencyConverterState.asStateFlow()

    private val _isAiScannerEnabled = MutableStateFlow(false)
    val isAiScannerEnabled: StateFlow<Boolean> = _isAiScannerEnabled.asStateFlow()

    private val _isAiAvailable = MutableStateFlow(false)
    val isAiAvailable: StateFlow<Boolean> = _isAiAvailable.asStateFlow()

    fun setAiScannerEnabled(enabled: Boolean) {
        if (_isAiScannerEnabled.value == enabled) return
        _isAiScannerEnabled.update { enabled }
        // Load Gemini Nano now so the first AI scan doesn't pay the model load cost.
        if (enabled) viewModelScope.launch { aiTextScanner.warmup() }
        _capturedImageState.value?.let { rescan(it, useAi = enabled) }
    }

    private val _overlays = MutableStateFlow(emptyList<OverlayText>())
    val priceBoxes: StateFlow<List<OverlayText>> = _overlays.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _capturedImageState = MutableStateFlow<Bitmap?>(null)
    val capturedImageState = _capturedImageState.asStateFlow()

    /** One-shot event: a capture was processed but no price tags were recognized. */
    private val _noPriceTagsFound = Channel<Unit>(Channel.BUFFERED)
    val noPriceTagsFound: Flow<Unit> = _noPriceTagsFound.receiveAsFlow()


    init {
        viewModelScope.launch {
            try {
                val status = generativeModel.checkStatus()
                @Suppress("DEPRECATION")
                val structuredOutput = generativeModel.isStructuredOutputFeatureAvailable() == true
                val available = status == FeatureStatus.AVAILABLE && structuredOutput
                _isAiAvailable.update { available }
                if (!available) {
                    _isAiScannerEnabled.update { false }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking AI availability", e)
                _isAiAvailable.update { false }
                _isAiScannerEnabled.update { false }
            }
        }

        viewModelScope.launch {
            sessionRepository.currencyDataState.collect { state ->
                if (state?.fromCurrencyData != null && state.toCurrencyData != null) {
                    _currencyConverterState.update {
                        CurrencyConverterState(
                            fromCurrency = CurrencyListItemData(
                                isoCode = state.fromCurrencyData.isoCode,
                                name = state.fromCurrencyData.name,
                                currencySymbol = state.fromCurrencyData.symbol,
                            ),
                            toCurrencyData = CurrencyListItemData(
                                isoCode = state.toCurrencyData.isoCode,
                                name = state.toCurrencyData.name,
                                currencySymbol = state.toCurrencyData.symbol
                            )
                        )
                    }
                    _capturedImageState.let {
                        _overlays.update { refreshData(it) }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        mlKitTextScanner.close()
        aiTextScanner.close()
    }

    /**
     * Re-runs recognition on the currently displayed image with the selected scanner. Only the
     * displayed frame is kept after capture, so this is a single-frame scan (no burst voting).
     */
    private fun rescan(image: Bitmap, useAi: Boolean) {
        viewModelScope.launch {
            _isProcessing.update { true }
            try {
                val scanner = if (useAi) aiTextScanner else mlKitTextScanner
                val result = recognize { scanner.recognizePrices(image) }
                // Ignore the result if the image was cleared or replaced while scanning.
                if (_capturedImageState.value !== image) return@launch
                publishVoted(listOf(result))
            } finally {
                _isProcessing.update { false }
            }
        }
    }


    fun swapCurrencies() {
        sessionRepository.swapCurrency()
    }

    fun clearCapturedImage() {
        _capturedImageState.update { null }
        _overlays.update { emptyList() }
    }

    fun setZoom(zoomRatio: Float) {
        camera.setZoom(zoomRatio)
    }

    /**
     * Captures a short burst and votes on the recognized prices across frames, so a sporadic
     * misread in one frame (e.g. "￥74" read as "474") is corrected by the others. The first
     * frame is the one displayed, as soon as it has been captured; overlay boxes are in its
     * coordinates and appear once processing finishes. The AI scanner is slow, so it uses a
     * single frame.
     *
     * Captures are sequential (ImageCapture takes one picture at a time), but each frame's OCR
     * starts in its own coroutine as soon as it is captured, overlapping with the next capture.
     * Voting runs once every frame has finished.
     */
    fun captureImage() {
        viewModelScope.launch {
            _isProcessing.update { true }
            try {
                val useAi = _isAiScannerEnabled.value
                val scanner = if (useAi) aiTextScanner else mlKitTextScanner
                val frameCount = if (useAi) 1 else BURST_FRAME_COUNT

                val frames = coroutineScope {
                    val pending = mutableListOf<Deferred<List<ScannedTextData>>>()
                    for (i in 0 until frameCount) {
                        val image = try {
                            camera.captureImage()
                        } catch (e: ImageCaptureException) {
                            Log.e(TAG, "Capture $i of $frameCount failed", e)
                            null
                        } ?: break
                        // Show frame 0 right away; overlays are added once processing finishes.
                        if (i == 0) _capturedImageState.update { image.toUprightBitmap() }
                        pending += async { recognize(scanner, image) }
                    }
                    // awaitAll keeps capture order, so frame 0 stays the voting reference.
                    pending.awaitAll()
                }
                if (frames.isNotEmpty()) publishVoted(frames)
                // An image was captured but nothing was recognized on it.
                if (_capturedImageState.value != null && _overlays.value.isEmpty()) {
                    _noPriceTagsFound.send(Unit)
                }
            } finally {
                _isProcessing.update { false }
            }
        }
    }

    private fun ImageProxy.toUprightBitmap(): Bitmap {
        val rotationDegrees = imageInfo.rotationDegrees
        val rawBitmap = toBitmap()
        if (rotationDegrees == 0) return rawBitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
    }

    fun refreshData(scannedTextData: List<OverlayText>): List<OverlayText> {
        val newTextData = mutableListOf<OverlayText>()
        scannedTextData.forEach {
            newTextData.add(
                it.copy(
                    replacementText = priceConverter
                        .convertPrice(it.originalValue)
                        .format(priceConverter.toIsoCode()),
                    // The source currency may have changed (e.g. swapped), so re-check.
                    possibleMisread = isPossibleYenSignMisread(it.originalValue, priceConverter.fromIsoCode())
                )
            )
        }
        return newTextData
    }

    private suspend fun recognize(scanner: TextScanner, image: ImageProxy): List<ScannedTextData> =
        recognize { scanner.recognizePrices(image) }

    private suspend fun recognize(block: suspend () -> List<ScannedTextData>): List<ScannedTextData> =
        try {
            block()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error recognizing prices", e)
            emptyList()
        }

    private fun publishVoted(frames: List<List<ScannedTextData>>) {
        val fromIsoCode = priceConverter.fromIsoCode()
        val voted = FrameVoter(minVotes = frames.size / 2 + 1).vote(
            frames.map { frame ->
                frame.map {
                    FrameVoter.Detection(
                        box = FrameVoter.Box(it.rect.left, it.rect.top, it.rect.right, it.rect.bottom),
                        value = it.originalValue,
                        payload = it
                    )
                }
            }
        )
        _overlays.update {
            voted.map { (box, data, _) ->
                OverlayText(
                    id = data.id,
                    rectInBitmapCoords = RectF(box.left, box.top, box.right, box.bottom),
                    originalText = data.originalText,
                    replacementText = data.replacementText,
                    originalValue = data.originalValue,
                    converted = true,
                    hidden = data.likelyNotPrice,
                    possibleMisread = isPossibleYenSignMisread(data.originalValue, fromIsoCode)
                )
            }
        }
    }

    fun setOverlayHidden(id: String, hidden: Boolean) {
        _overlays.update { boxes ->
            boxes.map { if (it.id == id) it.copy(hidden = hidden) else it }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    suspend fun bindCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        camera.bindCamera(context, lifecycleOwner, previewView)
    }

    data class CurrencyConverterState(
        val fromCurrency: CurrencyListItemData = CurrencyListItemData(),
        val toCurrencyData: CurrencyListItemData = CurrencyListItemData()
    )

    companion object {
        private const val TAG = "CameraViewModel"

        /** Captures per tap for the ML Kit scanner; an odd count avoids tied votes. */
        private const val BURST_FRAME_COUNT = 3
    }
}

data class OverlayText(
    val id: String,
    val rectInBitmapCoords: RectF,
    val originalValue: BigDecimal,
    val originalText: String,
    val replacementText: String,
    val converted: Boolean,
    val hidden: Boolean = false,
    /** The value may contain a misread ¥ (see [isPossibleYenSignMisread]); drawn in red. */
    val possibleMisread: Boolean = false
)