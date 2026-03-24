package com.example.gaitguardian.api

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.gaitguardian.FrameProgressCallback
import com.example.gaitguardian.MediaPipePoseExtractor
import com.example.gaitguardian.PoseBackend
import com.example.gaitguardian.PoseExtractor
import com.example.gaitguardian.RTMOPoseExtractor
import com.example.gaitguardian.TugPrediction
import com.example.gaitguardian.data.models.TugResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
/**
 * GaitAnalysisClient - Clean Pipeline Architecture
 * 
 * CLEAN PIPELINE FLOW:
 * 1. PoseExtraction → raw landmarks
 * 2. FeatureExtraction → 111 biomechanical features 
 * 3. TugPrediction → ONNX inference + smoothing + duration analysis
 * 4. GaitAnalysisClient → orchestrates the pipeline
 * 
 * Main entry point: analyzeVideoFile() uses analyzeVideoWithCorrectFPS()
 */
class GaitAnalysisClient(private val context: Context) {
    companion object {
        private const val TAG = "GaitAnalysisClient"
        private const val RTMO_KEYPOINT_COUNT = 17
        private const val MEDIAPIPE_KEYPOINT_COUNT = 33
        private val ACTIVE_BACKEND = PoseBackend.RTMO
    }
    
    private val poseExtractor: PoseExtractor = createPoseExtractor(context, ACTIVE_BACKEND)
    private val tugPredictor = TugPrediction(context)
    
    private var isInitialized = false
    
