package com.example.gaitguardian.pipeline.pose.rtmo

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.gaitguardian.FrameProgressCallback
import com.example.gaitguardian.pipeline.pose.core.PoseBackend
import com.example.gaitguardian.pipeline.pose.core.PoseCoordinateSpace
import com.example.gaitguardian.pipeline.pose.core.PoseExtractor
import com.example.gaitguardian.pipeline.pose.core.PoseFrame
import com.example.gaitguardian.pipeline.pose.core.PosePerson
import com.example.gaitguardian.pipeline.pose.core.PoseSequence
import java.nio.FloatBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RTMOPoseExtractor(private val context: Context) : PoseExtractor {

    companion object {
        private const val TAG = "RTMOPoseExtractor"
        private const val MODEL_FILE = "rtmo-t.onnx"
        private const val INPUT_SIZE = 416
        private const val NUM_KEYPOINTS = 17
        private const val BBOX_CONF_THRESHOLD = 0.5f
    }

    data class VideoLandmarksResult(
        val landmarks: List<List<FloatArray>?>,
        val fps: Float,
        val totalFrames: Int,
        val duration: Long
    )

    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var isInitialized = false

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing RTMO-t ONNX model...")
            ortEnvironment = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open(MODEL_FILE).readBytes()
            ortSession = ortEnvironment!!.createSession(modelBytes)
            isInitialized = true
            Log.d(TAG, "RTMO-t initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize RTMO-t", e)
            false
        }
    }

    suspend fun processVideoToLandmarksWithMetadata(
        videoUri: Uri,
        progressCallback: FrameProgressCallback? = null
    ): VideoLandmarksResult? = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.e(TAG, "Not initialized")
            return@withContext null
        }

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, videoUri)

            val duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

            val frameRateString = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE
            )
            var fps = frameRateString?.toFloatOrNull() ?: 30f
            if (fps <= 0 || fps > 120) fps = 30f

            val totalFrames = ((duration / 1000.0) * fps).toInt()
            Log.d(TAG, "Video: ${duration}ms, ${fps}fps, $totalFrames frames")

            val landmarksList = mutableListOf<List<FloatArray>?>()

            for (frameNumber in 0 until totalFrames) {
                try {
                    val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        try {
                            retriever.getFrameAtIndex(frameNumber)
                        } catch (e: Exception) {
                            val timeUs = (frameNumber * 1_000_000L / fps).toLong()
                            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        }
                    } else {
                        val timeUs = (frameNumber * 1_000_000L / fps).toLong()
                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    }

                    if (bitmap != null) {
                        val detections = runInference(bitmap, bitmap.width, bitmap.height)
                        landmarksList.add(if (detections.isEmpty()) null else detections)
                        bitmap.recycle()
                    } else {
                        landmarksList.add(null)
                    }

                    progressCallback?.onProgress(frameNumber + 1, totalFrames, "Extracting poses")
                } catch (e: Exception) {
                    Log.w(TAG, "Error at frame $frameNumber: ${e.message}")
                    landmarksList.add(null)
                }
            }

            retriever.release()

            VideoLandmarksResult(
                landmarks = landmarksList,
                fps = fps,
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

        val frames = result.landmarks.map { persons ->
            PoseFrame(
                persons = persons.orEmpty().map { keypoints ->
                    PosePerson(
                        keypoints = keypoints,
                        keypointCount = NUM_KEYPOINTS,
                        valuesPerKeypoint = 3,
                        coordinateSpace = PoseCoordinateSpace.PIXEL
                    )
                }
            )
        }

        return PoseSequence(
            backend = PoseBackend.RTMO,
            frames = frames,
            fps = result.fps,
            totalFrames = result.totalFrames,
            duration = result.duration
        )
    }

    override fun cleanup() {
        try {
            ortSession?.close()
            ortEnvironment?.close()
            Log.d(TAG, "RTMOPoseExtractor cleaned up")
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup warning: ${e.message}")
        }
    }

    private fun runInference(bitmap: Bitmap, origW: Int, origH: Int): List<FloatArray> {
        val session = ortSession ?: return emptyList()
        val env = ortEnvironment ?: return emptyList()

        return try {
            val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
            val argb = resized.copy(Bitmap.Config.ARGB_8888, false)
            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            argb.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

            val floatArray = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
            for (i in pixels.indices) {
                val pixel = pixels[i]
                floatArray[i] = ((pixel shr 16) and 0xFF).toFloat()
                floatArray[INPUT_SIZE * INPUT_SIZE + i] = ((pixel shr 8) and 0xFF).toFloat()
                floatArray[2 * INPUT_SIZE * INPUT_SIZE + i] = (pixel and 0xFF).toFloat()
            }

            resized.recycle()
            argb.recycle()

            val inputTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(floatArray),
                longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
            )

            val results = session.run(mapOf("input" to inputTensor))
            val dets = (results.get(0).value as Array<*>)[0] as Array<*>
            val kps = (results.get(1).value as Array<*>)[0] as Array<*>

            inputTensor.close()
            results.close()

            val scaleX = origW.toFloat() / INPUT_SIZE
            val scaleY = origH.toFloat() / INPUT_SIZE
            val detections = mutableListOf<FloatArray>()

            for (i in dets.indices) {
                val det = dets[i] as FloatArray
                if (det[4] < BBOX_CONF_THRESHOLD) continue

                val personKps = kps[i] as Array<*>
                val keypointArray = FloatArray(NUM_KEYPOINTS * 3)

                for (j in 0 until NUM_KEYPOINTS) {
                    val kp = personKps[j] as FloatArray
                    keypointArray[j * 3] = kp[0] * scaleX
                    keypointArray[j * 3 + 1] = kp[1] * scaleY
                    keypointArray[j * 3 + 2] = kp[2]
                }

                detections.add(keypointArray)
            }

            detections
        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message}")
            emptyList()
        }
    }
}
