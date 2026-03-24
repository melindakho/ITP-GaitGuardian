package com.example.gaitguardian.pipeline.pose.rtmo

import android.content.Context
import android.net.Uri
import com.example.gaitguardian.FrameProgressCallback
import com.example.gaitguardian.pipeline.pose.core.PoseBackend
import com.example.gaitguardian.pipeline.pose.core.PoseCoordinateSpace
import com.example.gaitguardian.pipeline.pose.core.PoseExtractor
import com.example.gaitguardian.pipeline.pose.core.PoseFrame
import com.example.gaitguardian.pipeline.pose.core.PosePerson
import com.example.gaitguardian.pipeline.pose.core.PoseSequence

class RTMOPoseExtractor(private val context: Context) : PoseExtractor {

    private val legacyExtractor = com.example.gaitguardian.RTMOPoseExtractor(context)

    override suspend fun initialize(): Boolean = legacyExtractor.initialize()

    override suspend fun extractPoseSequence(
        videoUri: Uri,
        progressCallback: FrameProgressCallback?
    ): PoseSequence? {
        val result = legacyExtractor.processVideoToLandmarksWithMetadata(videoUri, progressCallback)
            ?: return null

        val frames = result.landmarks.map { persons ->
            PoseFrame(
                persons = persons.orEmpty().map { keypoints ->
                    PosePerson(
                        keypoints = keypoints,
                        keypointCount = 17,
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
        legacyExtractor.cleanup()
    }
}