    private suspend fun initializeIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.e(TAG, "INITIALIZING LOCAL GAIT ANALYSIS COMPONENTS")
            Log.d(TAG, "Initializing local gait analysis components...")
            try {
                // Initialize TUG predictor model
                Log.e(TAG, "Initializing TUG predictor...")
                if (!tugPredictor.initializeModel()) {
                    Log.e(TAG, "Failed to initialize TUG prediction model")
                    return@withContext false
                }
                Log.e(TAG, "TUG predictor initialized")
                
                // Initialize pose estimation
                Log.e(TAG, "Initializing pose estimation model...")
                if (!poseExtractor.initialize()) {
                    Log.e(TAG, "Failed to initialize pose estimation model")
                    return@withContext false
                }
                Log.e(TAG, "Pose estimation model initialized")
                
                isInitialized = true
                Log.e(TAG, "Local gait analysis components initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing components", e)
                return@withContext false
            }
        } else {
            Log.d(TAG, "Components already initialized")
        }
        true
    }

    private fun createPoseExtractor(context: Context, backend: PoseBackend): PoseExtractor {
        return when (backend) {
            PoseBackend.RTMO -> RTMOPoseExtractor(context)
            PoseBackend.MEDIAPIPE -> MediaPipePoseExtractor(context)
        }
    }

    suspend fun analyzeVideo(videoUri: Uri, progressCallback: FrameProgressCallback? = null): TugResult {
        return try {
            Log.d(TAG, "STARTING URI ANALYSIS: $videoUri")
            Log.e(TAG, "ANALYSIS START")
            
            if (!initializeIfNeeded()) {
                Log.e(TAG, "URI Analysis - Failed to initialize")
                throw Exception("Failed to initialize analysis components")
            }
            
            Log.d(TAG, "Converting URI to temp file...")
            // Convert URI to temporary file
            val videoFile = copyUriToTempFile(videoUri)
            Log.d(TAG, "Temp file created: ${videoFile.name} (${videoFile.length()} bytes)")
            
            // Process with existing File-based method
            val result = analyzeVideoFile(videoFile, progressCallback)
            
            // Cleanup temp file
            videoFile.delete()
            Log.d(TAG, "URI Analysis completed")
            
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during local video analysis", e)
            throw e
        }
    }

    private suspend fun copyUriToTempFile(uri: Uri): File = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "temp_video_${System.currentTimeMillis()}.mp4")
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Could not open input stream for URI")
        
        val outputStream = FileOutputStream(tempFile)
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()
        
        tempFile
    }    

    suspend fun analyzeVideoFile(videoFile: File, progressCallback: FrameProgressCallback? = null): TugResult {
        return try {
            Log.d(TAG, "ANALYZING VIDEO FILE: ${videoFile.name} (${videoFile.length()} bytes)")
            Log.d(TAG, "File exists: ${videoFile.exists()}")
            Log.d(TAG, "File path: ${videoFile.absolutePath}")
            
            if (!initializeIfNeeded()) {
                Log.e(TAG, "FILE Analysis - Failed to initialize")
                throw Exception("Failed to initialize analysis components")
            }
            
            Log.d(TAG, "FILE Analysis - Components initialized")
            
            // NEW APPROACH: Enhanced direct landmark processing with correct FPS
            Log.d(TAG, "Using enhanced analysis with correct FPS...")
            val result = analyzeVideoWithCorrectFPS(videoFile, progressCallback)
            
            Log.d(TAG, "Enhanced analysis successful. Risk: ${result.riskAssessment}")
            
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during enhanced video analysis", e)
            
            // Return error instead of fallback since we're using clean pipeline
            createErrorResult("Enhanced analysis failed: ${e.message}")
        }
    }
    
    /**
     * NEW: Direct landmark processing method using advanced 111-feature extraction
     */
    /**
     * Enhanced analysis method with correct FPS handling
     */
    private suspend fun analyzeVideoWithCorrectFPS(videoFile: File, progressCallback: FrameProgressCallback? = null): TugResult = withContext(Dispatchers.IO) {
        Log.e(TAG, "ENHANCED ANALYSIS WITH CORRECT FPS")
        Log.d(TAG, "Start analysing gait video...")
        Log.d(TAG, "Video file: ${videoFile.name} (${videoFile.length()} bytes)")

        // Start overall timing
        val overallStartTime = System.currentTimeMillis()

        try {
            
            // Step 1: Extract pose landmarks
            Log.d(TAG, "Extracting pose landmarks...")
            val poseExtractionStartTime = System.currentTimeMillis()
            val videoUri = Uri.fromFile(videoFile)
            val poseSequence = poseExtractor.extractPoseSequence(videoUri, progressCallback)
            val poseExtractionEndTime = System.currentTimeMillis()
            
            if (poseSequence == null || poseSequence.frames.isEmpty()) {
                Log.e(TAG, "No pose sequence extracted")
                return@withContext createErrorResult("No pose landmarks detected in video")
            }

            val detectedFrames = poseSequence.frames.count { it.persons.isNotEmpty() }
            val firstDetectedFrame = poseSequence.frames.indexOfFirst { it.persons.isNotEmpty() }
            val firstPerson = poseSequence.frames
                .firstOrNull { it.persons.isNotEmpty() }
                ?.persons
                ?.firstOrNull()

            Log.e(TAG, "Extracted ${poseSequence.frames.size} ${poseSequence.backend} frames")
            Log.e(TAG, "${poseSequence.backend} frames detected: $detectedFrames/${poseSequence.frames.size}")
            Log.e(TAG, "First detected frame index: $firstDetectedFrame")
            Log.e(TAG, "${poseSequence.backend} fps=${poseSequence.fps}, durationMs=${poseSequence.duration}")

            if (firstPerson != null) {
                val sample = (0 until minOf(3, firstPerson.keypointCount)).joinToString(" | ") { index ->
                    val base = index * firstPerson.valuesPerKeypoint
                    val x = firstPerson.keypoints.getOrElse(base) { 0f }
                    val y = firstPerson.keypoints.getOrElse(base + 1) { 0f }
                    val confidence = firstPerson.keypoints.getOrElse(base + firstPerson.valuesPerKeypoint - 1) { 0f }
                    "kp$index=(x=${"%.3f".format(x)}, y=${"%.3f".format(y)}, last=${"%.3f".format(confidence)})"
                }
                Log.e(TAG, "First ${poseSequence.backend} person sample: $sample")
            }

            Log.e(TAG, "Time taken: ${System.currentTimeMillis() - overallStartTime}ms")
            Log.e(
                TAG,
                "Pipeline mismatch: ${poseSequence.backend} provides " +
                    "${firstPerson?.keypointCount ?: 0} keypoints with ${firstPerson?.valuesPerKeypoint ?: 0} values each, " +
                    "but FeatureExtraction/TugPrediction still expect $MEDIAPIPE_KEYPOINT_COUNT MediaPipe landmarks."
            )
            return@withContext createErrorResult(
                "${poseSequence.backend} extraction works, but prediction is not wired yet: current " +
                    "FeatureExtraction/TugPrediction expects $MEDIAPIPE_KEYPOINT_COUNT MediaPipe landmarks, " +
                    "while ${poseSequence.backend} currently provides ${firstPerson?.keypointCount ?: RTMO_KEYPOINT_COUNT} keypoints."
            )

            // if (videoLandmarksResult == null || videoLandmarksResult.landmarks.isEmpty()) {
            //     Log.e(TAG, "No pose landmarks extracted")
            //     return@withContext createErrorResult("No pose landmarks detected in video")
            // }

            // // Step 2: Run frame-by-frame TUG prediction using TugPrediction.processPoseLandmarks
            // Log.d(TAG, "Running frame-by-frame ONNX model on all ${videoLandmarksResult.landmarks.size} frames...")
            // val mlProcessingStartTime = System.currentTimeMillis()
            // val prediction = tugPredictor.processPoseLandmarks(videoLandmarksResult.landmarks, videoLandmarksResult.fps, progressCallback)
            // val mlProcessingEndTime = System.currentTimeMillis()
            
            // val overallEndTime = System.currentTimeMillis()
            
            // // Calculate timing metrics
            // val poseExtractionDuration = (poseExtractionEndTime - poseExtractionStartTime) / 1000.0
            // val mlProcessingDuration = (mlProcessingEndTime - mlProcessingStartTime) / 1000.0
            // val totalDuration = (overallEndTime - overallStartTime) / 1000.0
            
            // // Log comprehensive timing breakdown
            // Log.e(TAG, " ========== COMPLETE VIDEO ANALYSIS TIMING ==========")
            // Log.e(TAG, " TOTAL TIME: ${String.format("%.2f", totalDuration)} s")
            // Log.e(TAG, " ")
            // Log.e(TAG, " Pose Extraction: ${String.format("%.2f", poseExtractionDuration)} s (${String.format("%.1f", poseExtractionDuration/totalDuration*100)}%)")
            // Log.e(TAG, " ML Processing: ${String.format("%.2f", mlProcessingDuration)} s (${String.format("%.1f", mlProcessingDuration/totalDuration*100)}%)")
            // Log.e(TAG, "    └─ Feature Extraction + ONNX Prediction + Post-Processing")
            // Log.e(TAG, "       (See detailed breakdown in TugPrediction logs above)")
            // Log.e(TAG, " ")
            // Log.e(TAG, " Video Stats:")
            // Log.e(TAG, "    Frames: ${videoLandmarksResult.landmarks.size}")
            // Log.e(TAG, "    FPS: ${videoLandmarksResult.fps}")
            // Log.e(TAG, "    Video Duration: ${String.format("%.2f", videoLandmarksResult.landmarks.size / videoLandmarksResult.fps)} s")
            // Log.e(TAG, "=====================================================")

            // if (prediction.success) {
            //     convertPredictionToTugResult(prediction)
            // } else {
            //     val msg = if (prediction.error_message != null) "TUG prediction failed: ${prediction.error_message}"
            //             else "TUG prediction failed"
            //     createErrorResult(msg)
            // }

        } catch (e: Exception) {
            Log.e(TAG, "Error in enhanced analysis", e)
            createErrorResult("Enhanced analysis failed: ${e.message}")
        }
    }
    
    /**
     * Helper method to create error TugResult
     */
    private fun createErrorResult(errorMessage: String): TugResult {
        return TugResult(
            totalDuration = 0.0,
            sitToStandDuration = 0.0,
            walkingDuration = 0.0,
            standToSitDuration = 0.0,
            riskAssessment = "ERROR: $errorMessage",
            analysisDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
            phaseBreakdown = emptyMap()
        )
    }
    

    suspend fun checkServerHealth(): Result<Boolean> {
        return try {
            // For local processing, just check if components are initialized
            val isHealthy = initializeIfNeeded()
            Log.d(TAG, "Local analysis health check: $isHealthy")
            Result.success(isHealthy)
        } catch (e: Exception) {
            Log.e(TAG, "Health check error", e)
            Result.failure(e)
        }
    }
    
    fun cleanup() {
        try {
            // Close pose estimation
            poseExtractor.cleanup()
            Log.d(TAG, "GaitAnalysisClient cleaned up successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Error during cleanup", e)
        }
    }
    
    private fun convertPredictionToTugResult(prediction: TugPrediction.PredictionResult): TugResult {
        val total = prediction.total_duration_sec.toDouble()
        val sit  = prediction.phase_durations["Sit-To-Stand"]?.toDouble() ?: 0.0
        val walk = (prediction.phase_durations["Walk-From-Chair"]?.toDouble() ?: 0.0) +
                (prediction.phase_durations["Walk-To-Chair"]?.toDouble() ?: 0.0)
        val stand = prediction.phase_durations["Stand-To-Sit"]?.toDouble() ?: 0.0

        // Use the actual ML-calculated severity from the prediction
        val riskAssessment = if (prediction.success) {
            prediction.severity
        } else {
            prediction.error_message ?: "Analysis failed"
        }

        val breakdown: Map<String, Double> = prediction.phase_durations.mapValues { it.value.toDouble() }

        return TugResult(
            totalDuration       = total,
            sitToStandDuration  = sit,
            walkingDuration     = walk,
            standToSitDuration  = stand,
            riskAssessment      = riskAssessment,
            analysisDate        = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                    .format(java.util.Date()),
            phaseBreakdown      = breakdown
        )
    }


}
