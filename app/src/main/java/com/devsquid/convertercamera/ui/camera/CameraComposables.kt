package com.devsquid.convertercamera.ui.camera

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devsquid.convertercamera.R
import com.devsquid.convertercamera.ui.CurrencyListItemData
import com.devsquid.convertercamera.ui.MODE_SET_CONVERT_CURRENCY
import com.devsquid.convertercamera.ui.MODE_SET_ORIGINAL_CURRENCY
import com.devsquid.convertercamera.ui.ZoomableImageViewer
import com.devsquid.convertercamera.ui.dialog.PermissionRequestDialog
import com.devsquid.convertercamera.ui.dialog.PermissionStatus
import com.devsquid.convertercamera.ui.dialog.SettingsDialog
import com.devsquid.convertercamera.ui.dialog.rememberPermissionState
import com.devsquid.convertercamera.ui.theme.DarkGray
import com.devsquid.convertercamera.ui.theme.Dimens
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private const val TOP_SCRIM_ALPHA = 0.5f

@Composable
fun CameraScreen(
    cameraViewModel: CameraViewModel = koinViewModel(),
    onNavigateToSelectorRoute: (Int) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit) {

    val (status, requestPermission) = rememberPermissionState(Manifest.permission.CAMERA)

    with(cameraViewModel) {
        val currencyConverterState by currencyConverterState.collectAsStateWithLifecycle()
        val capturedImage by capturedImageState.collectAsStateWithLifecycle()
        val isProcessing by isProcessing.collectAsStateWithLifecycle()
        val isAiScannerEnabled by isAiScannerEnabled.collectAsStateWithLifecycle()
        val isAiAvailable by isAiAvailable.collectAsStateWithLifecycle()

        var showOverlay by remember { mutableStateOf(true) }
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val priceTagHiddenMessage = stringResource(R.string.price_tag_hidden)
        val undoLabel = stringResource(R.string.undo)
        val noPriceTagsFoundMessage = stringResource(R.string.no_price_tags_found)
        val showImageViewerUi = !isProcessing && capturedImage != null

        LaunchedEffect(Unit) {
            noPriceTagsFound.collect {
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        message = noPriceTagsFoundMessage,
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }

        LaunchedEffect(capturedImage) {
            // A pending undo refers to the previous image, so drop it. Other snackbars
            // (e.g. "No price tags found", shown as the image is cleared) are left alone.
            snackbarHostState.currentSnackbarData
                ?.takeIf { it.visuals.message == priceTagHiddenMessage }
                ?.dismiss()
            if (capturedImage != null) {
                showOverlay = true
            }
        }

        val onPriceTagLongPressed: (OverlayText) -> Unit = { overlay ->
            // Replace any visible snackbar so Undo always targets the latest hidden tag.
            snackbarHostState.currentSnackbarData?.dismiss()
            if (overlay.hidden) {
                setOverlayHidden(overlay.id, hidden = false)
            } else {
                setOverlayHidden(overlay.id, hidden = true)
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = priceTagHiddenMessage,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        setOverlayHidden(overlay.id, hidden = false)
                    }
                }
            }
        }

        val handleOnBack = {
            if (capturedImage != null) clearCapturedImage()
            else onBack()
        }

        BackHandler(enabled = capturedImage != null) {
            handleOnBack()
        }

        when (status) {
            PermissionStatus.Granted ->
                CameraFrame(
                    onBack = handleOnBack,
                    showBackButton = capturedImage != null,
                    snackbarHostState = snackbarHostState,
                    actionButton = {
                        if (capturedImage == null) CaptureButton(onClick = ::captureImage)
                    },
                    bottomBar = {
                        ConversionBar(
                            onNavigateToSelectorRoute = onNavigateToSelectorRoute,
                            fromCurrencyData = currencyConverterState.fromCurrency,
                            toCurrencyData = currencyConverterState.toCurrencyData,
                            onSwapCurrencies = ::swapCurrencies
                        )
                    }
                ) { topBarHeight ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (capturedImage != null) {
                            val overlays by priceBoxes.collectAsState()
                            DisplayImage(
                                image = capturedImage!!,
                                overlayTexts = overlays,
                                showOverlay = showOverlay,
                                onOverlayTapped = { },
                                onOverlayLongTapped = onPriceTagLongPressed
                            )
                        }
                        if (!showImageViewerUi) {
                            // Freeze the preview from the shutter press until the captured
                            // image is finished processing
                            CameraViewer(::bindCamera, ::setZoom, frozen = isProcessing)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                // Content draws behind the top bar; keep the toggles below it.
                                .padding(top = topBarHeight + Dimens.paddingMedium)
                        ) {
                            ScannerToggleSwitch(
                                isAiEnabled = isAiScannerEnabled,
                                enabled = isAiAvailable && !isProcessing,
                                onToggle = ::setAiScannerEnabled
                            )
                            if (showImageViewerUi) {
                                OverlayToggleSwitch(
                                    showConverted = showOverlay,
                                    enabled = true,
                                    onToggle = { showOverlay = it },
                                    modifier = Modifier.padding(top = Dimens.paddingSmall)
                                )
                            }
                        }

                        if (isProcessing) {
                            LoadingOverlay()
                        }
                    }
                }

            PermissionStatus.Unknown -> LaunchedEffect(Unit) { requestPermission() }
            PermissionStatus.Denied -> RationaleDialog(
                onConfirm = requestPermission,
                onCancel = onClose
            )

            PermissionStatus.PermanentlyDenied -> SettingsDialog(onClose, onBack)
        }
    }
}

