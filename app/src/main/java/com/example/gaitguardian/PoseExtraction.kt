package com.example.gaitguardian

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.io.File
import java.io.FileWriter

/**
 * Enhanced Pose Extraction - Using MediaPipe Tasks API
 */

/**
 * Data class to return both landmarks and video metadata
 */
data class VideoLandmarksResult(
    val landmarks: List<List<NormalizedLandmark>>,
    val fps: Float,
    val totalFrames: Int,
    val duration: Long
)

class PoseExtraction(private val context: Context) {
    
    companion object {
        private const val TAG = "PoseExtraction"
        private const val MODEL_FILE = "pose_landmarker_full.task"
    }
    
    private var poseLandmarker: PoseLandmarker? = null
    
    init {
        initializeMediaPipe()
    }
    
    private fun initializeMediaPipe() {
        try {
            Log.e(TAG, "MEDIAPIPE INITIALIZATION START")
            Log.d(TAG, "Initializing MediaPipe with model: $MODEL_FILE")
            Log.d(TAG, "Android API Level: ${android.os.Build.VERSION.SDK_INT}")
            Log.d(TAG, "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            
            // Check if model file exists in assets
            try {
                val inputStream = context.assets.open(MODEL_FILE)
                val size = inputStream.available()
                inputStream.close()
                Log.d(TAG, "Model file found in assets: $MODEL_FILE ($size bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "Model file NOT found in assets: $MODEL_FILE", e)
                return
            }
            
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_FILE)
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.VIDEO)    
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.5f)  
                .setMinPosePresenceConfidence(0.5f)  
                .setMinTrackingConfidence(0.5f)     
                .build()
                
            Log.d(TAG, "Creating PoseLandmarker from options...")
            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            Log.e(TAG, "MediaPipe PoseLandmarker initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPipe PoseLandmarker", e)
            Log.e(TAG, "Error details: ${e.message}")
            Log.e(TAG, "Error cause: ${e.cause}")
            poseLandmarker = null
        }
    }    /**
     * Process video to landmarks directly - returns landmarks and metadata
     */
    fun processVideoToLandmarksWithMetadata(
        videoUri: Uri,
        progressCallback: FrameProgressCallback? = null
    ): VideoLandmarksResult? {
        return try {
            Log.d(TAG, "========================================")
            Log.d(TAG, "POSE EXTRACTION STARTING")
            Log.d(TAG, "Video URI: $videoUri")
            Log.d(TAG, "Timestamp: ${System.currentTimeMillis()}")
            Log.d(TAG, "========================================")
            Log.e(TAG, "POSE_EXTRACTION_ENHANCED: processVideoToLandmarksWithMetadata CALLED")
            
            val mediaMetadataRetriever = MediaMetadataRetriever()
            mediaMetadataRetriever.setDataSource(context, videoUri)
            Log.e(TAG, "MediaMetadataRetriever initialized successfully")
            
            val duration = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            
            val frameRateString = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            var frameRate = frameRateString?.toFloatOrNull() ?: 30f // fallback to 30 if detection fails
            
            // Additional fallback: validate FPS range
            if (frameRate <= 0 || frameRate > 120) {
                Log.w(TAG, "Invalid FPS detected ($frameRate), using 30 FPS fallback")
                frameRate = 30f // Safe fallback
            }
            
            val totalFrames = ((duration / 1000.0) * frameRate).toInt()
            
            Log.d(TAG, "Video FPS detection - Raw FPS string: '$frameRateString', Parsed FPS: $frameRate")
            Log.d(TAG, "Video metadata - Duration: ${duration}ms, Total frames: $totalFrames, Frame rate: ${frameRate}fps")
            Log.e(TAG, "Duration: ${duration}ms, FPS: $frameRate, TotalFrames: $totalFrames")
            
            // Log frame extraction method for debugging
            val useFrameIndex = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
            Log.e(TAG, "Frame extraction method: ${if (useFrameIndex) "getFrameAtIndex() [API 28+]" else "getFrameAtTime() [Pre-API 28]"}")

            val landmarksList = mutableListOf<List<NormalizedLandmark>>()
            
            val rotation = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            Log.d(TAG, "Video rotation: $rotation degrees")
            
            // TODO: Use ImageProcessingOptions for rotation (API signature needs verification)
            // For now, use manual bitmap rotation but keep other optimizations
            
            // OPTIMIZATION: Use strictly increasing timestamps for MediaPipe
            val startTimestampMs = System.currentTimeMillis()
            val frameDurationMs = (1000.0 / frameRate).toLong().coerceAtLeast(1L) // Ensure at least 1ms between frames
            
            // Process ALL frames using MediaPipe
            for (frameNumber in 0 until totalFrames) {
                try {
                    // Use strictly increasing timestamp to avoid MediaPipe errors
                    val timestampMs = startTimestampMs + (frameNumber * frameDurationMs)
                    
                    // OPTIMIZATION: Use getFrameAtIndex() for exact frame extraction on API 28+
                    var bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        
                        try {
                            mediaMetadataRetriever.getFrameAtIndex(frameNumber)
                        } catch (e: Exception) {
                            Log.w(TAG, "getFrameAtIndex failed for frame $frameNumber, falling back to getFrameAtTime: ${e.message}")
                            // Fallback to time-based extraction if index fails
                            val timeUs = (frameNumber * 1000000L / frameRate).toLong()
                            mediaMetadataRetriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        }
                    } else {
                        // Pre-API 28 - Use time-based extraction
                        val timeUs = (frameNumber * 1000000L / frameRate).toLong()
                        mediaMetadataRetriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    }
                    
                    // Second-chance retry if frame extraction fails (for both methods)
                    if (bitmap == null && frameNumber > 0) {
                        bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            // Retry with previous frame index
                            try {
                                mediaMetadataRetriever.getFrameAtIndex(frameNumber - 1)
                            } catch (e: Exception) {
                                // Final fallback to time-based
                                val retryTimeUs = ((frameNumber - 1) * 1000000L / frameRate).toLong()
                                mediaMetadataRetriever.getFrameAtTime(retryTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                            }
                        } else {
                            // Retry with slightly offset time
                            val retryTimeUs = (frameNumber * 1000000L / frameRate).toLong() + (500000L / frameRate).toLong()
                            mediaMetadataRetriever.getFrameAtTime(retryTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        }
                    }
                    
                    if (frameNumber % 100 == 0) {
                        val extractionMethod = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) "getFrameAtIndex" else "getFrameAtTime"
                        Log.d(TAG, "Processing frame $frameNumber/$totalFrames (timestamp=${timestampMs}ms, method=$extractionMethod)")
                    }
                    
                    bitmap?.let { frame ->
                        try {
                            if (poseLandmarker != null) {
                                val resizedBitmap = Bitmap.createScaledBitmap(frame, 640, 480, true)
                                
                                val finalBitmap = if (rotation != 0) {
                                    val matrix = android.graphics.Matrix()
                                    matrix.postRotate(rotation.toFloat())
                                    Bitmap.createBitmap(resizedBitmap, 0, 0, resizedBitmap.width, resizedBitmap.height, matrix, true)
                                } else {
                                    resizedBitmap
                                }
                                
                                // Force ARGB_8888 format before MediaPipe (required format)
                                val argbBitmap = finalBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                
                                // Convert bitmap to MediaPipe image format
                                val mpImage = BitmapImageBuilder(argbBitmap).build()
                                
                                // Use detectForVideo with strictly increasing timestamps
                                val result = poseLandmarker!!.detectForVideo(mpImage, timestampMs)
                                
                                if (result.landmarks().isNotEmpty()) {
                                    val landmarks = result.landmarks()[0] // First person's landmarks
                                    if (landmarks.size == 33) {
                                        landmarksList.add(landmarks)
                                        if (frameNumber % 100 == 0) {
                                            Log.e(TAG, "Frame $frameNumber: MediaPipe detected ${landmarks.size} landmarks (640×480, timestamp=${timestampMs}ms, ARGB_8888)")
                                            // DETAILED COORDINATE LOGGING FOR COMPARISON
                                            Log.e(TAG, "FRAME $frameNumber COMPLETE COORDINATES:")
                                            for (i in landmarks.indices) {
                                                val landmark = landmarks[i]
                                                Log.e(TAG, "  Landmark $i: x=${landmark.x()}, y=${landmark.y()}, z=${landmark.z()}, visibility=${landmark.visibility().orElse(1.0f)}")
                                            }
                                            Log.e(TAG, "END FRAME $frameNumber COORDINATES")
                                        }
                                    } else {
                                        Log.w(TAG, "Frame $frameNumber: Insufficient landmarks (${landmarks.size}/33)")
                                                        // Log every 100 frames
                                                        if (frameNumber % 100 == 0) {
                                                            Log.e(TAG, "FRAME $frameNumber LANDMARKS (x, y, z):")
                                                            for (i in landmarks.indices) {
                                                                val landmark = landmarks[i]
                                                                Log.e(TAG, "  Landmark $i: x=${landmark.x()}, y=${landmark.y()}, z=${landmark.z()}")
                                                            }
                                                            Log.e(TAG, "END FRAME $frameNumber LANDMARKS")
                                                        }
                                        landmarksList.add(emptyList())
                                    }
                                } else {
                                    if (frameNumber <= 5) {
                                        Log.w(TAG, "Frame $frameNumber: No pose detected in VIDEO mode")
                                    }
                                    landmarksList.add(emptyList())
                                }
                                
                                // OPTIMIZATION: Recycle intermediate bitmaps to avoid OOM
                                argbBitmap.recycle()
                                if (finalBitmap != resizedBitmap) {
                                    finalBitmap.recycle() // Only recycle if it's a different bitmap
                                }
                                resizedBitmap.recycle()
                            } else {
                                Log.e(TAG, "PoseLandmarker is null")
                                landmarksList.add(emptyList())
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Skipping frame $frameNumber due to MediaPipe error: ${e.message}")
                            // Add empty landmark for this frame to maintain frame count consistency
                            landmarksList.add(emptyList())
                            // Continue processing next frames instead of failing
                        }
                        
                        // OPTIMIZATION: Recycle original frame bitmap
                        frame.recycle()
                    } ?: run {
                        Log.w(TAG, "Failed to extract bitmap for frame $frameNumber")
                        landmarksList.add(emptyList())
                    }
                    
                    // Report progress
                    progressCallback?.onProgress(frameNumber + 1, totalFrames, "Extracting poses")
                } catch (e: Exception) {
                    Log.e(TAG, "Error at frame $frameNumber: ${e.message}")
                    landmarksList.add(emptyList())
                }
            }
            
            mediaMetadataRetriever.release()
            
            val successfulFrames = landmarksList.count { it.isNotEmpty() }
            Log.e(TAG, "POSE EXTRACTION SUMMARY: ${successfulFrames}/${landmarksList.size} frames had poses detected")
            Log.d(TAG, "Extracted landmarks for ${landmarksList.size} frames (${successfulFrames} successful)")
            
            // Calculate a hash of first and last successful frames to verify uniqueness
            val firstSuccessfulFrame = landmarksList.firstOrNull { it.isNotEmpty() }
            val lastSuccessfulFrame = landmarksList.lastOrNull { it.isNotEmpty() }
            if (firstSuccessfulFrame != null && lastSuccessfulFrame != null) {
                val firstHash = firstSuccessfulFrame.take(3).joinToString { "${it.x()},${it.y()}" }.hashCode()
                val lastHash = lastSuccessfulFrame.take(3).joinToString { "${it.x()},${it.y()}" }.hashCode()
                Log.e(TAG, " VIDEO UNIQUENESS CHECK - First frame hash: $firstHash, Last frame hash: $lastHash")
            }
            
            if (successfulFrames == 0) {
                Log.e(TAG, "NO POSES DETECTED IN ANY FRAME - Check video quality, lighting, and person visibility")
            } 
            
            VideoLandmarksResult(
                landmarks = landmarksList,
                fps = frameRate,
                totalFrames = totalFrames,
                duration = duration
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing video: ${e.message}")
            null
        }
    }
    
    
    fun cleanup() {
        try {
            poseLandmarker?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup error: ${e.message}")
        }
    }
    
    /**
     * Initialize the pose landmarker if not already done
     */
    fun initialize(): Boolean {
        return if (poseLandmarker != null) {
            Log.d(TAG, "MediaPipe PoseLandmarker already initialized")
            true
        } else {
            Log.d(TAG, "Reinitializing MediaPipe PoseLandmarker...")
            initializeMediaPipe()
            poseLandmarker != null
        }
    }
    
    /**
     * Extract pose landmarks directly for MediaPipe feature extraction
     * Returns raw MediaPipe PoseLandmarkerResult objects for clean processing
     */
}
