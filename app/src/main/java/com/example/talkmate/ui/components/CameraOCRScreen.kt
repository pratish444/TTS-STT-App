package com.example.talkmate.ui.components

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.talkmate.utils.OCRHelper
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraOCRScreen(
    onTextExtracted: (String) -> Unit,
    onClose: () -> Unit,
    onSpeakText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val scope = rememberCoroutineScope()

    var extractedText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Camera use cases
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val ocrHelper = remember { OCRHelper() }
    val executor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    if (cameraPermissionState.status.isGranted) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.background(
                        Color.Black.copy(alpha = 0.5f),
                        CircleShape
                    )
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }

                Text(
                    text = "Scan Text",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(48.dp))
            }

            // Camera preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                CameraPreview(
                    onImageCaptureReady = { capture ->
                        imageCapture = capture
                        Log.d("CameraOCR", "ImageCapture ready")
                    },
                    onError = { error ->
                        errorMessage = error
                        Log.e("CameraOCR", "Camera error: $error")
                    }
                )

                // Overlay for scan guidance
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 4.dp.toPx()
                    val cornerLength = 40.dp.toPx()
                    val rectSize = size.width * 0.8f
                    val rectTop = (size.height - rectSize) / 2
                    val rectLeft = (size.width - rectSize) / 2

                    val cornerColor = Color.White

                    // Draw corner brackets (top-left, top-right, bottom-left, bottom-right)
                    drawLine(cornerColor, androidx.compose.ui.geometry.Offset(rectLeft, rectTop),
                        androidx.compose.ui.geometry.Offset(rectLeft + cornerLength, rectTop), strokeWidth)
                    drawLine(cornerColor, androidx.compose.ui.geometry.Offset(rectLeft, rectTop),
                        androidx.compose.ui.geometry.Offset(rectLeft, rectTop + cornerLength), strokeWidth)

                    drawLine(cornerColor, androidx.compose.ui.geometry.Offset(rectLeft + rectSize - cornerLength, rectTop),
                        androidx.compose.ui.geometry.Offset(rectLeft + rectSize, rectTop), strokeWidth)
                    drawLine(cornerColor, androidx.compose.ui.geometry.Offset(rectLeft + rectSize, rectTop),
                        androidx.compose.ui.geometry.Offset(rectLeft + rectSize, rectTop + cornerLength), strokeWidth)

                    drawLine(cornerColor, androidx.compose.ui.geometry.Offset(rectLeft, rectTop + rectSize - cornerLength),
                        androidx.compose.ui.geometry.Offset(rectLeft, rectTop + rectSize), strokeWidth)
                    drawLine(cornerColor, androidx.compose.ui.geometry.Offset(rectLeft, rectTop + rectSize),
                        androidx.compose.ui.geometry.Offset(rectLeft + cornerLength, rectTop + rectSize), strokeWidth)

                    drawLine(cornerColor, androidx.compose.ui.geometry.Offset(rectLeft + rectSize, rectTop + rectSize - cornerLength),
                        androidx.compose.ui.geometry.Offset(rectLeft + rectSize, rectTop + rectSize), strokeWidth)
                    drawLine(cornerColor, androidx.compose.ui.geometry.Offset(rectLeft + rectSize - cornerLength, rectTop + rectSize),
                        androidx.compose.ui.geometry.Offset(rectLeft + rectSize, rectTop + rectSize), strokeWidth)
                }

                // Processing indicator
                if (isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Processing image...",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            // Bottom section
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Position text within the frame and tap the camera button",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Extracted text display
                    if (extractedText.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Extracted Text:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = extractedText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    OutlinedButton(onClick = { onSpeakText(extractedText) }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Speak Text")
                                    }

                                    FilledTonalButton(onClick = { extractedText = "" }) {
                                        Text("Clear")
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Error message
                    if (errorMessage.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = errorMessage,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Capture button
                    FloatingActionButton(
                        onClick = {
                            imageCapture?.let { capture ->
                                isProcessing = true
                                errorMessage = ""

                                capture.takePicture(
                                    executor,
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                            scope.launch {
                                                try {
                                                    val bitmap = imageProxy.toBitmap()
                                                    val rotated = bitmap.rotate(imageProxy.imageInfo.rotationDegrees.toFloat())

                                                    val result = ocrHelper.extractTextFromBitmap(rotated)

                                                    result.onSuccess { text ->
                                                        extractedText = text
                                                        onTextExtracted(text)
                                                        errorMessage = ""
                                                    }.onFailure { error ->
                                                        errorMessage = "No text found. Try again."
                                                        Log.e("CameraOCR", "OCR failed: ${error.message}")
                                                    }
                                                } catch (e: Exception) {
                                                    errorMessage = "Failed to process image: ${e.message}"
                                                    Log.e("CameraOCR", "Processing error", e)
                                                } finally {
                                                    imageProxy.close()
                                                    isProcessing = false
                                                }
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            errorMessage = "Capture failed: ${exception.message}"
                                            isProcessing = false
                                            Log.e("CameraOCR", "Capture error", exception)
                                        }
                                    }
                                )
                            } ?: run {
                                errorMessage = "Camera not ready. Please wait."
                            }
                        },
                        modifier = Modifier.size(64.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            Icons.Default.Camera,
                            contentDescription = "Capture",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    } else {
        // Permission not granted UI
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Default.Camera,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera Permission Required",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please grant camera permission to scan text from images",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                    Text("Grant Permission")
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onClose) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    onImageCaptureReady: (ImageCapture) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build()

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )

                    onImageCaptureReady(imageCapture)
                    Log.d("CameraPreview", "Camera initialized successfully")
                } catch (e: Exception) {
                    onError("Failed to start camera: ${e.message}")
                    Log.e("CameraPreview", "Camera init error", e)
                }
            }, executor)

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

// Extension function to convert ImageProxy to Bitmap
private fun ImageProxy.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

// Extension function to rotate bitmap
private fun Bitmap.rotate(degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}