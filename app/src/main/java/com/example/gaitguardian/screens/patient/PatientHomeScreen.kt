package com.example.gaitguardian.screens.patient

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight.Companion.ExtraBold
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.gaitguardian.ui.theme.DefaultColor
import com.example.gaitguardian.ui.theme.Heading1
import com.example.gaitguardian.ui.theme.bgColor
import com.example.gaitguardian.ui.theme.body
import com.example.gaitguardian.viewmodels.PatientViewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.gaitguardian.api.GaitAnalysisResponse
import com.example.gaitguardian.ui.theme.ButtonActive
import com.example.gaitguardian.viewmodels.TugDataViewModel
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Composable
fun PatientHomeScreen(
    navController: NavController,
    patientViewModel: PatientViewModel,
    tugViewModel: TugDataViewModel,
    showButton: Boolean = true, // make sure this flag exists
    modifier: Modifier = Modifier
) {
    val patientInfo by patientViewModel.patient.collectAsState()
    val latestTwoDurations by tugViewModel.latestTwoDurations.collectAsState()
    val latestAssessment by tugViewModel.latestAssessment.collectAsState()
    val onMedication by tugViewModel.onMedication.collectAsState()

    var previousTiming by remember { mutableFloatStateOf(0f) }
    var latestTiming by remember { mutableFloatStateOf(0f) }
    val latestAnalysisFlow by tugViewModel.latestAnalysis.collectAsState()
    val severity = latestAnalysisFlow?.severity ?: "-" // will auto update the result card as it is observing (more for WorkManager update)
    val totalTime = latestAnalysisFlow?.timeTaken?.toFloat() ?: 0f
    var showTutorial by remember { mutableStateOf(false) } // <-- control overlay

//    Log.d("patienthomescreen", "latest tug analysis : $latestAnalysis")
    if (latestTwoDurations.size >= 2) {
        latestTiming = latestTwoDurations[0]
        previousTiming = latestTwoDurations[1]
    }
    // WorkManager to check if got ongoing jobs / jobs not completed (for vid analysis)
    val context = LocalContext.current
    val workManager = WorkManager.getInstance(context)

    LaunchedEffect(context) {
        tugViewModel.backfillMissingVideoDurations(context)
    }

    // Observe all video analysis work
    val workInfoList by workManager
        .getWorkInfosByTagLiveData("video_analysis")
        .observeAsState(initial = emptyList())

    val ongoingWork = remember(workInfoList) {
        workInfoList.filter {
            it.state == WorkInfo.State.RUNNING ||
                    it.state == WorkInfo.State.ENQUEUED
        }
    }

    workInfoList.forEach { workInfo ->
        Log.d("patienthomescreen", "WorkInfo: $workInfo")
    }

    val latestWorkInfo = workInfoList
        .filter { it.state == WorkInfo.State.SUCCEEDED }
        .find { workInfo ->
            val videoPath = workInfo.outputData.getString("VIDEO_PATH")
            videoPath != null && videoPath == latestAssessment?.videoTitle
        }
    Log.d("patienthomescreen", "MATCHED latestworkinfo videoPath: ${latestWorkInfo?.outputData?.getString("VIDEO_PATH")}")


    LaunchedEffect(latestWorkInfo) {
        if (latestWorkInfo != null) {
            if (latestWorkInfo.state == WorkInfo.State.SUCCEEDED) {
                val json = latestWorkInfo.outputData.getString("ANALYSIS_RESULT")
                val analysisResult = Gson().fromJson(json, GaitAnalysisResponse::class.java)
//                val latestAssessment by tugViewModel.latestAssessment.collectAsState()
                val assessmentId = latestAssessment?.testId
                Log.d("patienthomescreen", "latest assessmentiD: $assessmentId")

                withContext(Dispatchers.IO) {
                    val existingAnalysis = tugViewModel.checkTugAnalysisById(assessmentId ?: return@withContext)
                    if(existingAnalysis != null) // means analysis for corresponding tug assessment already done
                    {
                        return@withContext
                    }
                    if (analysisResult.success)
                    {
                        Log.d("patienthomescreen", "analysis success , going into handleAnalysisSuccess")
                        handleAnalysisSuccess(
                            assessmentId,
                            analysisResult,
//                        File(analysisResult.processingInfo?.videoPath ?: return@withContext),
                            tugViewModel,
                            patientViewModel
                        )
                    }
                    else {
                        Log.d("patienthomescreen", "analysis failed- removing")
                        tugViewModel.removeLastInsertedAssessment()
                    }
                    workManager.pruneWork()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(16.dp),
        contentAlignment = Alignment.Center // center everything vertically & horizontally
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Welcome Text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Welcome back,",
                    fontSize = body,
                    color = Color(0xFF718096)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = patientInfo?.name ?: "Sophia Tan",
                    fontWeight = ExtraBold,
                    fontSize = Heading1,
                    color = Color.Black
                )
            }

            // Core Results Card
            CoreResultsCard(
                latestAssessment = latestAssessment,
                previousTiming = previousTiming,
                latestTiming = latestTiming,
                severity = severity,
                totalTime = totalTime,
                medicationOn = onMedication,
                hasUpdatedMedication = latestAssessment?.updateMedication == true,
                showUpdateButton = false,
                onUpdateMedication = {}
            )

            if (showButton) {
                if (ongoingWork.isNotEmpty()) // if there's ongoing jobs , display a circular loader
                {
                    VideoProcessingBanner(ongoingWork)
                }
                else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { navController.navigate("gait_assessment_screen") },
                            //onClick = { navController.navigate("result_screen/TestAssessment") },
                            colors = ButtonDefaults.buttonColors(containerColor = ButtonActive),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Record Video",
                                color = DefaultColor,
                                fontSize = Heading1,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (showButton) {
                            Button(
                                onClick = { showTutorial = true }, // <-- show overlay
                                colors = ButtonDefaults.buttonColors(containerColor = ButtonActive),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "How to use",
                                    color = DefaultColor,
                                    fontSize = Heading1,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
        // --- Full-screen tutorial overlay using Dialog ---
        if (showTutorial) {
            Dialog(onDismissRequest = { showTutorial = false }) {
                PatientTutorialScreen(
                    onClose = { showTutorial = false }
                )
            }
        }
    }
}


@Composable
fun VideoProcessingBanner(
    ongoingWork: List<WorkInfo>,
) {

    val progressList = ongoingWork.map { info ->
        mapOf(
            "currentFrame" to info.progress.getInt("CURRENT_FRAME", 0),
            "totalFrames" to info.progress.getInt("TOTAL_FRAMES", 0),
            "percent" to info.progress.getInt("PROGRESS", 0),
        )
    }
    // for check
    LaunchedEffect(ongoingWork) {
        Log.d("VideoProcessingBanner", "Ongoing work count: ${ongoingWork.size}")
    }
    // Only show banner if there's ongoing work
    AnimatedVisibility(
        visible = ongoingWork.isNotEmpty(),
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF6A1B9A)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier.weight(1f)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Processing Video",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "${ongoingWork.size} analysis in progress",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                        if (progressList.isNotEmpty()) {
                            val p = progressList.first()

                            val currentFrame = p["currentFrame"] as Int
                            val totalFrame = p["totalFrames"] as Int
                            val percent = p["percent"] as Int

                            Text(
                                text = "Frame $currentFrame/$totalFrame ($percent%)",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp
                            )
                        }

                    }
                }

            }
        }
    }
}
