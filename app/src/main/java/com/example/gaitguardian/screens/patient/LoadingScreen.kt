package com.example.gaitguardian.screens.patient

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.gaitguardian.NotificationService
import com.example.gaitguardian.analysis.VideoViewType
import com.example.gaitguardian.api.GaitAnalysisClient
import com.example.gaitguardian.api.GaitAnalysisResponse
import com.example.gaitguardian.api.GaitMetrics
import com.example.gaitguardian.api.TugMetrics
import com.example.gaitguardian.api.ProcessingInfo
import com.example.gaitguardian.data.models.TugResult
import com.example.gaitguardian.data.roomDatabase.tug.TUGAnalysis
import com.example.gaitguardian.viewmodels.PatientViewModel
import com.example.gaitguardian.viewmodels.TugDataViewModel
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun LoadingScreen(
    navController: NavController,
    errorMessage: String?,
    tugDataViewModel: TugDataViewModel,
    patientViewModel: PatientViewModel
) {

    val latestAssessment by tugDataViewModel.latestAssessment.collectAsState()
    val selectedVideoViewType by tugDataViewModel.selectedVideoViewType.collectAsState()
    val outputPath = latestAssessment?.videoTitle
    val context = LocalContext.current
    val videoFile = outputPath?.let { File(it) }
    val gson = remember { Gson() }
    val workManager = WorkManager.getInstance(context)
    val workRequestId = remember { mutableStateOf<UUID?>(null) }
    var analysisState by remember {
        mutableStateOf(
            if (errorMessage != null) AnalysisState.Error(errorMessage)
            else AnalysisState.Idle
        )
    }
    var progress by remember { mutableStateOf(0) }
    var currentFrame by remember { mutableStateOf(0) }
    var totalFrames by remember { mutableStateOf(0) }
    var processingStage by remember { mutableStateOf("Initializing...") }
    var analysisResult by remember { mutableStateOf<GaitAnalysisResponse?>(null) }

    val motivationalQuotes = listOf(
        "🌟 Keep going, you're doing amazing!",
        "💪 Every step matters. You’ve got this!",
        "🌈 You are stronger than you think.",
        "🕊️ Small progress is still progress.",
        "🧠 Courage doesn’t always roar.\nSometimes it’s the quiet voice that says,\n‘I’ll try again tomorrow.’"
    )
    val randomQuote = remember { motivationalQuotes.random() }
    LaunchedEffect(errorMessage) {
        if (errorMessage == null) {
            tugDataViewModel.getLatestTUGAssessment()
            Log.d("loadingscreen", "error message dont have, retrieved latest alr")
        }
    }

    if (errorMessage != null)
    {
        analysisState = AnalysisState.Error(errorMessage)
    }
    else {
        LaunchedEffect(Unit) {
            tugDataViewModel.getLatestTUGAssessment()
        }

        // Start background analysis using WorkManager
        LaunchedEffect(videoFile, errorMessage) {
            delay(500)
            if (errorMessage == null && videoFile != null) {
                analysisState = AnalysisState.Analyzing
                Log.d("loadingscreen", "analysis state ste to analyzing now")
                Log.d("LoadingScreen", "video absolute path is ${videoFile.absolutePath}")
                // create a WorkManager request to analyse the video
                val workRequest = OneTimeWorkRequestBuilder<VideoAnalysisWorker>()
                    .setInputData(
                        workDataOf(
                            "VIDEO_PATH" to videoFile.absolutePath,
                            "VIDEO_VIEW_TYPE" to selectedVideoViewType.routeValue
                        )
                    )
                    .addTag("video_analysis")
                    .build()

                workManager.enqueue(workRequest)
                workRequestId.value = workRequest.id
            }
        }

        val workInfo by workRequestId.value?.let { id ->
            workManager.getWorkInfoByIdLiveData(id)
                .observeAsState() // ← Use observeAsState instead of observeForever!
        } ?: remember { mutableStateOf(null) }

        LaunchedEffect(workInfo) {
            workInfo?.let { info ->
                when (info.state) {
                    WorkInfo.State.RUNNING -> {
                        val p = info.progress.getInt("PROGRESS", 0)
                        progress = p
                        currentFrame = info.progress.getInt("CURRENT_FRAME", 0)
                        totalFrames = info.progress.getInt("TOTAL_FRAMES", 0)
                        processingStage = info.progress.getString("STAGE") ?: "Processing..."
                        analysisState = AnalysisState.Analyzing
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        val json = info.outputData.getString("ANALYSIS_RESULT")
                        val analysisResult = gson.fromJson(json, GaitAnalysisResponse::class.java)

                        Log.d("result", "analysisResult is $analysisResult")

                        if (analysisResult.success) {
                            Log.d("result", "analysis state is success: inserting new entry now!")
                            val assessmentId = latestAssessment?.testId
                            // Insert analysis and get the ID first
                            withContext(Dispatchers.IO) {
                                handleAnalysisSuccess(
                                    assessmentId!!,
                                    analysisResult,
//                                videoFile,
                                    tugDataViewModel,
                                    patientViewModel
                                )
                            }

                            Log.d("LoadingScreen", "✅ New analysis inserted")
                            Log.d("LoadingScreen", "📊 TUG Result - Total Time: ${analysisResult.tugMetrics?.totalTime}s")
                            Log.d("LoadingScreen", "📊 Severity: ${analysisResult.severity}")
                            workManager.cancelWorkById(workRequestId.value!!)
                            workManager.pruneWork()
                            // Set success state AFTER getting the ID
                            analysisState = AnalysisState.Success
                        } else {
                            analysisResult.error?.let {
                                analysisState = AnalysisState.Error(it)
                                tugDataViewModel.removeLastInsertedAssessment()
                                Log.d("LoadingScreen", "SUCCESSFULLY REMOVED LAST ASSESSMENT BECAUSE FAILED")
                            }
                        }
                    }

                    WorkInfo.State.FAILED -> {
                        val error = info.outputData.getString("ERROR_MESSAGE") ?: "Unknown ?error"
                        analysisState = AnalysisState.Error(error)
                        tugDataViewModel.removeLastInsertedAssessment()
                    }

                    WorkInfo.State.CANCELLED -> {
                        analysisState = AnalysisState.Error("Analysis was cancelled")
                        tugDataViewModel.removeLastInsertedAssessment()
                    }

                    else -> {}
                }
            }
        }

        // Navigate when success
        LaunchedEffect(analysisState) {
            if (analysisState == AnalysisState.Success) {
//            navController.navigate("result_screen/${assessmentTitle}/${analysisId}") {
//            navController.navigate("result_screen/${assessmentTitle}") {
//                navController.navigate("result_screen") {
//                popUpTo("loading_screen") { inclusive = true }
//                popUpTo("gait_assessment_screen") {inclusive = false}
//            }
                navController.navigate("result_screen") {
//                popUpTo("loading_screen") { inclusive = true }
                    popUpTo("gait_assessment_screen") {inclusive = false}
                }
            }
        }
    }


    // UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFFEDE7F6), Color(0xFFF3E5F5)))
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Processing your results...",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4A148C)
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            when (analysisState) {
                is AnalysisState.Analyzing -> {
                    CircularProgressIndicator(
                        color = Color(0xFF6A1B9A),
                        progress = progress / 100f
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "$progress% complete",
                        color = Color(0xFF4A148C),
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    // Display frame progress details
                    if (totalFrames > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Frame $currentFrame of $totalFrames",
                            color = Color(0xFF6A1B9A),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = processingStage,
                            color = Color(0xFF9C27B0),
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }

                is AnalysisState.Error -> {
                    Text(
                        text = "❌ ${(analysisState as AnalysisState.Error).message}",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = {
//                            navController.navigate("new_cam_screen")
                            navController.navigate("assessment_info_screen/'Timed Up and Go'")
//                            navController.navigate("camera_screen/$assessmentTitle")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))

                    )
                    {
                        Text("Record Again", color = Color.White)
                    }
                }

                else -> {}
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = randomQuote,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF6A1B9A)
                        )
                    )
                }
            }
        }
    }
}

