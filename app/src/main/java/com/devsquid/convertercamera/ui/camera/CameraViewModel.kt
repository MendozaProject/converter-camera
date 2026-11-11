package com.devsquid.convertercamera.ui.camera


import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devsquid.convertercamera.business.PriceConverter
import com.devsquid.convertercamera.business.TextScanner
import com.devsquid.convertercamera.business.format
import com.devsquid.convertercamera.database.SessionRepository
import com.devsquid.convertercamera.ui.CurrencyListItemData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

class CameraViewModel(
    private val sessionRepository: SessionRepository,
    private val textScanner: TextScanner,
    private val priceConverter: PriceConverter,
    private val camera: Camera
): ViewModel() {

    private val _currencyConverterState = MutableStateFlow(CurrencyConverterState())
    val currencyConverterState = _currencyConverterState.asStateFlow()

    private val _overlays = MutableStateFlow(emptyList<OverlayText>())
    val priceBoxes: StateFlow<List<OverlayText>> = _overlays.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _capturedImageState = MutableStateFlow<Bitmap?>(null)
    val capturedImageState = _capturedImageState.asStateFlow()

    private val hiddenOverlayIds = ArrayDeque<String>()

    init {
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
        textScanner.close()
    }

    fun swapCurrencies() {
        sessionRepository.swapCurrency()
    }

    fun clearCapturedImage() {
        _capturedImageState.update { null }
        hiddenOverlayIds.clear()
        _canUndo.update { false }
    }

    fun setZoom(zoomRatio: Float) {
        camera.setZoom(zoomRatio)
    }

    fun captureImage() {
        viewModelScope.launch {
            _isProcessing.update { true }
            camera.captureImage()?.let { image ->
                val rotationDegrees = image.imageInfo.rotationDegrees
                val rawBitmap = image.toBitmap()
                val bitmap = if (rotationDegrees != 0) {
                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    Bitmap.createBitmap(
                        rawBitmap,
                        0,
                        0,
                        rawBitmap.width,
                        rawBitmap.height,
                        matrix,
                        true
                    )
                } else {
                    rawBitmap
                }
                _capturedImageState.update { bitmap }
                analyzeImage(image)

                _isProcessing.update { false }
            }
        }
    }

    fun refreshData(scannedTextData: List<OverlayText>): List<OverlayText> {
        val newTextData = mutableListOf<OverlayText>()
        scannedTextData.forEach {
            newTextData.add(
                it.copy(
                    replacementText = priceConverter
                        .convertPrice(it.originalValue)
                        .format(priceConverter.toIsoCode())
                )
            )
        }
        return newTextData
    }

    private suspend fun analyzeImage(image: ImageProxy) {
        _overlays.update {
            textScanner.recognizePrices(image).map {
                OverlayText(
                    id = it.id,
                    rectInBitmapCoords = it.rect,
                    originalText = it.originalText,
                    replacementText = it.replacementText,
                    originalValue = it.originalValue,
                    converted = true
                )
            }
        }
    }

    fun hideOverlayEntry(id: String) {
        hiddenOverlayIds.addLast(id)
        _canUndo.update { true }
        _overlays.update { boxes ->
            boxes.map {
                if (it.id == id) {
                    it.copy(hidden = true)
                } else {
                    it
                }
            }
        }
    }

    fun undoHideOverlay() {
        hiddenOverlayIds.removeLastOrNull()?.let { id ->
            _canUndo.update { hiddenOverlayIds.isNotEmpty() }
            _overlays.update { boxes ->
                boxes.map {
                    if (it.id == id) {
                        it.copy(hidden = false)
                    } else {
                        it
                    }
                }
            }
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
    }
}

data class OverlayText(
    val id: String,
    val rectInBitmapCoords: RectF,
    val originalValue: BigDecimal,
    val originalText: String,
    val replacementText: String,
    val converted: Boolean,
    val hidden: Boolean = false
)