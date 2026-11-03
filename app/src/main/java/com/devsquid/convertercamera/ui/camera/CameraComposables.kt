package com.devsquid.convertercamera.ui.camera

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
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
import com.devsquid.convertercamera.ui.theme.Dimens
import org.koin.androidx.compose.koinViewModel

@Composable
fun CameraScreen(
    cameraViewModel: CameraViewModel = koinViewModel(),
    onNavigateToSelectorRoute: (Int) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit) {

    val (status, requestPermission) = rememberPermissionState(Manifest.permission.CAMERA)

    with (cameraViewModel) {
        val currencyConverterState by currencyConverterState.collectAsStateWithLifecycle()
        val capturedImage by capturedImageState.collectAsStateWithLifecycle()
        val isProcessing by isProcessing.collectAsStateWithLifecycle()
        val canUndo by canUndo.collectAsStateWithLifecycle()

        var showOverlay by remember { mutableStateOf(true) }

        LaunchedEffect(capturedImage) {
            if (capturedImage != null) {
                showOverlay = true
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
                    actionButton = {
                        if (capturedImage == null) CaptureButton(onClick = ::captureImage)
                        else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (canUndo) UndoButton(::undoHideOverlay)
                                ToggleOverlayButton(
                                    showOverlay = showOverlay,
                                    onClick = { showOverlay = !showOverlay }
                                )
                            }
                        }
                    },
                    bottomBar = {
                        ConversionBar(
                            onNavigateToSelectorRoute = onNavigateToSelectorRoute,
                            fromCurrencyData = currencyConverterState.fromCurrency,
                            toCurrencyData = currencyConverterState.toCurrencyData,
                            onSwapCurrencies = ::swapCurrencies
                        )
                    }
                ) {
                    if (capturedImage != null) {
                        val overlays by priceBoxes.collectAsState()
                        DisplayImage(
                            image = capturedImage!!,
                            overlayTexts = overlays,
                            showOverlay = showOverlay,
                            onOverlayTapped = { },
                            onOverlayLongTapped = { hideOverlayEntry(it.id) }
                        )
                    } else {
                        CameraViewer(::bindCamera, ::setZoom)
                    }

                    if (isProcessing) {
                        LoadingOverlay()
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
    bottomBar: @Composable () -> Unit = {},
    actionButton: @Composable () -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButtonPosition = FabPosition.Center,
        topBar = {
            Text(
                text = "Converter Camera",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(Dimens.paddingLarge)
                    .fillMaxWidth()
            )
        },
        floatingActionButton = { actionButton() },
        content = { contentPadding ->
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)) {
                content()
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
fun UndoButton(onClick: () -> Unit) {
    Button(
        onClick = { onClick() },
        modifier = Modifier.padding(Dimens.paddingMedium),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary
        )
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Default.Undo,
            contentDescription = "Undo",
            tint = MaterialTheme.colorScheme.onSecondary
        )
        Text(
            text = "Undo",
            color = MaterialTheme.colorScheme.onSecondary
        )
    }
}

@Composable
fun ToggleOverlayButton(showOverlay: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.padding(Dimens.paddingMedium),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary
        )
    ) {
        Icon(
            imageVector = if (showOverlay) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = if (showOverlay) "Converted" else "Original",
            tint = MaterialTheme.colorScheme.onSecondary
        )
        Text(
            text = if (showOverlay) "Hide" else "Show",
            color = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.padding(start = Dimens.paddingSmall)
        )
    }
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
            name = fromCurrencyData.name
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
            name = toCurrencyData.name
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
fun CurrencyButton(modifier: Modifier, onClick: () -> Unit, isoCode: String, name: String) {
    Button(
        modifier = modifier.background(
            MaterialTheme.colorScheme.primary,
            RoundedCornerShape(Dimens.cornerRadiusLarge)
        ),
        onClick = onClick,
    ) {
        Text(text = isoCode, modifier = Modifier.padding(Dimens.paddingMedium))
    }
}

@Composable
fun CameraViewer(
    bindCamera: suspend (
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) -> Unit,
    onZoom: (Float) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(Unit) { bindCamera(context, lifecycleOwner, previewView) }

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
        actionButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UndoButton(onClick = {})
                ToggleOverlayButton(showOverlay = true, onClick = {})
            }
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