private fun convertTugResultToGaitAnalysisResponse(tugResult: TugResult): GaitAnalysisResponse {
    // Check if this is an error result
    val isError = tugResult.riskAssessment.startsWith("ERROR:")

    return if (isError) {
        // Return error response
        GaitAnalysisResponse(
            success = false,
            gaitMetrics = null,
            tugMetrics = null,
            severity = null,
            processingInfo = null,
            requestId = "local_${System.currentTimeMillis()}",
            error = tugResult.riskAssessment,
            errorType = "ANALYSIS_FAILED"
        )
    } else {
        // Create successful response
        val tugMetrics = TugMetrics(
            sitToStandTime = tugResult.sitToStandDuration,
            walkFromChairTime = tugResult.phaseBreakdown["Walk-From-Chair"] ?: 0.0,
            turnFirstTime = tugResult.phaseBreakdown["Turn-First"] ?: 0.0,
            walkToChairTime = tugResult.phaseBreakdown["Walk-To-Chair"] ?: 0.0,
            turnSecondTime = tugResult.phaseBreakdown["Turn-Second"] ?: 0.0,
            standToSitTime = tugResult.standToSitDuration,
            totalTime = tugResult.totalDuration
        )

        // Create placeholder gait metrics
        val gaitMetrics = GaitMetrics(
            stepCount = 20, // Placeholder
            meanStepLength = 0.5, // Placeholder
            strideTime = 1.2, // Placeholder
            cadence = 100.0, // Placeholder
            stepSymmetry = 0.95, // Placeholder
            leftKneeRange = 45.0, // Placeholder
            rightKneeRange = 45.0, // Placeholder
            upperBodySway = 5.0, // Placeholder
            turn1Duration = tugMetrics.turnFirstTime,
            turn2Duration = tugMetrics.turnSecondTime
        )

        // Use the ML-calculated severity from TugResult instead of hardcoded logic
        val severity = tugResult.riskAssessment

        val processingInfo = ProcessingInfo(
            totalFrames = 100, // Placeholder
            processedFrames = 100, // Placeholder
            fps = 30.0,
            processingTimeSeconds = 0.0
        )

        GaitAnalysisResponse(
            success = true,
            gaitMetrics = gaitMetrics,
            tugMetrics = tugMetrics,
            severity = severity,
            processingInfo = processingInfo,
            requestId = "local_${System.currentTimeMillis()}",
            error = null,
            errorType = null
        )
    }
}

class VideoAnalysisWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val videoPath = inputData.getString("VIDEO_PATH") ?: return Result.failure()
        val videoViewType = VideoViewType.fromRouteValue(inputData.getString("VIDEO_VIEW_TYPE"))
        Log.d("VideoAnalysisWorker", "========================================")
        Log.d("VideoAnalysisWorker", "🎬 NEW VIDEO ANALYSIS STARTED")
        Log.d("VideoAnalysisWorker", "Video Path: $videoPath")
        Log.d("VideoAnalysisWorker", "Video View Type: ${videoViewType.routeValue}")
        Log.d("VideoAnalysisWorker", "Work Request ID: $id")
        Log.d("VideoAnalysisWorker", "Timestamp: ${System.currentTimeMillis()}")
        Log.d("VideoAnalysisWorker", "========================================")

        val gaitClient = GaitAnalysisClient(applicationContext)
        val videoFile = File(videoPath)
        
        if (!videoFile.exists()) {
            Log.e("VideoAnalysisWorker", "❌ ERROR: Video file does not exist: $videoPath")
            return Result.failure()
        }
        
        Log.d("VideoAnalysisWorker", "Video file size: ${videoFile.length()} bytes")
        Log.d("VideoAnalysisWorker", "Video file last modified: ${videoFile.lastModified()}")
        return try {
            // Real-time progress reporting using FrameProgressCallback
            val tugResult: TugResult = gaitClient.analyzeVideoFile(videoFile) { currentFrame: Int, totalFrames: Int, stage: String ->
                // Calculate percentage (0-100)
                val progressPercent = ((currentFrame.toFloat() / totalFrames.toFloat()) * 100f).toInt().coerceIn(0, 100)
                
                // Update WorkManager progress (needs runBlocking since setProgress is suspend)
                runBlocking {
                    setProgress(workDataOf(
                        "PROGRESS" to progressPercent,
                        "CURRENT_FRAME" to currentFrame,
                        "TOTAL_FRAMES" to totalFrames,
                        "STAGE" to stage
                    ))
                }
                
                // Log progress every 50 frames
                if (currentFrame % 50 == 0 || currentFrame == totalFrames - 1) {
                    Log.d("VideoAnalysisWorker", "$stage: $currentFrame/$totalFrames ($progressPercent%)")
                }
            }
            
            Log.d("VideoAnalysisWorker", "Analysis complete: ${tugResult.riskAssessment}")
            Log.d("VideoAnalysisWorker", "========================================")
            Log.d("VideoAnalysisWorker", "📊 TUG RESULT DETAILS:")
            Log.d("VideoAnalysisWorker", "Risk: ${tugResult.riskAssessment}")
            Log.d("VideoAnalysisWorker", "Total Duration: ${tugResult.totalDuration}s")
            Log.d("VideoAnalysisWorker", "Sit-to-Stand: ${tugResult.sitToStandDuration}s")
            Log.d("VideoAnalysisWorker", "Stand-to-Sit: ${tugResult.standToSitDuration}s")
            Log.d("VideoAnalysisWorker", "Phase Breakdown: ${tugResult.phaseBreakdown}")
            Log.d("VideoAnalysisWorker", "========================================")
//            NotificationService(applicationContext).showCompleteVideoNotification("TUG")
            val response: GaitAnalysisResponse = convertTugResultToGaitAnalysisResponse(tugResult)
            if (response.success)
            {
                NotificationService(applicationContext).showCompleteVideoNotification(true)
            }
            else {
                NotificationService(applicationContext).showCompleteVideoNotification(false,response.error)
            }
            Log.d("VideoAnalysisWorker", "your response now: $response")
            val resultJson = Gson().toJson(response)
            Log.d("VideoAnalysisWorker", "your json now: $resultJson")
            val output = workDataOf("ANALYSIS_RESULT" to resultJson, "VIDEO_PATH" to videoFile.absolutePath)

            Result.success(output)
        } catch (e: Exception) {
            Log.e("VideoAnalysisWorker", "Error during analysis", e)
            val error = workDataOf("ERROR_MESSAGE" to (e.message ?: "Unknown error"))
            Result.failure(error)
        }
    }
}

