package com.example.gaitguardian.api

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.gaitguardian.FrameProgressCallback
import com.example.gaitguardian.TugPrediction
import com.example.gaitguardian.data.models.TugResult
import com.example.gaitguardian.pipeline.prediction.rtmo.RtmoPhaseModelInputAdapter
import com.example.gaitguardian.pipeline.prediction.rtmo.RtmoPhasePredictor
import com.example.gaitguardian.pipeline.prediction.rtmo.RtmoSeverityFeatureBuilder
import com.example.gaitguardian.pipeline.prediction.rtmo.RtmoSeverityPredictor
import com.example.gaitguardian.pipeline.prediction.rtmo.RtmoSequenceNormalizer
import com.example.gaitguardian.pipeline.prediction.rtmo.RtmoTemporalInterpolator
import com.example.gaitguardian.pipeline.pose.core.PoseBackend
import com.example.gaitguardian.pipeline.pose.core.PoseExtractor
import com.example.gaitguardian.pipeline.pose.mediapipe.MediaPipePoseExtractor
import com.example.gaitguardian.pipeline.pose.rtmo.RTMOPoseExtractor
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
        private const val MEDIAPIPE_KEYPOINT_COUNT = 33
        private val ACTIVE_BACKEND = PoseBackend.RTMO
    }
    
    private val poseExtractor: PoseExtractor = createPoseExtractor(context, ACTIVE_BACKEND)
    private val rtmoPhaseInputAdapter = RtmoPhaseModelInputAdapter()
    private val rtmoTemporalInterpolator = RtmoTemporalInterpolator()
    private val rtmoSequenceNormalizer = RtmoSequenceNormalizer()
    private val rtmoPhasePredictor = RtmoPhasePredictor(context)
    private val rtmoSeverityFeatureBuilder = RtmoSeverityFeatureBuilder()
    private val rtmoSeverityPredictor = RtmoSeverityPredictor(context)
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

                if (ACTIVE_BACKEND == PoseBackend.RTMO) {
                    Log.e(TAG, "Initializing RTMO LSTM phase predictor...")
                    if (!rtmoPhasePredictor.initialize()) {
                        Log.e(TAG, "Failed to initialize RTMO LSTM phase predictor")
                        return@withContext false
                    }
                    Log.e(TAG, "RTMO LSTM phase predictor initialized")

                    Log.e(TAG, "Initializing RTMO severity MLP...")
                    if (!rtmoSeverityPredictor.initialize()) {
                        Log.e(TAG, "Failed to initialize RTMO severity MLP")
                        return@withContext false
                    }
                    Log.e(TAG, "RTMO severity MLP initialized")
                }
                
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
            val videoUri = Uri.fromFile(videoFile)

            when (ACTIVE_BACKEND) {
                PoseBackend.MEDIAPIPE -> runMediaPipeXgboostPipeline(
                    videoUri = videoUri,
                    overallStartTime = overallStartTime,
                    progressCallback = progressCallback
                )
                PoseBackend.RTMO -> runRtmoDebugPipeline(
                    videoUri = videoUri,
                    overallStartTime = overallStartTime,
                    progressCallback = progressCallback
                )
            }

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

    private suspend fun runMediaPipeXgboostPipeline(
        videoUri: Uri,
        overallStartTime: Long,
        progressCallback: FrameProgressCallback?
    ): TugResult {
        val mediaPipeExtractor = poseExtractor as? MediaPipePoseExtractor
            ?: return createErrorResult("MediaPipe backend selected but extractor is not MediaPipe")

        val poseExtractionStartTime = System.currentTimeMillis()
        val videoLandmarksResult = mediaPipeExtractor.processVideoToLandmarksWithMetadata(
            videoUri,
            progressCallback
        )
        val poseExtractionEndTime = System.currentTimeMillis()

        if (videoLandmarksResult == null || videoLandmarksResult.landmarks.isEmpty()) {
            Log.e(TAG, "No MediaPipe landmarks extracted")
            return createErrorResult("No pose landmarks detected in video")
        }

        Log.e(
            TAG,
            "MediaPipe frames detected: ${videoLandmarksResult.landmarks.count { it.isNotEmpty() }}/${videoLandmarksResult.landmarks.size}"
        )

        Log.d(TAG, "Running frame-by-frame ONNX model on all ${videoLandmarksResult.landmarks.size} frames...")
        val mlProcessingStartTime = System.currentTimeMillis()
        val prediction = tugPredictor.processPoseLandmarks(
            videoLandmarksResult.landmarks,
            videoLandmarksResult.fps,
            progressCallback
        )
        val mlProcessingEndTime = System.currentTimeMillis()
        val overallEndTime = System.currentTimeMillis()

        val poseExtractionDuration = (poseExtractionEndTime - poseExtractionStartTime) / 1000.0
        val mlProcessingDuration = (mlProcessingEndTime - mlProcessingStartTime) / 1000.0
        val totalDuration = (overallEndTime - overallStartTime) / 1000.0

        Log.e(TAG, "========== COMPLETE VIDEO ANALYSIS TIMING ==========")
        Log.e(TAG, "TOTAL TIME: ${String.format("%.2f", totalDuration)} s")
        Log.e(TAG, "Pose Extraction: ${String.format("%.2f", poseExtractionDuration)} s")
        Log.e(TAG, "ML Processing: ${String.format("%.2f", mlProcessingDuration)} s")
        Log.e(TAG, "Frames: ${videoLandmarksResult.landmarks.size}")
        Log.e(TAG, "FPS: ${videoLandmarksResult.fps}")
        Log.e(TAG, "===================================================")

        return if (prediction.success) {
            convertPredictionToTugResult(prediction)
        } else {
            val msg = prediction.error_message ?: "TUG prediction failed"
            createErrorResult(msg)
        }
    }

    private suspend fun runRtmoDebugPipeline(
        videoUri: Uri,
        overallStartTime: Long,
        progressCallback: FrameProgressCallback?
    ): TugResult {
        val poseSequence = poseExtractor.extractPoseSequence(videoUri, progressCallback)

        if (poseSequence == null || poseSequence.frames.isEmpty()) {
            Log.e(TAG, "No pose sequence extracted")
            return createErrorResult("No pose landmarks detected in video")
        }

        val phaseInput = rtmoPhaseInputAdapter.adapt(poseSequence)
        val interpolatedSequence = rtmoTemporalInterpolator.interpolate(phaseInput)
        val normalizedSequence = rtmoSequenceNormalizer.normalize(interpolatedSequence)
        val phasePrediction = rtmoPhasePredictor.predict(normalizedSequence)
        val severityFeatures = if (phasePrediction.success) {
            rtmoSeverityFeatureBuilder.build(phasePrediction)
        } else {
            null
        }
        val severityPrediction = if (severityFeatures != null) {
            rtmoSeverityPredictor.predict(severityFeatures)
        } else {
            null
        }
        val firstDetectedFrame = phaseInput.frames.firstOrNull { it.hasPose }?.frameIndex ?: -1
        val firstDetectedPhaseFrame = phaseInput.frames.firstOrNull { it.hasPose }

        Log.e(TAG, "Extracted ${poseSequence.frames.size} ${poseSequence.backend} frames")
        Log.e(TAG, "${poseSequence.backend} frames detected: ${phaseInput.detectedFrames}/${poseSequence.frames.size}")
        Log.e(TAG, "First detected frame index: $firstDetectedFrame")
        Log.e(TAG, "${poseSequence.backend} fps=${poseSequence.fps}, durationMs=${poseSequence.duration}")
        Log.e(
            TAG,
            "RTMO reduced sequence: sequenceLength=${phaseInput.sequenceLength}, " +
                "featuresPerFrame=${phaseInput.featuresPerFrame}, keypoints=${phaseInput.keypointIndices}"
        )
        Log.e(
            TAG,
            "RTMO interpolated sequence: frames=${interpolatedSequence.frameCount}, " +
                "joints=${interpolatedSequence.jointCount}, dims=${interpolatedSequence.dimensionsPerJoint}, " +
                "nanFrames=${interpolatedSequence.nanFrameCount}"
        )
        Log.e(
            TAG,
            "RTMO normalized sequence: frames=${normalizedSequence.frameCount}, " +
                "joints=${normalizedSequence.jointCount}, dims=${normalizedSequence.dimensionsPerJoint}, " +
                "scale=${"%.6f".format(normalizedSequence.normalizationScale)}"
        )
        Log.e(
            TAG,
            "RTMO LSTM result: success=${phasePrediction.success}, input=${phasePrediction.inputName}, " +
                "output=${phasePrediction.outputName}, inputShape=${phasePrediction.inputShape?.contentToString()}, " +
                "outputShape=${phasePrediction.outputShape?.contentToString()}, error=${phasePrediction.errorMessage}"
        )

        if (firstDetectedPhaseFrame != null) {
            val rawSample = (0 until minOf(3, phaseInput.keypointIndices.size)).joinToString(" | ") { index ->
                val base = index * 3
                val x = firstDetectedPhaseFrame.features.getOrElse(base) { 0f }
                val y = firstDetectedPhaseFrame.features.getOrElse(base + 1) { 0f }
                val confidence = firstDetectedPhaseFrame.features.getOrElse(base + 2) { 0f }
                "kp${phaseInput.keypointIndices[index]}=(x=${"%.3f".format(x)}, y=${"%.3f".format(y)}, conf=${"%.3f".format(confidence)})"
            }
            Log.e(TAG, "First ${poseSequence.backend} reduced sample: $rawSample")

            val interpolatedSample = (0 until minOf(3, interpolatedSequence.jointCount)).joinToString(" | ") { index ->
                val point = interpolatedSequence.coordinates[firstDetectedFrame.coerceAtLeast(0)][index]
                "kp${phaseInput.keypointIndices[index]}=(x=${"%.3f".format(point[0])}, y=${"%.3f".format(point[1])})"
            }
            Log.e(TAG, "First ${poseSequence.backend} interpolated sample: $interpolatedSample")

            val normalizedSample = (0 until minOf(3, normalizedSequence.jointCount)).joinToString(" | ") { index ->
                val point = normalizedSequence.coordinates[firstDetectedFrame.coerceAtLeast(0)][index]
                "kp${phaseInput.keypointIndices[index]}=(x=${"%.3f".format(point[0])}, y=${"%.3f".format(point[1])})"
            }
            Log.e(TAG, "First ${poseSequence.backend} normalized sample: $normalizedSample")
        }

        if (phasePrediction.success) {
            Log.e(
                TAG,
                "RTMO LSTM ordered durations (sec): " +
                    phasePrediction.orderedPhaseDurationsSec.joinToString(
                        prefix = "[",
                        postfix = "]"
                    ) { "%.3f".format(it) }
            )
            Log.e(TAG, "RTMO LSTM duration map: ${phasePrediction.phaseDurationsSec}")
            Log.e(TAG, "RTMO LSTM labels: firstFrames=${phasePrediction.frameLabels.take(10)}")
            if (severityFeatures != null) {
                Log.e(
                    TAG,
                    "RTMO severity features: " +
                        severityFeatures.featureNames.zip(severityFeatures.featureVector.toList()).joinToString(
                            prefix = "[",
                            postfix = "]"
                        ) { (name, value) -> "$name=${"%.3f".format(value)}" }
                )
            }
            if (severityPrediction != null) {
                val probabilitySummary = severityPrediction.classProbabilities.joinToString(
                    prefix = "[",
                    postfix = "]"
                ) { "%.3f".format(it) }
                Log.e(
                    TAG,
                    "RTMO severity result: success=${severityPrediction.success}, severity=${severityPrediction.severityLabel}, " +
                        "classIndex=${severityPrediction.predictedClassIndex}, input=${severityPrediction.inputName}, " +
                        "outputs=${severityPrediction.outputNames}, inputShape=${severityPrediction.inputShape?.contentToString()}, " +
                        "outputShapes=${severityPrediction.outputShapes.map { it?.contentToString() }}, " +
                        "probs=$probabilitySummary, " +
                        "error=${severityPrediction.errorMessage}"
                )
            }
        }

        Log.e(TAG, "Time taken: ${System.currentTimeMillis() - overallStartTime}ms")
        return if (phasePrediction.success) {
            Log.e(
                TAG,
                "RTMO phase inference and severity MLP are now wired through TugResult."
            )
            convertRtmoPhasePredictionToTugResult(phasePrediction, severityPrediction)
        } else {
            Log.e(
                TAG,
                "RTMO sequence preprocessing is ready, but LSTM phase inference still needs debugging. " +
                    "The legacy FeatureExtraction/TugPrediction stack still expects $MEDIAPIPE_KEYPOINT_COUNT MediaPipe landmarks."
            )
            createErrorResult(
                "${poseSequence.backend} extraction and preprocessing work, but LSTM phase prediction failed: " +
                    "${phasePrediction.errorMessage ?: "unknown error"}"
            )
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
            rtmoPhasePredictor.cleanup()
            rtmoSeverityPredictor.cleanup()
            Log.d(TAG, "GaitAnalysisClient cleaned up successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Error during cleanup", e)
        }
    }
    
    private fun convertPredictionToTugResult(prediction: TugPrediction.PredictionResult): TugResult {
        val total = prediction.total_duration_sec.toDouble()
        val sit  = prediction.phase_durations["Sit-to-Stand"]?.toDouble() ?: 0.0
        val walk = (prediction.phase_durations["Walk-from-Chair"]?.toDouble() ?: 0.0) +
                (prediction.phase_durations["Walk-to-Chair"]?.toDouble() ?: 0.0)
        val stand = prediction.phase_durations["Stand-to-Sit"]?.toDouble() ?: 0.0

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

    private fun convertRtmoPhasePredictionToTugResult(
        prediction: com.example.gaitguardian.pipeline.prediction.rtmo.RtmoPhasePredictionResult,
        severityPrediction: com.example.gaitguardian.pipeline.prediction.rtmo.RtmoSeverityPredictionResult?
    ): TugResult {
        val phaseBreakdown = prediction.phaseDurationsSec.mapValues { it.value.toDouble() }
        val sit = phaseBreakdown["Sit-To-Stand"] ?: 0.0
        val walkFrom = phaseBreakdown["Walk-From-Chair"] ?: 0.0
        val turnFirst = phaseBreakdown["Turn-First"] ?: 0.0
        val walkTo = phaseBreakdown["Walk-To-Chair"] ?: 0.0
        val turnSecond = phaseBreakdown["Turn-Second"] ?: 0.0
        val stand = phaseBreakdown["Stand-To-Sit"] ?: 0.0
        val total = sit + walkFrom + turnFirst + walkTo + turnSecond + stand

        return TugResult(
            totalDuration = total,
            sitToStandDuration = sit,
            walkingDuration = walkFrom + walkTo,
            standToSitDuration = stand,
            riskAssessment = severityPrediction?.severityLabel ?: "Unknown",
            analysisDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date()),
            phaseBreakdown = phaseBreakdown
        )
    }


}