@Composable
fun CameraFrame(
    onBack: () -> Unit,
    showBackButton: Boolean = false,
    bottomBar: @Composable () -> Unit = {},
    actionButton: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    /** Drawn behind the top bar; receives the top bar's height so it can inset its own UI. */
    content: @Composable BoxScope.(topBarHeight: Dp) -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                // Always dark, regardless of the system light/dark theme.
                Snackbar(
                    snackbarData = data,
                    containerColor = DarkGray,
                    contentColor = Color.White,
                    actionColor = Color.White
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Slight scrim so the title stays readable over the camera/image.
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = TOP_SCRIM_ALPHA), Color.Transparent)
                        )
                    )
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(Dimens.paddingLarge)
                        .fillMaxWidth()
                )
                if (showBackButton) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .statusBarsPadding()
                            .padding(start = Dimens.paddingSmall)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White
                        )
                    }
                }
            }
        },
        floatingActionButton = { actionButton() },
        content = { contentPadding ->
            // Skip the top padding so the content extends behind the top bar.
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                )) {
                content(contentPadding.calculateTopPadding())
            }
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                bottomBar()
            }
        },
    )
}

@Composable
fun CaptureButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .padding(Dimens.paddingHuge)
            .height(Dimens.iconButtonSize)
            .width(Dimens.iconButtonSize)
            .background(
                color = MaterialTheme.colorScheme.secondary,
                shape = CircleShape
            )
    ) {
        Box(
            Modifier
                .height(Dimens.innerFabSize)
                .width(Dimens.innerFabSize)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                )
        )
        Icon(
            imageVector = Icons.Default.CurrencyExchange,
            contentDescription = "Capture",
            tint = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
fun ConversionBar(
    fromCurrencyData: CurrencyListItemData,
    toCurrencyData: CurrencyListItemData,
    onSwapCurrencies: () -> Unit,
    onNavigateToSelectorRoute: (Int) -> Unit
) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(Dimens.spacingMedium)) {
        CurrencyButton(
            modifier = Modifier.weight(1f),
            onClick = { onNavigateToSelectorRoute(MODE_SET_CONVERT_CURRENCY) },
            isoCode = fromCurrencyData.isoCode,
            symbol = fromCurrencyData.currencySymbol
        )
        IconButton(onClick = { onSwapCurrencies() }) {
            Icon(
                imageVector = Icons.AutoMirrored.Default.ArrowForward,
                stringResource(R.string.tooltip_swap_currencies_icon)
            )
        }
        CurrencyButton(
            modifier = Modifier.weight(1f),
            onClick = { onNavigateToSelectorRoute(MODE_SET_ORIGINAL_CURRENCY) },
            isoCode = toCurrencyData.isoCode,
            symbol = toCurrencyData.currencySymbol
        )
    }
}