suspend fun handleAnalysisSuccess(
    assessmentId: String,
    response: GaitAnalysisResponse,
//    videoFile: File,
    tugDataViewModel: TugDataViewModel,
    patientViewModel: PatientViewModel
) {
    val tugMetrics = response.tugMetrics
    val previousAnalysis = tugDataViewModel.getLatestTugAnalysis()
    var isFlagged = false

    if (previousAnalysis != null) {
        val currentTime = tugMetrics?.totalTime ?: 0.0
        val prevTime = previousAnalysis.timeTaken
        val diff = kotlin.math.abs(currentTime - prevTime)
        if (diff > 1.0) isFlagged = true
    }

    val analysis = TUGAnalysis(
        testId = assessmentId,
        severity = response.severity ?: "Unknown",
        timeTaken = tugMetrics?.totalTime ?: 0.0,
        stepCount = response.gaitMetrics?.stepCount ?: 0,
        sitToStand = tugMetrics?.sitToStandTime ?: 0.0,
        walkFromChair = tugMetrics?.walkFromChairTime ?: 0.0,
        turnFirst = tugMetrics?.turnFirstTime ?: 0.0,
        walkToChair = tugMetrics?.walkToChairTime ?: 0.0,
        turnSecond = tugMetrics?.turnSecondTime ?: 0.0,
        standToSit = tugMetrics?.standToSitTime ?: 0.0,
        isFlagged = isFlagged
    )
    Log.d("handleAnalysisSuccess", " analysis Info: $analysis")
    tugDataViewModel.insertTugAnalysis(analysis)
//
//    if (!patientViewModel.saveVideos.value && videoFile.exists()) {
//        videoFile.delete()
//    }
}
