package com.example.gaitguardian.pipeline.pose.mediapipe

import android.content.Context
import android.net.Uri
import com.example.gaitguardian.FrameProgressCallback
import com.example.gaitguardian.PoseExtraction
import com.example.gaitguardian.pipeline.pose.core.PoseBackend
import com.example.gaitguardian.pipeline.pose.core.PoseCoordinateSpace
import com.example.gaitguardian.pipeline.pose.core.PoseExtractor
import com.example.gaitguardian.pipeline.pose.core.PoseFrame
import com.example.gaitguardian.pipeline.pose.core.PosePerson
import com.example.gaitguardian.pipeline.pose.core.PoseSequence

class MediaPipePoseExtractor(private val context: Context) : PoseExtractor {

    private val poseExtraction = PoseExtraction(context)

    override suspend fun initialize(): Boolean = poseExtraction.initialize()

    override suspend fun extractPoseSequence(
        videoUri: Uri,
        progressCallback: FrameProgressCallback?
    ): PoseSequence? {
        val result = poseExtraction.processVideoToLandmarksWithMetadata(videoUri, progressCallback)
            ?: return null

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
        poseExtraction.cleanup()
    }
}