@Composable
fun DisplayImage(
    image: Bitmap,
    overlayTexts: List<OverlayText>,
    showOverlay: Boolean,
    onOverlayTapped: (OverlayText) -> Unit,
    onOverlayLongTapped: (OverlayText) -> Unit
) {
    ZoomableImageViewer(
        bitmap = image,
        overlays = overlayTexts,
        showOverlay = showOverlay,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp)),
        onPriceTapped = onOverlayTapped,
        onPriceLongTapped = onOverlayLongTapped
    )
}

@Composable
fun CurrencyButton(modifier: Modifier, onClick: () -> Unit, isoCode: String, symbol: String) {
    Button(
        modifier = modifier.background(
            MaterialTheme.colorScheme.primary,
            RoundedCornerShape(Dimens.cornerRadiusLarge)
        ),
        onClick = onClick,
    ) {
        Text(
            text = "$isoCode ($symbol)",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(Dimens.paddingMedium)
        )
    }
}

@Composable
fun CameraViewer(
    bindCamera: suspend (
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) -> Unit,
    onZoom: (Float) -> Unit,
    frozen: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    // Snapshot of the preview shown on top of the live feed while frozen.
    var frozenFrame by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) { bindCamera(context, lifecycleOwner, previewView) }

    LaunchedEffect(frozen) {
        frozenFrame = if (frozen) previewView.bitmap else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(Dimens.cornerRadiusLarge))
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    zoomRatio = (zoomRatio * zoom).coerceIn(1f, 10f)
                    onZoom(zoomRatio)
                }
            }
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        frozenFrame?.let {
            // The snapshot is already view-sized, so draw it 1:1 over the preview.
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun LoadingOverlay() {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(Dimens.loadingOverlaySize)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = MaterialTheme.shapes.medium
                )
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ScannerToggleSwitch(
    isAiEnabled: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LabeledToggleSwitch(
        leftLabel = "ML Kit",
        rightLabel = "AI Enhanced",
        checked = isAiEnabled,
        enabled = enabled,
        onToggle = onToggle,
        modifier = modifier
    )
}

@Composable
fun OverlayToggleSwitch(
    showConverted: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LabeledToggleSwitch(
        leftLabel = stringResource(R.string.label_original),
        rightLabel = stringResource(R.string.label_converted),
        checked = showConverted,
        enabled = enabled,
        onToggle = onToggle,
        modifier = modifier
    )
}

@Composable
fun LabeledToggleSwitch(
    leftLabel: String,
    rightLabel: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = leftLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (!checked) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                enabled = enabled,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Text(
                text = rightLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}



@Composable
fun RationaleDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    PermissionRequestDialog(
        onConfirm = onConfirm,
        onCancel = onCancel,
        title = stringResource(R.string.dialog_camera_access_required_title),
        text = stringResource(R.string.dialog_camera_access_required_message)
    )
}

@Composable
fun SettingsDialog(onCancel: () -> Unit, onDismiss: () -> Unit) {
    SettingsDialog(
        onCancel = onCancel,
        text = stringResource(R.string.enable_camera_dialog_message)
    )
}

@Preview
@Composable
fun PreviewCamera() {
    CameraFrame(
        onBack = {},
        content = {},
        actionButton = {
            CaptureButton(onClick = {})
        },
        bottomBar = {
            ConversionBar(
                fromCurrencyData = CurrencyListItemData("YEN", "Japanese Yen"),
                toCurrencyData = CurrencyListItemData("USD", "United States Dollar"),
                onSwapCurrencies = {},
                onNavigateToSelectorRoute = {}
            )
        }
    )
}

@Preview
@Composable
fun PreviewDisplayImage() {
    CameraFrame(
        onBack = {},
        content = {},
        bottomBar = {
            ConversionBar(
                fromCurrencyData = CurrencyListItemData("YEN", "Japanese Yen"),
                toCurrencyData = CurrencyListItemData("USD", "United States Dollar"),
                onSwapCurrencies = {},
                onNavigateToSelectorRoute = {}
            )
        }
    )
}