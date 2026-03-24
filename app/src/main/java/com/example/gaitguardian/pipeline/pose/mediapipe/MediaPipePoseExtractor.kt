package com.example.gaitguardian.pipeline.pose.mediapipe

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.gaitguardian.FrameProgressCallback
import com.example.gaitguardian.pipeline.pose.core.PoseBackend
import com.example.gaitguardian.pipeline.pose.core.PoseCoordinateSpace
import com.example.gaitguardian.pipeline.pose.core.PoseExtractor
import com.example.gaitguardian.pipeline.pose.core.PoseFrame
import com.example.gaitguardian.pipeline.pose.core.PosePerson
import com.example.gaitguardian.pipeline.pose.core.PoseSequence
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

class MediaPipePoseExtractor(private val context: Context) : PoseExtractor {

    companion object {
        private const val TAG = "MediaPipePoseExtractor"
        private const val MODEL_FILE = "pose_landmarker_full.task"
    }

    private var poseLandmarker: PoseLandmarker? = null

    init {
        initializeMediaPipe()
    }

    override suspend fun initialize(): Boolean {
        return if (poseLandmarker != null) {
            Log.d(TAG, "MediaPipe PoseLandmarker already initialized")
            true
        } else {
            Log.d(TAG, "Reinitializing MediaPipe PoseLandmarker...")
            initializeMediaPipe()
            poseLandmarker != null
        }
    }

    fun processVideoToLandmarksWithMetadata(
        videoUri: Uri,
        progressCallback: FrameProgressCallback? = null
    ): MediaPipeVideoLandmarksResult? {
        return try {
            val mediaMetadataRetriever = MediaMetadataRetriever()
            mediaMetadataRetriever.setDataSource(context, videoUri)

            val duration = mediaMetadataRetriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

            val frameRateString = mediaMetadataRetriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE
            )
            var frameRate = frameRateString?.toFloatOrNull() ?: 30f
            if (frameRate <= 0 || frameRate > 120) {
                Log.w(TAG, "Invalid FPS detected ($frameRate), using 30 FPS fallback")
                frameRate = 30f
            }

            val totalFrames = ((duration / 1000.0) * frameRate).toInt()
            val landmarksList = mutableListOf<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>>()
            val rotation = mediaMetadataRetriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            )?.toIntOrNull() ?: 0
            val startTimestampMs = System.currentTimeMillis()
            val frameDurationMs = (1000.0 / frameRate).toLong().coerceAtLeast(1L)

            for (frameNumber in 0 until totalFrames) {
                try {
                    val timestampMs = startTimestampMs + (frameNumber * frameDurationMs)
                    var bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        try {
                            mediaMetadataRetriever.getFrameAtIndex(frameNumber)
                        } catch (e: Exception) {
                            val timeUs = (frameNumber * 1_000_000L / frameRate).toLong()
                            mediaMetadataRetriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        }
                    } else {
                        val timeUs = (frameNumber * 1_000_000L / frameRate).toLong()
                        mediaMetadataRetriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    }

                    if (bitmap == null && frameNumber > 0) {
                        bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            try {
                                mediaMetadataRetriever.getFrameAtIndex(frameNumber - 1)
                            } catch (e: Exception) {
                                val retryTimeUs = ((frameNumber - 1) * 1_000_000L / frameRate).toLong()
                                mediaMetadataRetriever.getFrameAtTime(retryTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                            }
                        } else {
                            val retryTimeUs = (frameNumber * 1_000_000L / frameRate).toLong() +
                                (500_000L / frameRate).toLong()
                            mediaMetadataRetriever.getFrameAtTime(retryTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        }
                    }

                    bitmap?.let { frame ->
                        try {
                            if (poseLandmarker != null) {
                                val resizedBitmap = Bitmap.createScaledBitmap(frame, 640, 480, true)
                                val finalBitmap = if (rotation != 0) {
                                    val matrix = android.graphics.Matrix()
                                    matrix.postRotate(rotation.toFloat())
                                    Bitmap.createBitmap(
                                        resizedBitmap,
                                        0,
                                        0,
                                        resizedBitmap.width,
                                        resizedBitmap.height,
                                        matrix,
                                        true
                                    )
                                } else {
                                    resizedBitmap
                                }

                                val argbBitmap = finalBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                val mpImage = BitmapImageBuilder(argbBitmap).build()
                                val result = poseLandmarker!!.detectForVideo(mpImage, timestampMs)

                                if (result.landmarks().isNotEmpty() && result.landmarks()[0].size == 33) {
                                    landmarksList.add(result.landmarks()[0])
                                } else {
                                    landmarksList.add(emptyList())
                                }

                                argbBitmap.recycle()
                                if (finalBitmap != resizedBitmap) {
                                    finalBitmap.recycle()
                                }
                                resizedBitmap.recycle()
                            } else {
                                landmarksList.add(emptyList())
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Skipping frame $frameNumber due to MediaPipe error: ${e.message}")
                            landmarksList.add(emptyList())
                        }

                        frame.recycle()
                    } ?: landmarksList.add(emptyList())

                    progressCallback?.onProgress(frameNumber + 1, totalFrames, "Extracting poses")
                } catch (e: Exception) {
                    Log.e(TAG, "Error at frame $frameNumber: ${e.message}")
                    landmarksList.add(emptyList())
                }
            }

            mediaMetadataRetriever.release()

            MediaPipeVideoLandmarksResult(
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

    override suspend fun extractPoseSequence(
        videoUri: Uri,
        progressCallback: FrameProgressCallback?
    ): PoseSequence? {
        val result = processVideoToLandmarksWithMetadata(videoUri, progressCallback) ?: return null

        val frames = result.landmarks.map { landmarks ->
            val persons = if (landmarks.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    PosePerson(
                        keypoints = FloatArray(landmarks.size * 4) { index ->
                            val landmark = landmarks[index / 4]
                            when (index % 4) {
                                0 -> landmark.x()
                                1 -> landmark.y()
                                2 -> landmark.z()
                                else -> landmark.visibility().orElse(1.0f)
                            }
                        },
                        keypointCount = landmarks.size,
                        valuesPerKeypoint = 4,
                        coordinateSpace = PoseCoordinateSpace.NORMALIZED
                    )
                )
            }
            PoseFrame(persons = persons)
        }

        return PoseSequence(
            backend = PoseBackend.MEDIAPIPE,
            frames = frames,
            fps = result.fps,
            totalFrames = result.totalFrames,
            duration = result.duration
        )
    }

    override fun cleanup() {
        try {
            poseLandmarker?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup error: ${e.message}")
        }
    }

    private fun initializeMediaPipe() {
        try {
            context.assets.open(MODEL_FILE).close()

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

            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            Log.e(TAG, "MediaPipe PoseLandmarker initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPipe PoseLandmarker", e)
            poseLandmarker = null
        }
    }
}
