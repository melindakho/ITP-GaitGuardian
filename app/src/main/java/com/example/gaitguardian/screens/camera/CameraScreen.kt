package com.example.gaitguardian.screens.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.app.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.gaitguardian.data.roomDatabase.tug.TUGAssessment
import com.example.gaitguardian.viewmodels.CameraViewModel
import com.example.gaitguardian.viewmodels.TugDataViewModel
import kotlinx.coroutines.delay
import java.io.File
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.collections.isNotEmpty


/**
 * Camera Screen - Video Recording of TUG Assessment
 * ML Kit Pose Detection
 * Three-Phase Sequential Validation Process
 * 1. Check for Device Orientation - Ensure it is always in Landscape
 * 2. Check for 3 metres space  - Ensure the user is in a 3 metre space , Boundary Box included to indicate if user detected
 * 3. Check Environment Lighting - Ensure the environment is well-lit
 * Stores recorded Video into local storage
 * Insert database entry storing the patient's pre-assessment information + absolute path of video
 */
enum class devOrientation {
    PORTRAIT, LANDSCAPE_LEFT, LANDSCAPE_RIGHT, PORTRAIT_UPSIDE_DOWN
}
data class devOrientationState (
    val orientation: devOrientation,
    val lockedLandscape: Boolean
)

// WORKING BOUNDARY BOX
@Composable
fun BoundingBoxOverlay(
    box: Rect?,
    imageSize: Triple<Int, Int, Int>, // width, height, rotation
    previewView: PreviewView,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        box?.let {
            val (imageWidth, imageHeight, rotation) = imageSize

            // Get preview view dimensions
            val viewWidth = size.width
            val viewHeight = size.height

            if (imageWidth == 0 || imageHeight == 0) return@let

            // Transform coordinates from image space to view space
            val transformedRect = transformRect(
                rect = it,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                viewWidth = viewWidth.toInt(),
                viewHeight = viewHeight.toInt(),
                rotation = rotation,
                scaleType = previewView.scaleType
            )

            drawRect(
                color = Color.Red,
                topLeft = Offset(transformedRect.left.toFloat(), transformedRect.top.toFloat()),
                size = Size(
                    transformedRect.width().toFloat(),
                    transformedRect.height().toFloat()
                ),
                style = Stroke(width = 4f)
            )
        }
    }
}
//// Helper function to transform coordinates
fun transformRect(
    rect: Rect,
    imageWidth: Int,
    imageHeight: Int,
    viewWidth: Int,
    viewHeight: Int,
    rotation: Int,
    scaleType: PreviewView.ScaleType
): Rect {
    // Step 1: Handle rotation
    val (rotatedRect, rotatedImageWidth, rotatedImageHeight) = when (rotation) {
        90, 270 -> {
            // Swap dimensions and transform coordinates
            val newRect = if (rotation == 90) {
                Rect(
                    rect.top,
                    imageWidth - rect.right,
                    rect.bottom,
                    imageWidth - rect.left
                )
            } else { // 270
                Rect(
                    imageHeight - rect.bottom,
                    rect.left,
                    imageHeight - rect.top,
                    rect.right
                )
            }
            Triple(newRect, imageHeight, imageWidth)
        }
        180 -> {
            val newRect = Rect(
                imageWidth - rect.right,
                imageHeight - rect.bottom,
                imageWidth - rect.left,
                imageHeight - rect.top
            )
            Triple(newRect, imageWidth, imageHeight)
        }
        else -> Triple(rect, imageWidth, imageHeight)
    }

    // Step 2: Calculate scale factor based on scale type
    val (scaleX, scaleY, offsetX, offsetY) = when (scaleType) {
        PreviewView.ScaleType.FILL_CENTER -> {
            // Fill mode: scale to fill, crop the excess
            val scale = maxOf(
                viewWidth.toFloat() / rotatedImageWidth,
                viewHeight.toFloat() / rotatedImageHeight
            )
            val scaledWidth = rotatedImageWidth * scale
            val scaledHeight = rotatedImageHeight * scale
            val offsetX = (viewWidth - scaledWidth) / 2f
            val offsetY = (viewHeight - scaledHeight) / 2f
            arrayOf(scale, scale, offsetX, offsetY)
        }
        PreviewView.ScaleType.FIT_CENTER -> {
            // Fit mode: scale to fit, letterbox if needed
            val scale = minOf(
                viewWidth.toFloat() / rotatedImageWidth,
                viewHeight.toFloat() / rotatedImageHeight
            )
            val scaledWidth = rotatedImageWidth * scale
            val scaledHeight = rotatedImageHeight * scale
            val offsetX = (viewWidth - scaledWidth) / 2f
            val offsetY = (viewHeight - scaledHeight) / 2f
            arrayOf(scale, scale, offsetX, offsetY)
        }
        else -> {
            // FIT_START, FIT_END, FILL_START, FILL_END - similar logic
            val scale = viewWidth.toFloat() / rotatedImageWidth
            arrayOf(scale, scale, 0f, 0f)
        }
    }

    // Step 3: Transform the rectangle
    val transformedLeft = (rotatedRect.left * scaleX + offsetX).toInt()
    val transformedTop = (rotatedRect.top * scaleY + offsetY).toInt()
    val transformedRight = (rotatedRect.right * scaleX + offsetX).toInt()
    val transformedBottom = (rotatedRect.bottom * scaleY + offsetY).toInt()

    return Rect(transformedLeft, transformedTop, transformedRight, transformedBottom)
}
// END OF BOUNDARY BOX WORKING

