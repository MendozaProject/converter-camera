package com.devsquid.convertercamera.ui.camera

import android.content.Context
import androidx.annotation.OptIn
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalGetImage::class)
class Camera(private val executor: Executor) {

    private var imageCapture: ImageCapture? = null
    private var cameraControl: CameraControl? = null

    fun setZoom(zoomRatio: Float) {
        cameraControl?.setZoomRatio(zoomRatio)
    }

    suspend fun bindCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        val cameraProvider = ProcessCameraProvider.awaitInstance(context)
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
        this.imageCapture = imageCapture
        val preview = Preview.Builder()
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }
        cameraProvider.unbindAll()
        val camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageCapture
        )
        this.cameraControl = camera.cameraControl
    }

    suspend fun captureImage(): ImageProxy? {
        if (imageCapture == null) return null
        return imageCapture!!.takePictureSuspend()
    }

    private suspend fun ImageCapture.takePictureSuspend(): ImageProxy =
        suspendCancellableCoroutine { cont ->
            takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    if (cont.isActive) cont.resume(imageProxy)
                }

                override fun onError(exception: ImageCaptureException) {
                    if (cont.isActive) cont.resumeWithException(exception)
                }
            })
        }

    companion object {
        private const val TAG = "Camera"
    }
}