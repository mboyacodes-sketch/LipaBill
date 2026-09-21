package com.lipabill.app.ui.tickets

import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Space
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "LipaBill.QrScan"

/**
 * In-app portrait QR scanner using CameraX (back camera) + ML Kit.
 * Does not rotate the activity or flip to a separate capture screen.
 */
@Composable
fun QrScannerScreen(
    onResult: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val handled = remember { AtomicBoolean(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    BackHandler(onBack = onCancel)

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_PDF417,
                    Barcode.FORMAT_AZTEC,
                    Barcode.FORMAT_DATA_MATRIX
                )
                .build()
        )
    }

    DisposableEffect(scanner) {
        onDispose { scanner.close() }
    }

    LaunchedEffect(previewView, lifecycleOwner) {
        val view = previewView ?: return@LaunchedEffect
        try {
            val cameraProvider = awaitCameraProvider(context)
            cameraProvider.unbindAll()

            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()
                .also { it.surfaceProvider = view.surfaceProvider }

            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                if (handled.get()) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val value = barcodes
                            .asSequence()
                            .mapNotNull { it.rawValue?.trim() }
                            .firstOrNull { it.isNotEmpty() }
                        if (value != null && handled.compareAndSet(false, true)) {
                            view.post { onResult(value) }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "barcode_scan_fail", e)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            }

            val selector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                analysis
            )
            cameraError = null
        } catch (t: Throwable) {
            Log.e(TAG, "camera_bind_failed", t)
            cameraError = t.message ?: "Couldn’t open the rear camera"
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }.also { previewView = it }
            }
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val box = size.minDimension * 0.68f
            val left = (size.width - box) / 2f
            val top = (size.height - box) / 2.4f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.92f),
                topLeft = Offset(left, top),
                size = Size(box, box),
                cornerRadius = CornerRadius(28f, 28f),
                style = Stroke(width = 5f)
            )
        }

        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(Space.page)
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Close",
                tint = CardWhite
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(Space.page)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                .padding(Space.card),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Rear camera · hold steady on the QR",
                style = HomeType.rowTitle,
                color = CardWhite,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Space.tight))
            Text(
                text = cameraError ?: "Portrait locked — no rotation",
                style = HomeType.caption,
                color = CardWhite.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

private suspend fun awaitCameraProvider(context: android.content.Context): ProcessCameraProvider {
    val future = ProcessCameraProvider.getInstance(context)
    return suspendCancellableCoroutine { cont ->
        future.addListener(
            {
                try {
                    cont.resume(future.get())
                } catch (t: Throwable) {
                    cont.resumeWithException(t)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }
}