@Composable
fun rembDeviceOrientation(): devOrientationState {
    val context = LocalContext.current
    var orientation by remember { mutableStateOf(devOrientation.PORTRAIT) }

    var isListenerEnabled by remember { mutableStateOf(true) }

    DisposableEffect(context, isListenerEnabled) {
        if (!isListenerEnabled) return@DisposableEffect onDispose { }

        val orientationEventListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientationDegrees: Int) {
                if (orientationDegrees == ORIENTATION_UNKNOWN) return

                val newOrientation = when (orientationDegrees) {
                    in 315..360, in 0..45 -> devOrientation.PORTRAIT
                    in 45..135 -> devOrientation.LANDSCAPE_LEFT
                    in 135..225 -> devOrientation.PORTRAIT_UPSIDE_DOWN
                    in 225..315 -> devOrientation.LANDSCAPE_RIGHT
                    else -> orientation
                }

                if (newOrientation != orientation) {
                    orientation = newOrientation
                }
            }
        }

        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable()
        }

        onDispose {
            orientationEventListener.disable()
        }
    }

    var lockedLandscape by remember { mutableStateOf(false) }

    LaunchedEffect(orientation) {
        if (!lockedLandscape &&
            (orientation == devOrientation.LANDSCAPE_LEFT || orientation == devOrientation.LANDSCAPE_RIGHT)
        ) {
            delay(5000)
            // Check if still in landscape
            if (orientation == devOrientation.LANDSCAPE_LEFT || orientation == devOrientation.LANDSCAPE_RIGHT) {
                lockedLandscape = true // lock UI
                Log.d("oriListener", "Locked to landscape")
            }
        }
    }
    return devOrientationState(orientation, lockedLandscape)
}

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
@SuppressLint("RestrictedApi")
@Composable
fun CameraScreen(viewModel: CameraViewModel = viewModel(), navController: NavController, tugViewModel: TugDataViewModel) {
    val cameraPhase by viewModel.cameraPhase.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }

    val deviceOrientation = rembDeviceOrientation()
    val isDeviceLandscape =
        deviceOrientation.lockedLandscape ||
                deviceOrientation.orientation == devOrientation.LANDSCAPE_LEFT ||
                deviceOrientation.orientation == devOrientation.LANDSCAPE_RIGHT

    val box = viewModel.boundingBox.collectAsState().value
    val imageSize = viewModel.imageSize.collectAsState().value

    // Upload video picker
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        // Copy picked video to app's external movies dir so it has a real file path
        val outputFile = File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES),
            "uploaded-video-${System.currentTimeMillis()}.mp4"
        )
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                outputFile.outputStream().use { output -> input.copyTo(output) }
            }
            val assessmentId = UUID.randomUUID().toString()
            val newTug = TUGAssessment(
                testId = assessmentId,
                dateTime = Date().time,
                videoDuration = 0f, // unknown at upload time
                videoTitle = outputFile.absolutePath,
                onMedication = tugViewModel.onMedication.value,
                patientComments = tugViewModel.selectedComments.value.joinToString(", ")
            )
            tugViewModel.insertNewAssessment(newTug)
            Toast.makeText(context, "Video uploaded, processing…", Toast.LENGTH_SHORT).show()
            navController.navigate("loading_screen")
        } catch (e: Exception) {
            Log.e("UploadVideo", "Failed to copy video", e)
            Toast.makeText(context, "Failed to load video: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isDeviceLandscape) {
            // Camera preview
            AndroidView({ previewView }, modifier = Modifier.fillMaxSize())

            // Overlays
            when (cameraPhase) {
                CameraViewModel.CameraPhase.CheckingDistance -> {
                    DistanceTestOverlay(viewModel)
                    BoundingBoxOverlay(box = box, imageSize = imageSize, previewView = previewView)
                }

                CameraViewModel.CameraPhase.CheckingLuminosity -> {
                    LuminosityCheckOverlay(viewModel)
                }

                CameraViewModel.CameraPhase.VideoRecording -> {
                    VideoRecordingOverlay(
                        navController = navController,
                        tugDataViewModel = tugViewModel,
                        recording = recording,
                        isRecording = isRecording,
                        onRecordingStateChange = { rec, isRec ->
                            recording = rec
                            isRecording = isRec
                        },
                        context = context,
                        lifecycleOwner = lifecycleOwner,
                        videoCapture = videoCapture,
                        onRecordingFinished = { uri ->
                            Log.d("App", "Video saved: $uri")
                            viewModel.resetToDistanceCheck()
                        },
                        deviceOrientation = deviceOrientation
                    )
                }
            }
        
            // Upload button
            UploadVideoButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                onClick = { videoPicker.launch("video/*") }
            )

        } else { // In Portrait
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ScreenRotation,
                    contentDescription = "Rotate Screen",
                    modifier = Modifier.size(64.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Rotate to Landscape",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Turn your device sideways to access the camera.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                UploadVideoButton(onClick = { videoPicker.launch("video/*") })
            }
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                context as ComponentActivity,
                arrayOf(Manifest.permission.CAMERA),
                100
            )
            return@LaunchedEffect
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            viewModel.setCameraProvider(cameraProvider)

            val preview = Preview.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analyzer.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                viewModel.processImage(imageProxy)
            }

            viewModel.setImageAnalysis(analyzer)

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            preview.setSurfaceProvider(previewView.surfaceProvider)

            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                analyzer,
                videoCapture!!
            )
            camera.cameraControl.cancelFocusAndMetering()

            val cameraInfo = Camera2CameraInfo.from(camera.cameraInfo)
            val focalLengths = cameraInfo.getCameraCharacteristic(
                android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
            )
            val sensorSize = cameraInfo.getCameraCharacteristic(
                android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE
            )

            previewView.viewTreeObserver.addOnGlobalLayoutListener(object :
                ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    previewView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    val widthPx = previewView.width.takeIf { it > 0 } ?: 1920
                    val heightPx = previewView.height.takeIf { it > 0 } ?: 1080
                    if (focalLengths != null && focalLengths.isNotEmpty() && sensorSize != null) {
                        viewModel.setCameraParams(
                            focalLengthMeters = focalLengths[0] / 1000f,
                            sensorWidthMeters = sensorSize.width / 1000f,
                            sensorHeightMeters = sensorSize.height / 1000f,
                            imageHeightPx = heightPx,
                            imageWidthPx = widthPx
                        )
                    }
                }
            })
        }, ContextCompat.getMainExecutor(context))
    }
}

// Upload button
@Composable
fun UploadVideoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black.copy(alpha = 0.6f)
        )
    ) {
        Icon(
            imageVector = Icons.Default.FileUpload,
            contentDescription = "Upload Video",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("Upload Video", color = Color.White, fontSize = 14.sp)
    }
}

@Composable
fun DistanceTestOverlay(viewModel: CameraViewModel) {
    val personDistance by viewModel.personDistance.collectAsState()
    val lateralWidth by viewModel.lateralWidth.collectAsState()
    var heightInput by remember { mutableStateOf("1.7") }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            TextField(
                value = heightInput,
                onValueChange = { heightInput = it },
                label = { Text("Person height (m)") },
                modifier = Modifier.width(150.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                viewModel.setPersonHeight(heightInput.toFloatOrNull() ?: 1.7f)
            }) {
                Text("Apply")
            }
        }

        // Bottom info
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(16.dp)
        ) {
            Text(
                "Distance to Camera: %.2f m".format(personDistance),
                color = Color.White,
                fontSize = 18.sp
            )
            Text(
                "Walkable Space: %.2f m".format(lateralWidth),
                color = Color.White,
                fontSize = 18.sp
            )
            when {
                lateralWidth > 3.2f -> {
                    Text(
                        "Move Forward. Too much space detected.",
                        color = Color.Red,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                lateralWidth >= 3.0f -> {
                    Text(
                        "3m walkable space available - Recording will start soon...",
                        color = Color.Green,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                else -> {
                    Text(
                        "Not enough walkable space. Please ensure at least 3m of walkable space, max 3.2m.",
                        color = Color.Red,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
@Composable
fun LuminosityCheckOverlay(viewModel: CameraViewModel) {
    val luminance by viewModel.luminance.collectAsState()
    val error by viewModel.luminosityError.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Checking Lighting Conditions",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Luminance: %.1f".format(luminance),
            color = Color.White,
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (error != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    error!!,
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(
                "✅ Lighting conditions are good",
                color = Color.Green,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

    }
}



@Composable
fun VideoRecordingOverlay(
    navController: NavController,
    tugDataViewModel: TugDataViewModel,
    recording: Recording?,
    isRecording: Boolean,
    onRecordingStateChange: (Recording?, Boolean) -> Unit,
    context: Context,
    lifecycleOwner: LifecycleOwner,
    videoCapture: VideoCapture<Recorder>?,
    onRecordingFinished: (Uri?) -> Unit,
    deviceOrientation: devOrientationState
) {
    // Initialize TTS
    val tts = remember {
        android.speech.tts.TextToSpeech(context.applicationContext) { status ->
            if (status != android.speech.tts.TextToSpeech.SUCCESS) {
                Log.e("TTS", "Initialization failed")
            }
        }
    }
    LaunchedEffect(tts) {
        tts.language = Locale.getDefault()
    }

    // Countdown state
    var countdown by remember { mutableStateOf<Int?>(null) }
    var startCountdown by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Countdown display (only numbers)
        countdown?.let { number ->
            Text(
                text = number.toString(),
                color = Color.White,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Recording indicator at top
        if (isRecording) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .background(Color.Red.copy(alpha = 0.7f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color.White, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Recording", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        // similar to old screen
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .size(80.dp)
                .clip(CircleShape)
                .background(if (isRecording) Color.Red else Color.DarkGray)
                .clickable {
                    if (isRecording) {
                        recording?.stop()
                        onRecordingStateChange(null, false)
                    } else {
                        startCountdown = true
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Videocam,
                contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
    }

    // Countdown effect
    if (startCountdown) {
        LaunchedEffect(startCountdown) {
            val executor = ContextCompat.getMainExecutor(context)

            // Countdown 3, 2, 1 with TTS
            for (i in 3 downTo 1) {
                countdown = i
                executor.execute {
                    tts.speak(
                        i.toString(),
                        android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                        null,
                        "COUNTDOWN_$i"
                    )
                }
                delay(1000)
            }

            // Hide countdown
            countdown = null

            // Speak "Start" via TTS (not displayed)
            executor.execute {
                tts.speak(
                    "Start",
                    android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                    null,
                    "COUNTDOWN_START"
                )
            }

            startCountdown = false

            // Start recording
            if (videoCapture != null) {
                startRecording(
                    context,
                    lifecycleOwner,
                    videoCapture,
                    tugDataViewModel,
                    navController,
                    onRecordingStateChange,
                    onRecordingFinished,
                    deviceOrientation
                )
            }
        }
    }
}




private fun startRecording(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    videoCapture: VideoCapture<Recorder>,
    tugViewModel: TugDataViewModel,
    navController: NavController,
    onRecordingStateChange: (Recording?, Boolean) -> Unit,
    onRecordingFinished: (Uri?) -> Unit,
    deviceOrientation: devOrientationState
) {
    // Set the target rotation based on device orientation
    val targetRotation = when (deviceOrientation.orientation) {
        devOrientation.PORTRAIT -> Surface.ROTATION_0
        devOrientation.LANDSCAPE_LEFT -> Surface.ROTATION_270
        devOrientation.LANDSCAPE_RIGHT -> Surface.ROTATION_90
        devOrientation.PORTRAIT_UPSIDE_DOWN -> Surface.ROTATION_180
    }
    videoCapture.targetRotation = targetRotation

    val outputFile = File(
        context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES),
        "new-video-${System.currentTimeMillis()}.mp4")
    val outputOptions = FileOutputOptions.Builder(outputFile).build()

    val pendingRecording = videoCapture.output.prepareRecording(context, outputOptions)
    var activeRecording: Recording? = null
    var recordingStartTimeNanos: Long = 0
    activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
        when (event) {
            is VideoRecordEvent.Start -> {
                Log.d("VideoRecording", "Recording started")
                recordingStartTimeNanos = System.nanoTime()
                onRecordingStateChange(activeRecording, true)
            }
            is VideoRecordEvent.Finalize -> {
                if (event.hasError()) {
                    Log.e("CameraRecording", "Video capture failed: ${event.error}", event.cause)
                    activeRecording?.close()
                    onRecordingStateChange(null, false)
                    if (outputFile.exists()) outputFile.delete()
                    Toast.makeText(context, "Video capture failed", Toast.LENGTH_LONG).show()
                } else {
                    val durationSeconds = ((System.nanoTime() - recordingStartTimeNanos) / 1_000_000_000).toFloat()
                    onRecordingStateChange(null, false)
                    Toast.makeText(context, "Video capture succeeded", Toast.LENGTH_LONG).show()

                    onRecordingFinished(Uri.fromFile(outputFile))
                    val encodedPath = Uri.encode(outputFile.absolutePath)
                    val assessmentId = UUID.randomUUID().toString()
                    Log.d("camera_screen", "absolutePath: ${outputFile.absolutePath}")
                    val newTug = TUGAssessment(
                        testId = assessmentId,
                        dateTime = Date().time,
                        videoDuration = durationSeconds,
                        videoTitle = outputFile.absolutePath,
                        onMedication = tugViewModel.onMedication.value,
                        patientComments = tugViewModel.selectedComments.value.joinToString(", ")
                    )
                    tugViewModel.insertNewAssessment(newTug)
                    Log.d("camera_screen", "absolutePath: ${outputFile.absolutePath}")
                    Log.d("camera_screen", "encodedPath: $encodedPath")
                    navController.navigate("loading_screen")
                }
            }
        }
    }
}
